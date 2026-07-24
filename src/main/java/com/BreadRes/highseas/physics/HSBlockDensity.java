package com.BreadRes.highseas.physics;

public record HSBlockDensity(
        double density,
        double volume,
        boolean displacesWater,
        boolean contributesMass
) {
    public static final HSBlockDensity AIR = new HSBlockDensity(0.0, 0.0, false, false);
    public static final HSBlockDensity DEFAULT = new HSBlockDensity(1.0, 1.0, true, true);

    public double mass() {
        return density * volume;
    }

    public double buoyancyVolume() {
        return displacesWater ? volume : 0.0;
    }
}