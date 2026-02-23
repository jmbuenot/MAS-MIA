public class MoveLeftOperator extends GenericOperator {
    
    @Override
    public State operate(State previousState)
    {
        MapNavigationState mapNavState = ((MapNavigationState)previousState);

        Position newPos = null;
        try {
            newPos = (Position) mapNavState.position.clone();
            newPos.y--;
        } catch (Exception e)
        {
            e.printStackTrace();
        }
            
        return new MapNavigationState(newPos);
    }
    
    @Override
    public String toString()
    {
        return "MoveLeft";
    }
}

