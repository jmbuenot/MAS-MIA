import java.util.LinkedList;

public class MapNavigator {
    
    private LinkedList<GenericOperator> _operators;

    private void initializeOperatorsList()
    {
        // Assign valid operators for the problem
        _operators = new LinkedList<GenericOperator>();
        _operators.add(new MoveUpOperator());
        _operators.add(new MoveDownOperator());
        _operators.add(new MoveLeftOperator());
        _operators.add(new MoveRightOperator());
    }

    public MapNavigator() {
        initializeOperatorsList();
    }

    public LinkedList<Position> getNextPossiblePositions(Map map, Position currentPos)
    {
        LinkedList<Position> possiblePos = new LinkedList<Position>();
        
        MapNavigationState currentMapNavigationState = new MapNavigationState(currentPos);
                
        Position tmpPos = null;
        for(GenericOperator op : _operators)
        {
            tmpPos = ((MapNavigationState)op.operate(currentMapNavigationState)).position;
            
            if (map.withinMapLimits(tmpPos))
                possiblePos.add(tmpPos);
        }

        return possiblePos;
    }
}
