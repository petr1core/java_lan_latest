package com.example.leaderboardapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leaderboardapp.adapter.LeaderboardAdapter
import com.example.leaderboardapp.model.PlayerStats
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LeaderboardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LeaderboardAdapter()
        recyclerView.adapter = adapter

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("Leaderboard", "Connecting to server...")
                val socket = Socket("192.168.0.9", 8080)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Отправляем запрос на получение таблицы лидеров
                // Log.d("Leaderboard", "Sending request: $requestJson")
                val joinJson = "{\"type\":\"JOIN\", \"content\":\"spectator_android\"}"
                writer.println(joinJson)

                val requestJson = "{\"type\":\"GET_LEADERBOARD\"}"
                writer.println(requestJson)

                // Читаем ответ (ожидаем JSON)
                var response: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    Log.d("Leaderboard", "Received line: $line")
                    val jsonObject = Gson().fromJson(line, JsonObject::class.java)
                    if (jsonObject.get("type")?.asString == "LEADERBOARD_UPDATE") {
                        response = line
                        break
                    }
                }

                if (response == null) {
                    Log.e("Leaderboard", "Response is null!")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Нет ответа от сервера",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // Парсим JSON-ответ
                val gson = Gson()
                val jsonObject = gson.fromJson(response, JsonObject::class.java)
                val leadersJson = jsonObject.getAsJsonArray("leaders")
                Log.d("Leaderboard", "Leaders JSON: $leadersJson")
                val type = object : TypeToken<List<PlayerStats>>() {}.type
                val players: List<PlayerStats> = gson.fromJson(leadersJson, type)
                Log.d("Leaderboard", "Parsed players: $players")

                // Обновляем UI на главном потоке
                runOnUiThread {
                    adapter.updatePlayers(players)
                }
            } catch (e: Exception) {
                Log.e("Leaderboard", "Ошибка: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }
}