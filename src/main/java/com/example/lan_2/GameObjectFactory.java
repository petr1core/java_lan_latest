package com.example.lan_2;

public class GameObjectFactory {
    public static Archer createArcher(String nickname, double posY, boolean isEnemy) {
        return new Archer(nickname, posY, Config.getCanvasWidth(), isEnemy);
    }

    public static Projectile createProjectile(double posX, double posY, String shooter) {
        Projectile p = new Projectile(posX, posY,
                Config.ARCHER_ARROW_SPEED,
                Config.ARCHER_ARROW_IMG_PATH,
                Config.getWindowWidth());
        p.setShooter(shooter);
        return p;
    }

    public static Target createTarget(TargetData data) {
        return new Target(data, Config.TARGET_IMG_PATH);
    }
}
