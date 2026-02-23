package itemworld;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SimulationAgent (Simulator): referee + world state holder.
 *
 * Join protocol:
 *  Participant -> REQUEST (conversationId="join-simulation-request", content=commitment int)
 *  Simulator   -> AGREE or REFUSE (if AGREE attaches SimulationState initial)
 *
 * Turn protocol:
 *  Simulator   -> REQUEST (conversationId="request-action")
 *  Participant -> PROPOSE (conversationId="request-action", contentObject=Position)
 *  Simulator   -> INFORM  (conversationId="update-state", contentObject=SimulationState)
 *
 * End:
 *  Simulator   -> INFORM (conversationId="simulation-complete")
 */
public class SimulationAgent extends Agent {

    // ==== DF Service ====
    private static final String SERVICE_TYPE = "SimulatorService";
    private static final String SERVICE_NAME = "ItemWorldSimulator";

    // ==== Configuration (defaults) ====
    private int width = 10, height = 10;
    private int numItems = 3, numTraps = 3;
    private int maxParticipants = 2;
    private int totalRounds = 500;
    private int rescheduleEvery = 0;          // 0 => no reschedule
    private long actionTimeoutMs = 10_000;    // 10 seconds

    // ==== Real world state ====
    private final Set<Position> items = new HashSet<>();
    private final Set<Position> traps = new HashSet<>();
    private final Map<AID, Position> agentPos = new HashMap<>();
    private final Map<AID, Integer> scores = new HashMap<>();
    private final Map<AID, Integer> commitments = new HashMap<>();
    private final Map<AID, Integer> lastSyncRound = new HashMap<>();
    private final List<AID> participants = new ArrayList<>();

    @Override
    protected void setup() {
        readArgs(getArguments());
        initWorld();
        registerServiceInDF();

        addBehaviour(new JoinThenRunBehaviour());
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (Exception ignored) {}
        System.out.println("SimulationAgent finished.");
    }

    // -------------------- Args --------------------
    private void readArgs(Object[] args) {
        // Optional: width height numItems numTraps maxParticipants totalRounds rescheduleEvery
        if (args == null) return;
        try {
            if (args.length > 0) width = Integer.parseInt(args[0].toString());
            if (args.length > 1) height = Integer.parseInt(args[1].toString());
            if (args.length > 2) numItems = Integer.parseInt(args[2].toString());
            if (args.length > 3) numTraps = Integer.parseInt(args[3].toString());
            if (args.length > 4) maxParticipants = Integer.parseInt(args[4].toString());
            if (args.length > 5) totalRounds = Integer.parseInt(args[5].toString());
            if (args.length > 6) rescheduleEvery = Integer.parseInt(args[6].toString());
        } catch (Exception e) {
            System.out.println("Warning: could not parse SimulationAgent args. Using defaults.");
        }
    }

