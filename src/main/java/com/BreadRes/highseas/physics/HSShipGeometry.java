package com.BreadRes.highseas.physics;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;

public final class HSShipGeometry {

    private final int minX;
    private final int minY;
    private final int minZ;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private final HSCell[][][] cells;

    private boolean outsideComputed;

    public HSShipGeometry(BoundingBox3ic bounds) {

        minX = bounds.minX();
        minY = bounds.minY();
        minZ = bounds.minZ();

        sizeX = bounds.maxX() - bounds.minX() + 1;
        sizeY = bounds.maxY() - bounds.minY() + 1;
        sizeZ = bounds.maxZ() - bounds.minZ() + 1;

        cells = new HSCell[sizeX][sizeY][sizeZ];

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {

                    cells[x][y][z] = new HSCell(
                            x + minX,
                            y + minY,
                            z + minZ
                    );

                }
            }
        }
    }

    public HSShipGeometry(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;

        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;

        cells = new HSCell[sizeX][sizeY][sizeZ];

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {

                    cells[x][y][z] = new HSCell(
                            x + minX,
                            y + minY,
                            z + minZ
                    );

                }
            }
        }
    }

    public HSCell get(int x, int y, int z) {

        x -= minX;
        y -= minY;
        z -= minZ;

        if (x < 0 || y < 0 || z < 0)
            return null;

        if (x >= sizeX || y >= sizeY || z >= sizeZ)
            return null;

        return cells[x][y][z];
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return minX + sizeX - 1;
    }

    public int maxY() {
        return minY + sizeY - 1;
    }

    public int maxZ() {
        return minZ + sizeZ - 1;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public boolean outsideComputed() {
        return outsideComputed;
    }

    public void setOutsideComputed(boolean value) {
        outsideComputed = value;
    }
}