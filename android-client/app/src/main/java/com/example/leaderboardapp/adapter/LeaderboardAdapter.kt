package com.example.leaderboardapp.adapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.leaderboardapp.R
import com.example.leaderboardapp.model.PlayerStats

class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {
    private var players: List<PlayerStats> = emptyList()

    fun updatePlayers(newPlayers: List<PlayerStats>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = players[position]
        holder.bind(player)
    }

    override fun getItemCount() = players.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.playerName)
        private val winsTextView: TextView = view.findViewById(R.id.playerWins)
        private val pointsTextView: TextView = view.findViewById(R.id.playerPoints)
        private val lastSeenTextView: TextView = view.findViewById(R.id.playerLastSeen)

        fun bind(player: PlayerStats) {
            nameTextView.text = player.name
            winsTextView.text = "Победы: ${player.wins}"
            pointsTextView.text = "Очки: ${player.totalPoints}"
            lastSeenTextView.text = "Последняя активность: ${player.lastSeen}"
        }
    }
}