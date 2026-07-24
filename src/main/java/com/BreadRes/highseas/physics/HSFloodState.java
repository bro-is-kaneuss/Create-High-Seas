package com.BreadRes.highseas.physics;

import org.joml.Vector3d;

public final class HSFloodState {
    private static final double EPSILON = 0.0001;

    private double fill;
    private double waterVolume;
    private double floodableVolume;
    private double breachPressure;
    private double overtoppingPressure;
    private int breachCount;
    private long lastScanTime = -999999L;
    private final Vector3d localCenter = new Vector3d();

    public double fill() {
        return fill;
    }

    public double waterVolume() {
        return waterVolume;
    }

    public double floodableVolume() {
        return floodableVolume;
    }

    public double extraWaterMass() {
        return waterVolume * HSFloodingSystem.WATER_DENSITY;
    }

    public double breachPressure() {
        return breachPressure;
    }

    public double overtoppingPressure() {
        return overtoppingPressure;
    }

    public int breachCount() {
        return breachCount;
    }

    public long lastScanTime() {
        return lastScanTime;
    }

    public Vector3d localCenter() {
        return new Vector3d(localCenter);
    }

    void setScan(
            long gameTime,
            double scannedFloodableVolume,
            int breachCount,
            double breachPressure,
            double overtoppingPressure,
            Vector3d scannedLocalCenter
    ) {
        this.lastScanTime = gameTime;
        this.breachCount = Math.max(0, breachCount);
        this.breachPressure = Math.max(0.0, breachPressure);
        this.overtoppingPressure = Math.max(0.0, overtoppingPressure);

        if (scannedFloodableVolume > 0.0) {
            this.floodableVolume = scannedFloodableVolume;
            this.waterVolume = clamp(this.waterVolume, 0.0, this.floodableVolume);

            if (scannedLocalCenter != null) {
                this.localCenter.set(scannedLocalCenter);
            }
        } else if (this.waterVolume <= EPSILON && this.breachCount <= 0 && this.overtoppingPressure <= 0.0) {
            this.floodableVolume = 0.0;
            this.waterVolume = 0.0;

            if (scannedLocalCenter != null) {
                this.localCenter.set(scannedLocalCenter);
            }
        }

        updateFill();
    }

    void resetIfNoVolume(long gameTime, Vector3d fallbackCenter) {
        this.lastScanTime = gameTime;
        this.breachCount = 0;
        this.breachPressure = 0.0;
        this.overtoppingPressure = 0.0;
        this.floodableVolume = 0.0;
        this.waterVolume = 0.0;
        this.fill = 0.0;

        if (fallbackCenter != null) {
            this.localCenter.set(fallbackCenter);
        }
    }

    void tick(double timeStep) {
        if (floodableVolume <= EPSILON) {
            fill = 0.0;
            waterVolume = 0.0;
            return;
        }

        if (breachCount > 0 && breachPressure > 0.0) {
            double rate = HSFloodingSystem.FLOOD_RATE * breachCount * Math.sqrt(breachPressure);
            waterVolume = approach(waterVolume, floodableVolume, rate * timeStep * floodableVolume);
        }

        if (overtoppingPressure > 0.0) {
            double rate = HSFloodingSystem.OVERTOPPING_RATE * Math.sqrt(overtoppingPressure);
            waterVolume = approach(waterVolume, floodableVolume, rate * timeStep * floodableVolume);
        }

        updateFill();
    }

    private void updateFill() {
        if (floodableVolume <= EPSILON) {
            fill = 0.0;
            return;
        }

        waterVolume = clamp(waterVolume, 0.0, floodableVolume);
        fill = clamp(waterVolume / floodableVolume, 0.0, 1.0);
    }

    private static double approach(double current, double target, double amount) {
        if (current < target) {
            return Math.min(target, current + amount);
        }

        if (current > target) {
            return Math.max(target, current - amount);
        }

        return current;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }

        if (value > max) {
            return max;
        }

        return value;
    }
}