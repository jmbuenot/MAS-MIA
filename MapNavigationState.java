import jade.util.leap.Serializable;

public class MapNavigationState extends State implements Serializable{

    public Position position;

    public MapNavigationState(Position position)
    {
        this.position = position;
    }

    public boolean Equals(MapNavigationState stateToCheck)
    {
        return (stateToCheck.position.equals(position));
    }

    @Override
	public Object clone() throws CloneNotSupportedException {
	    return new MapNavigationState((Position) this.position.clone());
	}
    
    @Override
    public String toString()
    {
        return String.format("Navigation state: ({%d},{%d})", position.x, position.y);
    }

}
