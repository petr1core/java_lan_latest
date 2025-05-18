package com.example.lan_2;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.example.lan_2.Server.clients;

public class Server {
    private static final int PORT = 8080;
    static final Set<ClientHandler> clients = Collections.synchronizedSet(new LinkedHashSet<>());
    protected static final List<ProjectileData> activeProjectiles = Collections.synchronizedList(new ArrayList<>());
    protected static final CopyOnWriteArrayList<TargetData> activeTargets = new CopyOnWriteArrayList<>();
    static final Gson gson = new Gson();
    protected static boolean paused = false;
    protected static volatile boolean gameEnded = false;
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            DataBase.initialize();
            startGameLoop();
            while (!Thread.interrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection accepted");
                    new ClientHandler(clientSocket).start();
                } catch (IOException e) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                    // Продолжаем работу сервера после ошибки
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void saveUser(String name) {
        DataBase.savePlayerStats(name);
    }

    public static  synchronized void savePlayerStats(String name) {
        DataBase.savePlayerStats(name);
    }

    public static synchronized void incrementWins(String name) {
        DataBase.incrementWins(name);
    }

    public static void incrementTotalPoints(String username, Integer pts) {
        DataBase.incrementTotalPoints(username, pts);
    }

    public static void updateLastSeen(String username) {
        DataBase.updateLastSeen(username);
    }

    public static synchronized List<PlayerStats> getLeaderboard() {
        return DataBase.getLeaderboard();
    }

