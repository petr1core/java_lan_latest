package com.example.lan_2;

import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.Transaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class DataBase {
    public static final String dbPath = "jdbc:sqlite:"+System.getProperty("user.dir")+"\\src\\main\\resources\\DataBase.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbPath);
    }

    public static void initialize() {
        System.out.println("Hibernate initialized");
    }

    public static synchronized List<PlayerStats> getLeaderboard() {
        try (Session session = HibernateUtil.getSession()) {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<PlayerStats> criteria = builder.createQuery(PlayerStats.class);
            Root<PlayerStats> root = criteria.from(PlayerStats.class);

            // Упоряд по wins (по убыванию) затем по totalPoints (по убыванию)
            criteria.orderBy(
                    builder.desc(root.get("wins")),
                    builder.desc(root.get("totalPoints"))
            );

            return session.createQuery(criteria)
                    .setMaxResults(50)
                    .getResultList();
        } catch (Exception e) {
            System.err.println("Ошибка получения таблицы лидеров: " + e.getMessage());
            return List.of();
        }
    }

    public static synchronized void savePlayerStats(String name) {
        if (name != null && !name.equals("spectator_android")) {
            try (Session session = HibernateUtil.getSession()) {
                Transaction tx = session.beginTransaction();

                PlayerStats player = session.bySimpleNaturalId(PlayerStats.class)
                        .load(name);

                if (player == null) {
                    player = new PlayerStats(name);
                    System.out.println("db: created new player: " + name);
                } else {
                    System.out.println("db: updated existing player: " + name);
                }

                player.updateLastSeen();
                session.saveOrUpdate(player);
                tx.commit();
            } catch (Exception e) {
                System.err.println("Ошибка сохранения игрока: " + e.getMessage());
            }
        }
    }

    public static synchronized void incrementWins(String name) {
        Session session = HibernateUtil.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            PlayerStats player = session.bySimpleNaturalId(PlayerStats.class)
                    .load(name);

            if (player != null) {
                player.setWins(player.getWins() + 1);
                session.merge(player);
                tx.commit();
                System.out.println("Successfully incremented wins for " + name);
            } else {
                System.out.println("Player " + name + " not found in database");
                if (tx != null) tx.rollback();
            }
        } catch (Exception e) {
            System.err.println("Error incrementing wins: " + e.getMessage());
            if (tx != null) tx.rollback();
        } finally {
            session.close();
        }
    }

    public static synchronized void incrementTotalPoints(String name, Integer points) {
        Session session = null;
        Transaction tx = null;
        try {
            session = HibernateUtil.getSession();
            tx = session.beginTransaction();

            PlayerStats player = session.byNaturalId(PlayerStats.class)
                    .using("name", name)
                    .load();

            if (player != null) {
                player.setTotalPoints(player.getTotalPoints() + points);
                session.update(player);
                tx.commit();
                System.out.println("Added " + points + " points to " + name +
                        ", total points: " + player.getTotalPoints());
            } else {
                System.err.println("Player not found: " + name);
                if (tx != null) tx.rollback();
            }
        } catch (Exception e) {
            System.err.println("Error incrementing points: " + e.getMessage());
            if (tx != null) tx.rollback();
        } finally {
            if (session != null) session.close();
        }
    }

    public static synchronized void updateLastSeen(String name) {
        savePlayerStats(name);
    }

    public static void shutdown() {
        HibernateUtil.shutdown();
    }
}
