package com.BreadRes.highseas.physics;

public final class HSCell {

    private final int x;
    private final int y;
    private final int z;

    private boolean solid;
    private boolean outside;
    private boolean flooded;
    private boolean breach;
    private int compartment = -1;

    public HSCell(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public boolean solid() {
        return solid;
    }

    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    public boolean outside() {
        return outside;
    }

    public void setOutside(boolean outside) {
        this.outside = outside;
    }

    public boolean flooded() {
        return flooded;
    }

    public void setFlooded(boolean flooded) {
        this.flooded = flooded;
    }

    public boolean passable() {
        return !solid;
    }

    public boolean breach() {
        return breach;
    }

    public void setBreach(boolean breach) {
        this.breach = breach;
    }

    public int compartment() {
        return compartment;
    }

    public void setCompartment(int compartment) {
        this.compartment = compartment;
    }
}