import java.util.LinkedList;

import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class SimulationManagerBehaviour extends Behaviour {
    
    public void action() {

        LinkedList<Participant> participants = ((SimulatorAgent)myAgent).getParticipants();
        
        while (!((SimulatorAgent)myAgent).simulationComplete())
        {
            System.out.printf("\n%s: starting simulation round %d\n", myAgent.getAID().getLocalName(), ((SimulatorAgent)myAgent).roundCount);

            // Loop through all participants and ask for their next action
            for(Participant participant : participants)
            {
                // Ask movement
                ACLMessage reqp = new ACLMessage(ACLMessage.REQUEST);
                
                reqp.setSender(myAgent.getAID());
                reqp.setConversationId("request-action");
                reqp.setReplyWith("request"+System.currentTimeMillis());
                reqp.addReceiver(participant.getAID());
                
                System.out.println("\n"+myAgent.getAID().getLocalName()+": Sending request to "+ participant.getAID().getLocalName());
                myAgent.send(reqp);

                MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchConversationId("request-action"),
                    MessageTemplate.MatchInReplyTo(reqp.getReplyWith()));
                mt = MessageTemplate.and(mt, MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                /* agent totally stops until receiving this type of message or no response in 10s, 
                   then assuming opportunity for response passed */
                ACLMessage msg = myAgent.blockingReceive(mt, 10000); 
                if (msg != null)
                {
                    // Process action
                    try {
                        Position requestedPosition = (Position)msg.getContentObject();
                    
                        // Check if valid, process action, and calculate new participant state
                        SimulationState newSimulationState = processAction(requestedPosition, participant);

                        // Return state
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setConversationId("update-state");
                        
                        // setContent() and setContentObject both write to the same field of the ACLMessage
                        // hence no possible to set both of them separatedly
                        reply.setContentObject(newSimulationState);
                        myAgent.send(reply);
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }        
                }
            } // end processing actions for this round

            // Show simulation state
            ((SimulatorAgent)myAgent).showOverallState();

            // Increase round counter
            ((SimulatorAgent)myAgent).roundCount++;

            // Check wheter map needs to be updated
            // Notice that next request for decision is based on the preceding state
            // Thus effectively meaning possible wrong decision, 
            // regardless of client agent's correspsonding commitment configurations
            ((SimulatorAgent)myAgent).checkMapMustChange();
        }
    }

    public boolean done() {
        return ((SimulatorAgent)myAgent).simulationComplete();
    }

    private LinkedList<Position> getParticipantsPositions()
    {
        LinkedList<Participant> participants = ((SimulatorAgent)myAgent).getParticipants();
        LinkedList<Position> participantsPos = new LinkedList<Position>();

        for(Participant participant : participants)
        {
            participantsPos.add(participant.getSimulationState().getPosition());
        }

        return participantsPos;
    }

    private boolean occupiedByAgentPosition(Position pos)
    {
        return getParticipantsPositions().contains(pos);
    }

    private SimulationState processAction(Position newPosition, Participant participant)
    {
        // Process action and act accordingly
        SimulationState newState = participant.getSimulationState(); // by default "old" state
        
        // Get updated version of the map from the Simulator agent
        Map currentMap = ((SimulatorAgent)myAgent).getCurrentMap();

        boolean validRequest = isValidMovement(newPosition, false) && 
            !occupiedByAgentPosition(newPosition);

        int remainingCommitmentSteps = participant.decreaseCommitmentSteps(1);
        
        // Update state and corresponding commitment/scoring fields
        if (validRequest)
        {            
            // Check whether next movement involves reaching an item
            if (currentMap.isItemPosition(newPosition))
            {
                // Score point
                participant.increaseItemCounter(1);

                // Participant is always informed about having scored the item,
                // (to avoid risk of getting trapped in local minima)
                // but full updated map (i.e. currentMap) will only be shared
                // according to its "commitment counter"
                participant.getSimulationState().getMap().clearPosition(newPosition);

                // Update simulator's map (remove item, generate new)
                currentMap.clearPosition(newPosition);
                currentMap.generateNewItem();

            } else {

                // valid movement other than to item position
                if (currentMap.isTrapPosition(newPosition))
                {
                    participant.increaseTrapCounter(1);

                    // It remains trapped
                    newPosition = participant.getSimulationState().getPosition();
                }

                // otherwise "newPosition" involves valid movement toward empty position

                // regardless, an agent with unupdated map (e.g. because high commitment)
                // might still "believe" there is some item there
                // hence we try to communicate that, again to avoid risk of getting
                // trapped in local minima
                if (participant.getSimulationState().getMap().isItemPosition(newPosition))
                    participant.getSimulationState().getMap().clearPosition(newPosition);
            }
        }
        else 
        {
            // invalid position, it remains where it is
            newPosition = participant.getSimulationState().getPosition();
        }
        // check if fully updated version of the map needs to be provided
        if (remainingCommitmentSteps == 0)
        {
            try {
                newState = new SimulationState((Map) currentMap.clone(), newPosition);
            } catch (Exception e)
            {
                e.printStackTrace();
            }

            // Restart commitment counter
            participant.resetCommitmentSteps();
        }
        else {
            // New position but with "unupdated" map
            newState = new SimulationState(participant.getSimulationState().getMap(), newPosition);
        }
        
        // Store new participant's state
        participant.updateState(newState);
        
        return newState;
    }

    private boolean isValidMovement(Position pos, boolean avoidTraps)
    {
        boolean valid = true;

        valid &= ((SimulatorAgent)myAgent).getCurrentMap().withinMapLimits(pos);
        if (avoidTraps)
            valid &= !((SimulatorAgent)myAgent).getCurrentMap().isTrapPosition(pos);
            
        return valid;
    }
}