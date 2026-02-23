package itemworld;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ParticipantAgent extends Agent {

    // Debe coincidir con SimulationAgent
    private static final String SIM_SERVICE_TYPE = "SimulatorService";

    private int commitment = 1;
    private AID simulatorAID = null;

    // Percepción del mundo (beliefs)
    private SimulationState belief = null;

    // Estado interno
    private boolean joined = false;
    private boolean finished = false;

    @Override
    protected void setup() {
        readArgs(getArguments());

        // 1) Behaviour: escucha permanente del fin de simulación
        addBehaviour(new ListenSimulationCompleteBehaviour());

        // 2) Behaviour: descubrir simulador + hacer join
        addBehaviour(new DiscoverAndJoinBehaviour());
    }

    private void readArgs(Object[] args) {
        // Esperamos: commitment como primer argumento
        if (args != null && args.length > 0) {
            try {
                commitment = Integer.parseInt(args[0].toString().trim());
            } catch (Exception ignored) {
                commitment = 1;
            }
        }
        if (commitment < 1) commitment = 1;
        System.out.println(getLocalName() + " commitment=" + commitment);
    }

    // ===================== 1) Descubrir simulador y hacer join =====================
    private class DiscoverAndJoinBehaviour extends Behaviour {
        private boolean done = false;

        private long nextSearchTime = 0;

        @Override
        public void action() {
            if (finished) { done = true; return; }

            if (!joined) {
                // buscar en DF cada 1 segundo
                if (System.currentTimeMillis() >= nextSearchTime) {
                    simulatorAID = searchSimulatorInDF();
                    nextSearchTime = System.currentTimeMillis() + 1000;
                }

                if (simulatorAID == null) {
                    block(200);
                    return;
                }

                // Enviar join request y esperar respuesta
                boolean ok = doJoin(simulatorAID);
                if (ok) {
                    joined = true;
                    // 3) Behaviour: bucle de turnos (solo cuando ya estás dentro)
                    addBehaviour(new TurnBehaviour());
                    done = true;
                } else {
                    // si REFUSE, terminamos (o podrías reintentar)
                    finished = true;
                    doDelete();
                    done = true;
                }
                return;
            }

            done = true;
        }

        @Override
        public boolean done() {
            return done;
        }

        private AID searchSimulatorInDF() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(SIM_SERVICE_TYPE);
            template.addServices(sd);

            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result != null && result.length > 0) {
                    AID found = result[0].getName();
                    // System.out.println(getLocalName() + " found simulator: " + found.getName());
                    return found;
                }
            } catch (FIPAException e) {
                // DF no disponible temporalmente
            }
            return null;
        }

        private boolean doJoin(AID sim) {
            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(sim);
            req.setConversationId("join-simulation-request");
            req.setContent(Integer.toString(commitment));
            req.setReplyWith("join-" + getLocalName() + "-" + System.nanoTime());
            send(req);

            // Esperar AGREE o REFUSE
            MessageTemplate tpl = MessageTemplate.and(
                    MessageTemplate.MatchConversationId("join-simulation-request"),
                    MessageTemplate.or(
                            MessageTemplate.MatchPerformative(ACLMessage.AGREE),
                            MessageTemplate.MatchPerformative(ACLMessage.REFUSE)
                    )
            );

            ACLMessage reply = blockingReceive(tpl, 8000); // 8s de margen
            if (reply == null) {
                System.out.println(getLocalName() + " join timeout.");
                return false;
            }

            if (reply.getPerformative() == ACLMessage.REFUSE) {
                System.out.println(getLocalName() + " join REFUSED: " + reply.getContent());
                return false;
            }

            // AGREE: recibir SimulationState inicial
            try {
                Serializable obj = reply.getContentObject();
                belief = (SimulationState) obj;
                System.out.println(getLocalName() + " joined. Start at " + belief.self + " score=" + belief.score);
            } catch (Exception e) {
                System.out.println(getLocalName() + " joined but could not read SimulationState.");
                belief = null;
            }

            return true;
        }
    }

    // ===================== 2) Bucle de turnos =====================
    private class TurnBehaviour extends CyclicBehaviour {

        private final MessageTemplate requestActionTpl = MessageTemplate.and(
                MessageTemplate.MatchConversationId("request-action"),
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
        );

        private final MessageTemplate updateStateTpl = MessageTemplate.and(
                MessageTemplate.MatchConversationId("update-state"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM)
        );

        @Override
        public void action() {
            if (finished) { myAgent.doDelete(); return; }

            // Espera su turno
            ACLMessage turnReq = receive(requestActionTpl);
            if (turnReq == null) {
                block(200);
                return;
            }

            // Deliberar: elegir siguiente Position según belief
            Position next = decideNextMove();

            // Responder con PROPOSE + Position
            ACLMessage propose = turnReq.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setConversationId("request-action");
            try {
                propose.setContentObject(next);
            } catch (Exception e) {
                // Si algo raro pasa, quedarse quieto
                try { propose.setContentObject(belief != null ? belief.self : next); } catch (Exception ignored) {}
            }
            send(propose);

            // Esperar update-state (INFORM)
            ACLMessage update = blockingReceive(updateStateTpl, 10_000);
            if (update != null) {
                try {
                    Serializable obj = update.getContentObject();
                    SimulationState newSt = (SimulationState) obj;
                    mergeBelief(newSt);
                } catch (Exception ignored) {
                }
            }
        }

        private void mergeBelief(SimulationState incoming) {
            if (incoming == null) return;
            if (belief == null) {
                belief = incoming;
                return;
            }

            // Siempre actualiza self/score/round
            belief.self = incoming.self;
            belief.score = incoming.score;
            belief.round = incoming.round;

            // Si incoming.items/traps son null => no hay actualización por commitment, conserva antiguos
            if (incoming.items != null) belief.items = incoming.items;
            if (incoming.traps != null) belief.traps = incoming.traps;

            // (Opcional) imprimir cada X rondas
            // System.out.println(getLocalName() + " round=" + belief.round + " score=" + belief.score + " pos=" + belief.self);
        }

        private Position decideNextMove() {
            // Si no sabemos nada, "stay"
            if (belief == null || belief.self == null) {
                return new Position(0, 0);
            }

            Position cur = belief.self;

            List<Position> candidates = new ArrayList<>();
            candidates.add(cur); // stay

            candidates.add(new Position(cur.x + 1, cur.y));
            candidates.add(new Position(cur.x - 1, cur.y));
            candidates.add(new Position(cur.x, cur.y + 1));
            candidates.add(new Position(cur.x, cur.y - 1));

            // Filtrar fuera de mapa si conocemos width/height
            candidates.removeIf(p -> p.x < 0 || p.x >= belief.width || p.y < 0 || p.y >= belief.height);

            // Evitar traps si las conocemos en la percepción
            if (belief.traps != null) {
                candidates.removeIf(p -> belief.traps.contains(p));
                // Si quitamos todo, al menos quedarse
                if (candidates.isEmpty()) candidates.add(cur);
            }

            // Estrategia baseline: elegir random
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
    }

    // ===================== 3) Escucha permanente fin de simulación =====================
    private class ListenSimulationCompleteBehaviour extends CyclicBehaviour {
        private final MessageTemplate endTpl = MessageTemplate.and(
                MessageTemplate.MatchConversationId("simulation-complete"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM)
        );

        @Override
        public void action() {
            ACLMessage m = receive(endTpl);
            if (m == null) {
                block(500);
                return;
            }
            finished = true;
            System.out.println(getLocalName() + " received simulation-complete. Final score=" +
                    (belief != null ? belief.score : "?"));
            myAgent.doDelete();
        }
    }
}