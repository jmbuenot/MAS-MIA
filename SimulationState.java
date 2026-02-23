package itemworld;

import java.io.Serializable;
import java.util.Set;

public class SimulationState implements Serializable {
    public final int width, height;
    public Set<Position> items;  // puede ser null si no hay actualización
    public Set<Position> traps;  // puede ser null si no hay actualización
    public Position self;
    public int score;
    public int round;

    public SimulationState(int width, int height) {
        this.width = width;
        this.height = height;
    }
}