    public static void broadcastLobbyUpdate() {
        List<String> nicknames = new ArrayList<>();
        Set<String> readyPlayers = new HashSet<>();

        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getNickname() != null && !client.getNickname().equals("spectator_android")) {
                    nicknames.add(client.getNickname());
                    if(client.isReady()) readyPlayers.add(client.getNickname());
                }
            }
        }

        Message msg = new Message();
        msg.setType(MessageType.LOBBY_UPDATE);
        msg.setNicknames(nicknames);
        msg.setReadyPlayers(readyPlayers);

        //System.out.println("[SERVER] Sending lobby update: " + nicknames);

        String json = gson.toJson(msg);
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(json);
            }
        }
    }

    public static void resetAllReadyStatus() {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.setIsReady(false);
            }
        }
        broadcastLobbyUpdate();
    }

    public static void startGameLoop() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!paused){
                    updateProjectilesPositions();
                    updateTargetsPosition();
                    removeOutOfBoundsProjectiles();
                    broadcastProjectiles(activeProjectiles);
                    broadcastTargets();
                    broadcastGameState();
                }
            }
        }, 0, Config.PING_PERIOD);
    }

    private static void updateTargetsPosition() {
        synchronized (activeTargets) {
            long currentTime = System.currentTimeMillis();
            for(TargetData target : activeTargets) {
                double deltaTime = (currentTime - target.getLastUpdateTime()) / 1000.0;
                target.setLastUpdateTime(currentTime);

                // Обновление позиции
                double newY = target.getPosY() + target.getSpeed() * deltaTime;

                // Проверка границ
                if(newY < 0 || newY + (50 * target.getSize()) > Config.getCanvasHeight()) {
                    target.setSpeed(-target.getSpeed());
                    newY = Math.max(0, Math.min(newY, Config.getCanvasHeight() - 50 * target.getSize()));
                }

                target.setPosY(newY);
            }
        }
    }

    public static void broadcastGameState() {
        Message msg = new Message();
        msg.setType(MessageType.GAME_STATE_UPDATE);

        Map<String, Double> positions = new HashMap<>();
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Integer> arrows = new HashMap<>();

        synchronized(clients) {
            for (ClientHandler client : clients) {
                String nick = client.getNickname();
                if (nick != null) {
                    positions.put(nick, client.getPlayerY());
                    scores.put(nick, client.getScore());
                    arrows.put(nick, client.getArrowsCount());

                    // Проверка победы
                    if (client.getScore() >= Config.GAME_WIN_SCORE && !gameEnded) {
                        processGameEnd(nick, scores);
                        return; // Прекращаем рассылку, если игра окончена
                    }
                }
            }
        }

        msg.setPlayerPositions(positions);
        msg.setScores(scores);
        msg.setArrows(arrows);

        String json = gson.toJson(msg);
        synchronized(clients) {
            for(ClientHandler client : clients) {
                client.sendMessage(json);
            }
        }
    }

    protected static synchronized void processGameEnd(String winnerNick, Map<String, Integer> scores) {
        if (gameEnded) return;
        gameEnded = true;

        System.out.println("Game ended. Winner: " + winnerNick);

        Server.incrementWins(winnerNick);

        Message gameOverMsg = new Message();
        gameOverMsg.setType(MessageType.GAME_OVER);
        gameOverMsg.setContent(winnerNick);

        resetAllReadyStatus();

        String json = gson.toJson(gameOverMsg);
        synchronized(clients) {
            for(ClientHandler client : clients) {
                String currentNick = client.getNickname();
                Server.savePlayerStats(currentNick);
                Server.incrementTotalPoints(currentNick, scores.get(currentNick));
                Server.updateLastSeen(currentNick);
                client.sendMessage(json);
                client.resetGameState();
            }
        }

        broadcastLeaderboard();
        // сбрасываем состояние
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                gameEnded = false; // Разрешаем новую игру
                Server.activeTargets.clear();
                Server.activeProjectiles.clear();
            }
        }, 5000); // Через 5 секунд после окончания
    }

    private static void broadcastLeaderboard() {
        List<PlayerStats> leaders = DataBase.getLeaderboard();
        Message msg = new Message();
        msg.setType(MessageType.LEADERBOARD_UPDATE);
        msg.setLeaders(leaders);

        String json = gson.toJson(msg);
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(json);
            }
        }
    }

    public static void broadcastProjectiles(List<ProjectileData> projectiles) {
        Message msg = new Message();
        msg.setType(MessageType.PROJECTILE_UPDATE);
        msg.setProjectiles(projectiles);

        String json = gson.toJson(msg);
        synchronized (clients) {
            for(ClientHandler client : clients) {
                client.sendMessage(json);
            }
        }
    }

    public static void spawnAndBroadcastTargets() {
        System.out.println("Spawning new targets...");
        if (!activeTargets.isEmpty()) {
            System.err.println("Warning: activeTargets not empty before spawn!");
            activeTargets.clear();
        }

        double areaStart = Config.getCanvasWidth() - 100;
        double areaHeight = Config.getCanvasHeight();
        int cols = 4;
        double verticalSpacing = areaHeight / (cols + 1);

        List<TargetData> newTargets = new ArrayList<>();
        Random rand = new Random();

        for(int col = 0; col < cols; col++) {
            double size = 0.5 + rand.nextDouble() * 0.7;
            double x = areaStart - (col * 60);
            double y = verticalSpacing * (col + 1) - 25; // Центрируем по вертикали

            TargetData target = new TargetData(
                    x,
                    y,
                    size,
                    Config.ARCHER_TARGET_SPEED * (rand.nextBoolean() ? 1 : -1),
                    (int) (50 / size),
                    col
            );
            newTargets.add(target);
        }

        synchronized(activeTargets) {
            activeTargets.clear();
            activeTargets.addAll(newTargets);
        }

        Message spawnMsg = new Message();
        spawnMsg.setType(MessageType.TARGETS_SPAWN);
        spawnMsg.setTargets(newTargets);
        String json = gson.toJson(spawnMsg);
        synchronized (clients) {
            for(ClientHandler client : clients) {
                client.sendMessage(json);
            }
        }
        System.out.println("Spawned " + newTargets.size());
        broadcastTargets();
    }

    protected static void broadcastTargets() {
        System.out.println("Broadcasting " + activeTargets.size() + " targets");
//        if (activeTargets.isEmpty() && ) {
//            new Exception("Empty targets! Stack trace:").printStackTrace();
//        }
        List<TargetData> copy;
        synchronized (activeTargets) { // Блок синхронизации
            copy = new ArrayList<>(activeTargets); // Создаем копию
        }

        Message msg = new Message();
        msg.setType(MessageType.TARGETS_UPDATE);
        msg.setTargets(copy); // Используем копию для сериализации

        String json = gson.toJson(msg);
        synchronized (clients) {
            for(ClientHandler client : clients) {
                client.sendMessage(json);
            }
        }
    }

    public static void addProjectile(ProjectileData projectile) {
        activeProjectiles.add(projectile);
    }

    public static void removeOutOfBoundsProjectiles() {
        synchronized (activeProjectiles) {
            activeProjectiles.removeIf(p ->
                    p.getPosX() > Config.getWindowWidth() ||
                            p.getPosX() < 0 ||
                            p.getPosY() < 0 ||
                            p.getPosY() > Config.getWindowHeight()
            );
        }
    }

    public static void updateProjectilesPositions() {
        long currentTime = System.currentTimeMillis();
        synchronized (activeProjectiles) {
            for (ProjectileData p : activeProjectiles) {
                double deltaTime = (currentTime - p.getCreationTime()) / 1000.0;
                p.setPosX(p.getPosX() + p.getVelocity() * deltaTime);
            }
        }
    }
}

class ClientHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private String nickname;
    private Boolean isReady = false;
    private double playerY;

    private int arrowsCount = Config.ARCHER_INIT_ARROWS;
    private static Timer gameStartTimer;
    private static boolean isCountdownActive = false;

    private int score = 0;

    public ClientHandler(Socket clientSocket) {
        this.socket = clientSocket;
    }

    public void sendMessage(String json) {
        out.println(json);
    }

    public String getNickname() {
        return nickname;
    }

    public void setIsReady(Boolean isReady) {
        this.isReady = isReady;
    }

    public boolean isReady() {
        return isReady;
    }

    public double getPlayerY() {
        return this.playerY;
    }

    public int getScore() {
        return score;
    }

    public int getArrowsCount() {
        return arrowsCount;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            this.out = out;

            String joinMessage = in.readLine();
            if (joinMessage == null) return;

            Message joinMsg = Server.gson.fromJson(joinMessage, Message.class);

            if (joinMsg.getType() == MessageType.JOIN) {
                // Обработка nickname
                nickname = joinMsg.getContent().trim();
                if (joinMsg.getContent() != null && joinMsg.getContent().startsWith("spectator")) {
                    // spectator - не добавляем в clients, не участвует в лобби
                    this.nickname = joinMsg.getContent();
                    // Можно сохранить сокет, чтобы отвечать на запросы, но не добавлять в clients
                    System.out.println("Spectator connected: " + this.nickname);
                } else {
                    // Проверка уникальности
                    synchronized (clients) {
                        if (clients.stream().anyMatch(c -> c.nickname.equals(nickname))) {
                            sendMessage(Server.gson.toJson(new Message(MessageType.ERROR, "Name taken")));
                            return;
                        }
                    }

                    clients.add(this);
                    this.isReady = false;
                    Server.saveUser(nickname);
                    Server.updateLastSeen(nickname);
                    //System.out.println(DataBase.dbPath);
                    System.out.println("Registered: " + nickname);
                }
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                //System.out.println("[SERVER] Received: " + inputLine);
                Message msg = Server.gson.fromJson(inputLine, Message.class);
                switch (msg.getType()) {
                    case READY:
                        this.isReady = !this.isReady;
                        System.out.println(nickname + " ready: " + this.isReady);

                        // Добавляем задержку перед проверкой
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Server.broadcastLobbyUpdate();
                                new Timer().schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        checkAllReady();
                                    }
                                }, 300); // Дор задержка
                            }
                        }, 200); // Даём 200 мс на синхронизацию
                        break;
                    case PAUSE:
                        Server.paused = true;
                        break;
                    case RESUME:
                        Server.paused = false;
                        break;
                    case PLAYER_MOVE:
                        this.playerY = msg.getPlayerY();
                        break;
                    case SCORE_UPDATE:
                        this.score++;
                        break;
                    case PLAYER_SHOOT:
                        this.arrowsCount--;
                        //projectile data
                        ProjectileData newProjectile = msg.getProjectiles().get(0);
                        newProjectile.setVelocity(Config.ARCHER_ARROW_SPEED);
                        newProjectile.setShooter(this.nickname);
                        // server time stamp
                        newProjectile.setServerTime(System.currentTimeMillis());

                        Server.addProjectile(newProjectile); // add arrow on server
                        break;
                    case HIT:
                        System.out.println("HIT detected from " + msg.getShooter());
                        System.out.println("Target ID: " + msg.getHitTargetId() + ", Arrow ID: " + msg.getHitArrowId());

                        // Находим мишень
                        TargetData target = Server.activeTargets.stream()
                                .filter(t -> t.getId().equals(msg.getHitTargetId()))
                                .findFirst()
                                .orElse(null);

                        if (target != null) {
                            System.out.println("Target found, respawning...");

                            // Начисляем очки
                            if (this.nickname.equals(msg.getShooter())) {
                                this.score += msg.getPoints();
                                System.out.println("Points awarded: " + msg.getPoints() + ", Total: " + this.score);
                            }

                            // Респавним мишень
                            respawnTarget(target);

                            // Удаляем снаряд
                            Server.activeProjectiles.removeIf(p -> p.getId().equals(msg.getHitArrowId()));
                        } else {
                            System.out.println("Target not found!");
                        }
                        break;

                    case GET_LEADERBOARD:
                        List<PlayerStats> leaders = DataBase.getLeaderboard();
                        Message lbResponse = new Message();
                        lbResponse.setType(MessageType.LEADERBOARD_UPDATE);
                        lbResponse.setLeaders(leaders);

                        System.out.println("[SERVER] Sending leaderboard to: " + nickname);
                        System.out.println("[SERVER] JSON: " + Server.gson.toJson(lbResponse));
                        sendMessage(Server.gson.toJson(lbResponse));
                        break;

                    case RETURN_TO_LOBBY:
                        setIsReady(false);
                        Server.broadcastLobbyUpdate();
                        break;

                    case EXIT:
                        throw new IOException("Client exited");
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + nickname + " cause: " + e.getMessage());
        } finally {
            clients.remove(this);
            Server.broadcastLobbyUpdate();
        }
    }

    private void respawnTarget(TargetData target) {
        Random rand = new Random();

        // Новые параметры мишени
        double newSize = 0.5 + rand.nextDouble() * 0.7;
        double newY = rand.nextDouble() * (Config.getCanvasHeight() - 50 * newSize);

        // Обновляем мишень
        target.setPosY(newY);
        target.setSize(newSize);
        target.setPoints((int)(50 / newSize));
        target.setSpeed(Config.ARCHER_TARGET_SPEED * (rand.nextBoolean() ? 1 : -1));

        System.out.println("Respawned target " + target.getId() +
                " at Y=" + newY + " with size=" + newSize);

        // Отправляем обновление всем клиентам
        Message updateMsg = new Message();
        updateMsg.setType(MessageType.TARGET_UPDATE);
        updateMsg.setTargets(Collections.singletonList(target));

        String json = Server.gson.toJson(updateMsg);
        clients.forEach(c -> c.sendMessage(json));
    }

    public void resetGameState() {
        this.score = 0;
        this.arrowsCount = Config.ARCHER_INIT_ARROWS;
    }

    private void checkAllReady() {
//        synchronized (Server.clients) {
//            // 1. Явная проверка всех клиентов с логированием
//            boolean allReady = true;
//            for (ClientHandler client : Server.clients) {
//                if (!client.isReady()) {
//                    System.out.println("Player NOT ready: " + client.nickname);
//                    allReady = false;
//                }
//            }
//
//            // 2. Условия запуска/отмены
//            if (allReady && Server.clients.size() >= 2) {
//                if (!isCountdownActive) {
//                    System.out.println("Starting countdown!");
//                    startGameCountdown();
//                }
//            } else {
//                if (isCountdownActive) {
//                    System.out.println("Canceling countdown. Reason: " +
//                            "allReady=" + allReady +
//                            ", clients=" + Server.clients.size());
//                    cancelGameCountdown();
//                }
//            }
//        }
        synchronized (Server.clients) {
            System.out.println("=== Checking ready status ===");

            // 1. Логируем состояние всех игроков
            Server.clients.forEach(client ->
                    System.out.println("Player: " + client.nickname + " | Ready: " + client.isReady())
            );
            if (Server.clients.size() < 2) {
                System.out.println("[SERVER] Not enough players, ignoring ready check");
                return;
            }
            // 2. Проверяем условия
            boolean allReady = Server.clients.stream()
                    .allMatch(ClientHandler::isReady);
            int playersCount = Server.clients.size();

            System.out.println("All ready: " + allReady + " | Players: " + playersCount);

            // 3. Управление таймером
            if (allReady && playersCount >= 2) {
                if (!isCountdownActive) {
                    System.out.println("Starting game countdown!");
                    startGameCountdown();
                }
            } else {
                if (isCountdownActive) {
                    System.out.println("Canceling countdown - not all ready");
                    cancelGameCountdown();
                }
            }
        }
    }

    private void startGameCountdown() {
        isCountdownActive = true;

        // Полный сброс состояния
        synchronized (Server.activeTargets) {
            Server.activeTargets.clear();
        }
        synchronized (Server.activeProjectiles) {
            Server.activeProjectiles.clear();
        }
        Server.gameEnded = false;

        gameStartTimer = new Timer();
        gameStartTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (checkReadyStatus()) {
                    // 1. Очистка
                    Server.activeTargets.clear();
                    Server.activeProjectiles.clear();

                    // 2. Спавн новых мишеней
                    Server.spawnAndBroadcastTargets();

                    // 3. Уведомление клиентов
                    broadcastGameStart();

                    System.out.println("[SERVER] New game started. Clean state.");
                }
                isCountdownActive = false;
            }
        }, Config.READY_TIMER_TIME);
    }

    private static void broadcastGameStart() {
        Message startMsg = new Message();
        startMsg.setType(MessageType.GAME_START);
        String json = Server.gson.toJson(startMsg);

        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(json);
                System.out.println("[SERVER] Sent GAME_START to " + client.nickname);
            }
        }
    }

    private void cancelGameCountdown() {
        System.out.println("[SERVER] Game countdown cancelled.");
        if (gameStartTimer != null) {
            gameStartTimer.cancel();
            gameStartTimer = null;
        }
        isCountdownActive = false;

        Message cancelMsg = new Message();
        cancelMsg.setType(MessageType.GAME_CANCEL);
        String json = Server.gson.toJson(cancelMsg);
        for (ClientHandler client : clients) {
            client.sendMessage(json);
        }
    }

    private static void spawnNewTargetInColumn(TargetData target) {
        if(target == null) {
            System.err.println("Attempt to respawn null target!");
            return;
        }
        Random rand = new Random();

        // Генерация новой позиции Y с проверкой коллизий
        double newY;
        boolean collision;
        int attempts = 0;
        double newSize;

        do {
            collision = false;
            newY = rand.nextDouble() * (Config.getCanvasHeight() - 150);
            newSize = 0.5 + rand.nextDouble() * 0.7;
            double newHeight = 50 * newSize;

            // Проверка пересечения с другими мишенями
            synchronized(Server.activeTargets) {
                for(TargetData t : Server.activeTargets) {
                    if(!t.getId().equals(target.getId()) &&  // Исключаем текущую мишень из проверки
                            Math.abs(t.getPosY() - newY) < (newHeight + 50 * t.getSize())) {
                        collision = true;
                        break;
                    }
                }
            }
            attempts++;
        } while(collision && attempts < 10);

        // Обновляем параметры мишени
        target.setPosY(newY);
        target.setSize(newSize);
        int newPoints = (int)(50 / newSize);
        target.setPoints(newPoints);
        target.setSpeed(Config.ARCHER_TARGET_SPEED * (rand.nextBoolean() ? 1 : -1));

        System.out.println("Respawned target " + target.getId() +
                " with new points: " + newPoints);

        // Немедленная рассылка обновления
        Message updateMsg = new Message();
        updateMsg.setType(MessageType.TARGET_UPDATE); // Новый тип сообщения
        updateMsg.setTargets(Collections.singletonList(target));

        String json = Server.gson.toJson(updateMsg);
        for (ClientHandler client : clients) {
            client.sendMessage(json);
        }
    }

    private boolean checkReadyStatus() {
        synchronized (clients) {
            return clients.stream().allMatch(ClientHandler::isReady) && clients.size() >= 2;
        }
    }
}