import jade.util.leap.Serializable;

public abstract class GenericOperator implements Serializable
{
    public abstract State operate(State inputState);

    //public abstract boolean isAplicable(State inputState);
}
