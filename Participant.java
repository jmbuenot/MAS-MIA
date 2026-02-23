import java.util.Arrays;
import java.util.LinkedList;

import jade.core.AID;

public class Participant {
    
    private AID agentAID; // identifies the participant agent
    private SimulationState simulationState; // represents participant's agents perception of the world
    private int numItems; // counter for items scored by the participant agent
    private int numTraps; // counter for traps in which the participant agent has fallen into
    final int commitment; // unmutable: defines participant's agent commitment
    private int currentCommitment; // current commitment of the participant in the simulation

    public Participant(AID agentAID, SimulationState simulationState, int commitment) {
        this.agentAID = agentAID;
        this.simulationState = simulationState;
        this.numItems = 0;
        this.numTraps = 0;
        this.commitment = commitment;
        this.currentCommitment = commitment;
    }

    // Gets participant's global score
    public int getScore()
    {
        return numItems - numTraps;
    }

    public int getCommitment()
    {
        return commitment;
    }

    public AID getAID()
    {
        return agentAID;
    }

    public SimulationState getSimulationState()
    {
        return simulationState;
    }

    public void updateState(SimulationState simulationState)
    {
        this.simulationState = simulationState;
    }

    public int increaseItemCounter(int howMuch)
    {
        numItems += howMuch;
        return numItems;
    }

    public int increaseTrapCounter(int howMuch)
    {
        numTraps += howMuch;
        return numTraps;
    }

    public int decreaseCommitmentSteps(int howMuch)
    {
        currentCommitment -= howMuch;
        return currentCommitment;
    }

    public void resetCommitmentSteps()
    {
        currentCommitment = commitment;
    }

    /* Mostly for debugging purposes. Returns string representing current participant's state */
    @Override
    public String toString() {
        LinkedList<Position> posToHighlight = new LinkedList<Position>(Arrays.asList(simulationState.getPosition()));
                
        // Information about the participant's state
        return String.format("\nName: %s\nPosition: (%d,%d)\nCommitment: %d-%d\nScore: %d\nNumTraps: %d\nMap:\n%s", 
            agentAID.getLocalName(), simulationState.getPosition().x, simulationState.getPosition().y, 
            currentCommitment, commitment, getScore(), numTraps, simulationState.getMap().toString(posToHighlight));
    }
}