    // -------------------- DF --------------------
    private void registerServiceInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_TYPE);
        sd.setName(SERVICE_NAME);
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("Registered DF service: " + SERVICE_TYPE + " (" + SERVICE_NAME + ")");
        } catch (FIPAException e) {
            throw new RuntimeException("DF registration failed", e);
        }
    }

    // -------------------- World init/reschedule --------------------
    private void initWorld() {
        items.clear();
        traps.clear();
        agentPos.clear();
        scores.clear();
        commitments.clear();
        lastSyncRound.clear();
        participants.clear();

        while (traps.size() < numTraps) traps.add(randomFreeCell(Collections.emptySet()));
        while (items.size() < numItems) items.add(randomFreeCell(traps));

        System.out.printf("World: %dx%d | items=%d traps=%d | rounds=%d | rescheduleEvery=%d%n",
                width, height, items.size(), traps.size(), totalRounds, rescheduleEvery);
    }

    private void rescheduleWorld() {
        items.clear();
        traps.clear();

        while (traps.size() < numTraps) traps.add(randomFreeCell(Collections.emptySet()));
        while (items.size() < numItems) items.add(randomFreeCell(traps));

        System.out.println("World RESCHEDULED.");
    }

    private Position randomFreeCell(Set<Position> forbidden) {
        while (true) {
            int x = ThreadLocalRandom.current().nextInt(0, width);
            int y = ThreadLocalRandom.current().nextInt(0, height);
            Position p = new Position(x, y);

            if (forbidden.contains(p)) continue;
            if (items.contains(p) || traps.contains(p)) continue;
            if (agentPos.containsValue(p)) continue;
            return p;
        }
    }

    // -------------------- Main behaviour --------------------
    private class JoinThenRunBehaviour extends Behaviour {
        private boolean joinPhaseDone = false;
        private boolean finished = false;

        private int round = 0;
        private int idx = 0;

        private final MessageTemplate joinTpl = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchConversationId("join-simulation-request")
        );

        @Override
        public void action() {
            if (!joinPhaseDone) {
                handleJoinRequests();
                if (participants.size() >= maxParticipants) {
                    joinPhaseDone = true;
                    for (AID p : participants) lastSyncRound.put(p, 0);
                    System.out.println("Join phase complete. Starting simulation.");
                }
                return;
            }

            if (round >= totalRounds) {
                finishSimulation();
                finished = true;
                return;
            }

            if (rescheduleEvery > 0 && round > 0 && (round % rescheduleEvery == 0)) {
                rescheduleWorld();
            }

            AID current = participants.get(idx);
            processTurn(current, round);

            idx++;
            if (idx >= participants.size()) {
                idx = 0;
                round++;
            }
        }

        private void handleJoinRequests() {
            ACLMessage msg = receive(joinTpl);
            if (msg == null) {
                block(250);
                return;
            }

            if (participants.size() >= maxParticipants) {
                ACLMessage refuse = msg.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setConversationId("join-simulation-request");
                refuse.setContent("Simulation full");
                send(refuse);
                return;
            }

            AID requester = msg.getSender();
            int commitment = parseCommitment(msg.getContent());

            // assign start position
            Position start = randomFreeCell(Collections.emptySet());

            participants.add(requester);
            commitments.put(requester, Math.max(1, commitment));
            agentPos.put(requester, start);
            scores.put(requester, 0);

            ACLMessage agree = msg.createReply();
            agree.setPerformative(ACLMessage.AGREE);
            agree.setConversationId("join-simulation-request");

            SimulationState initState = buildPerceivedStateFor(requester, 0, true);
            try {
                agree.setContentObject(initState);
            } catch (IOException e) {
                throw new RuntimeException("SimulationState must be Serializable", e);
            }

            send(agree);

            System.out.printf("AGREE join: %s commitment=%d start=%s%n",
                    requester.getLocalName(), commitments.get(requester), start);
        }

        private int parseCommitment(String content) {
            try { return Integer.parseInt(content.trim()); }
            catch (Exception e) { return 1; }
        }

        private void processTurn(AID agent, int currentRound) {
            // Ask for action
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(agent);
            req.setConversationId("request-action");
            req.setReplyWith("req-" + agent.getLocalName() + "-" + currentRound + "-" + System.nanoTime());
            send(req);

            // Wait for PROPOSE/request-action from same agent up to timeout
            MessageTemplate proposalTpl = MessageTemplate.and(
                    MessageTemplate.MatchConversationId("request-action"),
                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
            );

            ACLMessage proposal = null;
            long deadline = System.currentTimeMillis() + actionTimeoutMs;
            while (System.currentTimeMillis() < deadline) {
                ACLMessage m = receive(proposalTpl);
                if (m != null && m.getSender().equals(agent)) {
                    proposal = m;
                    break;
                }
                block(200);
            }

            // If no proposal: expire turn (no movement), still send update-state
            if (proposal == null) {
                SimulationState st = buildPerceivedStateFor(agent, currentRound, false);
                sendUpdateState(agent, st);
                return;
            }

            // Read requested Position
            Position requested = null;
            try {
                Serializable obj = proposal.getContentObject();
                requested = (Position) obj;
            } catch (Exception ignored) {
                // if not sent correctly -> treat as "stay"
            }

            if (requested == null) requested = agentPos.get(agent);

            // Apply referee rules
            applyMovement(agent, requested);

            // Send update
            SimulationState st = buildPerceivedStateFor(agent, currentRound, false);
            sendUpdateState(agent, st);
        }

        private void applyMovement(AID agent, Position requested) {
            Position current = agentPos.get(agent);

            // Only stay or adjacent
            if (!isStayOrAdjacent(current, requested)) return;

            // Must be within bounds
            if (!inBounds(requested)) return;

            // Occupied by another agent -> reject
            if (occupiedByOther(agent, requested)) return;

            // Trap -> reject + -1
            if (traps.contains(requested)) {
                scores.put(agent, scores.get(agent) - 1);
                return;
            }

            // Move allowed
            agentPos.put(agent, requested);

            // Item -> +1 and respawn elsewhere
            if (items.contains(requested)) {
                scores.put(agent, scores.get(agent) + 1);
                items.remove(requested);
                items.add(randomFreeCell(traps));
            }
        }

        private void sendUpdateState(AID agent, SimulationState st) {
            ACLMessage inf = new ACLMessage(ACLMessage.INFORM);
            inf.addReceiver(agent);
            inf.setConversationId("update-state");
            try {
                inf.setContentObject(st);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            send(inf);
        }

        private void finishSimulation() {
            for (AID p : participants) {
                ACLMessage end = new ACLMessage(ACLMessage.INFORM);
                end.addReceiver(p);
                end.setConversationId("simulation-complete");
                end.setContent("Simulation complete");
                send(end);
            }

            System.out.println("\n=== FINAL SCORES ===");
            participants.stream()
                    .sorted((a, b) -> Integer.compare(scores.getOrDefault(b, 0), scores.getOrDefault(a, 0)))
                    .forEach(a -> System.out.printf("%s -> %d%n", a.getLocalName(), scores.getOrDefault(a, 0)));
        }

        @Override
        public boolean done() {
            return finished;
        }
    }

    // -------------------- Commitment-based perception --------------------
    private SimulationState buildPerceivedStateFor(AID agent, int round, boolean forceSync) {
        int c = commitments.getOrDefault(agent, 1);

        boolean sync = forceSync;
        if (!sync) {
            int last = lastSyncRound.getOrDefault(agent, 0);
            if (round - last >= c) sync = true;
        }

        SimulationState st = new SimulationState(width, height);
        st.self = agentPos.get(agent);
        st.score = scores.getOrDefault(agent, 0);
        st.round = round;

        if (sync) {
            st.items = new HashSet<>(items);
            st.traps = new HashSet<>(traps);
            lastSyncRound.put(agent, round);
        } else {
            // No update this round: the participant should keep its previous belief.
            st.items = null;
            st.traps = null;
        }

        return st;
    }

    // -------------------- Helpers --------------------
    private boolean inBounds(Position p) {
        return p.x >= 0 && p.x < width && p.y >= 0 && p.y < height;
    }

    private boolean occupiedByOther(AID me, Position p) {
        for (Map.Entry<AID, Position> e : agentPos.entrySet()) {
            if (!e.getKey().equals(me) && e.getValue().equals(p)) return true;
        }
        return false;
    }

    private boolean isStayOrAdjacent(Position a, Position b) {
        if (a.equals(b)) return true;
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return (dx + dy) == 1;
    }
}