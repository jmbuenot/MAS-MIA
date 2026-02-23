import java.io.Serializable;

public class SimulationState implements Serializable {

    private Map _map;
    private Position _position;

    public SimulationState(Map map, Position position)
    {
        _map = map;
        _position = position;
    }

    public void updateState(Map map, Position position)
    {
        _map = map;
        _position = position;
    }

    public Map getMap()
    {
        return _map;
    }

    public Position getPosition()
    {
        return _position;
    }

}
