import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/* Simulator Agent behaviour to handle participant's registrations requests */
public class RegisterParticipantsBehaviour extends CyclicBehaviour {

    public void action() {

        try {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId("join-simulation-request"),
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
        
            ACLMessage msg = myAgent.receive(mt);
                    
            if (msg != null) {
                AID requester = msg.getSender();
                int agentCommitment = Integer.parseInt(msg.getContent()); // read agent's commitment
                ACLMessage reply = msg.createReply(); // this already sets "sender" and "conversationId"
                if (((SimulatorAgent)myAgent).participantsComplete())
                {
                    reply.setPerformative(ACLMessage.REFUSE);
                } else {
                    reply.setPerformative(ACLMessage.AGREE);
                    SimulationState initialState = ((SimulatorAgent)myAgent).addParticipant(requester, agentCommitment);
                    reply.setContentObject(initialState);
                }
                
                myAgent.send(reply);       
            }
            else {
                block(); // until new message arrives
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }

    }    

}

