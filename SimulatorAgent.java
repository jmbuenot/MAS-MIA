import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAException;

import java.util.LinkedList;

public class SimulatorAgent extends Agent {
    
    private LinkedList<Participant> participants;
    private Map _map;
    
    // Map parameters
    int mapSize = 10;
    int numItems = 5;
    int numTraps = 10;
    
    // Simulation parameters
    int numParticipants = 2;
    int numSimRounds = 1000;
    int numStepsMapReDist = 10; // If equals numSimRounds, implies no map rescheduling
    
    // Simulation state
    public boolean simulationStarted = false;
    int roundCount = 0;

    @Override
    protected void setup() {
        System.out.println("Starting setup of simulator agent...");

        // Initialize map according to parameters
        try{
            _map = new Map(mapSize, mapSize, numItems, numTraps);
        } catch(Exception e) {
            e.printStackTrace();
        }
        _map.show();
        
        // Register in Service Facilitator (yellow pages)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("SimulatorService");
        sd.setName("simulation-service");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Start behaviour to register participants
        participants = new LinkedList<Participant>();
        
        addBehaviour(new RegisterParticipantsBehaviour());

        // Main control behaviour
        addBehaviour(new TickerBehaviour(this, 10000) {
            protected void onTick(){
                
                // Check if game needs to end
                if (simulationComplete())
                {
                    System.out.println(getAID().getLocalName()+": SIMULATION ENDED!!!");

                    // Comunicate simulation has ended to all participants and show final results
                    for(Participant participant : participants)
                    {
                        // Ask movement
                        ACLMessage reqp = new ACLMessage(ACLMessage.INFORM);
                        reqp.setSender(myAgent.getAID());
                        reqp.setConversationId("simulation-complete");
                        reqp.addReceiver(participant.getAID());
                        send(reqp);
                    }

                    showOverallState();

                    doDelete();
                }
                
                // Either simulation running or not started
                
                // Check if game can be started
                if (participantsComplete() & !simulationStarted)
                {
                    System.out.println(getAID().getLocalName()+": SIMULATION CAN START!!!");

                    simulationStarted = true;
                    
                    addBehaviour(new SimulationManagerBehaviour());
                    
                } else
                {
                    if (!simulationStarted)
                        System.out.printf("\n%s %d / %d registered participants, waiting for more to join...", 
                            getAID().getLocalName(), participants.size(), numParticipants);
                    
                    // else simulation is running but not yet complet, do nothing 
                }
            }
        }); // end ticker behaviour

    }

    @Override
    protected void takeDown() {
        super.takeDown();

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    public Map getCurrentMap()
    {
        return _map;
    }

    public LinkedList<Participant> getParticipants()
    {
        return participants;
    }

    public boolean participantsComplete()
    {
        return participants.size() == numParticipants;
    }

    public boolean simulationComplete()
    {
        return roundCount == numSimRounds;
    }

    public void checkMapMustChange()
    {
        if (roundCount % numStepsMapReDist == 0)
        {
            _map.redistributeMap();
            System.out.println("MAP RESCHEDULING!!");
        }
    }

    // Used when accepting a participant to join the simulation (waiting to start)
    // It returns its initial SimulationState to be communicated to the accepted participant
    public SimulationState addParticipant(AID agentAID, int agentCommitment)
    {
        SimulationState initialState = null;
        try {
            initialState = new SimulationState((Map)_map.clone(), _map.searchRandomEmtpyPosition());
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        participants.add(new Participant(agentAID, initialState, agentCommitment));
        
        return initialState;
    }

    public void showOverallState()
    {
        // Retrieve participant's positions
        LinkedList<Position> posToHighlight = new LinkedList<>();
        for(Participant participant : participants)
        {
            posToHighlight.add(participant.getSimulationState().getPosition());
        }
        
        // SimulatorAgent's simulation state
        System.out.println("\nSimulator's agent status:");
        System.out.print(_map.toString(posToHighlight));
        //_map.show(); // map with no highlighted participant's position
        
        // Show each participant's simulation state
        System.out.println("\nParticipant's status:");
        for(Participant participant : participants)
        {
            System.out.print(participant.toString());
        } 
        
        // debug: wait pressing enter to continue
        /*try {
            System.in.read();
        } catch (Exception e)
        {
            e.printStackTrace();
        }*/
    }
}