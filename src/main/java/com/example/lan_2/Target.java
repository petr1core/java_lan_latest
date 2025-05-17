package com.example.lan_2;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;

import java.util.UUID;


public class Target {
    private final String id;
    private final double baseWidth  = 50;
    private final double baseHeight  = 50;
    private final double size;
    private double speed;

    private final int points;
    private final Image image;
    private double posX, posY;
    private double targetPosX, targetPosY;
    private boolean active = true;

    public Target(TargetData data, String imagePath) {
        this.id = data.getId();
        this.posX = data.getPosX();
        this.posY = data.getPosY();
        this.size = data.getSize();
        this.speed = data.getSpeed();
        this.points = data.getPoints();
        this.image = new Image(imagePath);
    }

    public void updateFromData(TargetData data) { //////////////////////////////////
//        this.posX = data.getPosX();
//        this.posY = data.getPosY();
        this.targetPosX = data.getPosX();
        this.targetPosY = data.getPosY();
        this.speed = data.getSpeed();
    }

    public void updatePosition(double deltaTime) {
        double lerpFactor = 8.0 * deltaTime;
        lerpFactor = Math.min(lerpFactor, 1.0); // Ограничиваем максимальный фактор

        posX = posX + (targetPosX - posX) * lerpFactor;
        posY = posY + (targetPosY - posY) * lerpFactor;
    }

    public boolean isHit(Projectile projectile) {
        double targetWidth = 50 * this.size;
        double targetHeight = 50 * this.size;

        // Размеры снаряда
        double projWidth = projectile.getWidth();
        double projHeight = projectile.getHeight();

        // Проверка пересечения прямоугольников
        boolean xOverlap = projectile.getPosX() < this.posX + targetWidth &&
                projectile.getPosX() + projWidth > this.posX;

        boolean yOverlap = projectile.getPosY() < this.posY + targetHeight &&
                projectile.getPosY() + projHeight > this.posY;

        return xOverlap && yOverlap;
    }

    public void draw(GraphicsContext gc) {
        if (image != null) {
            gc.drawImage(image, posX, posY, baseWidth * size, baseHeight * size);
        } else {
            gc.setFill(Color.PINK);
            gc.fillRect(posX, posY, baseWidth * size, baseHeight * size);
        }
    }

    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getId() { return id; }
    public int getPoints() { return points; }
}
