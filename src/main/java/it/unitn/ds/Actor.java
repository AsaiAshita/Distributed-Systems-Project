package it.unitn.ds;

import akka.actor.*;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import it.unitn.ds.Client.GetMsg;
import it.unitn.ds.Client.UpdateMsg;

public class Actor extends AbstractActor {

    // Node identifier
    private int id;

    // Read quorum
    private static final int R = 2;

    // Total number of nodes in the system
    private static final int N = 3; 

    // List of other nodes in the system (excluding self)
    private final ArrayList<ActorRef> currentView;

    // Local key-value store: key -> (version, value)
    private final Map<Integer, Pair<Integer,String>> values;

    // Keeps track of the responses received for each read request
    private Map<Integer, ArrayList<Pair<Integer,String>>> pendingReads = new HashMap<>();

    // Maps each read key to the client actor that requested it
    private Map<Integer, ActorRef> pendingClients = new HashMap<>();

    // Used for introducing randomized delays in responses
    private final Random random = new Random();

    // Timeout duration for quorum wait
    private static final int TIMEOUT_MS = 2500;

    // Keeps track of which keys currently have a pending read operation
    private Set<Integer> pendingReadOperations;

    public Actor(int id) {
        this.id = id;
        this.currentView = new ArrayList<>();
        this.values = new HashMap<>();
        this.pendingReadOperations = new HashSet<>();
    }

    public static Props props(int id) {
        return Props.create(Actor.class, () -> new Actor(id));
    }

     // Handles a Get request from a client. If no read is pending for the key, initiate a quorum read.
    private void getValue(GetMsg getMsg){
        if (pendingReadOperations.contains(getMsg.key)) {
            return;
        }

        pendingReadOperations.add(getMsg.key);
        pendingReads.put(getMsg.key, new ArrayList<>());
        pendingClients.put(getMsg.key, getSender());

        // Send InternalGetMsg to all nodes in the view
        for (int i = 0; i < currentView.size(); i++) {
            currentView.get(i).tell(new Actor.InternalGetMsg(getMsg.key), getSelf());
        }

        // Send to the coordinator as well
        getSelf().tell(new Actor.InternalGetMsg(getMsg.key), getSelf());

        // Schedule a timeout in case not enough responses arrive in time
        getContext().getSystem().scheduler().scheduleOnce(
            scala.concurrent.duration.Duration.create(TIMEOUT_MS, "milliseconds"),
            getSelf(),
            new Timeout(getMsg.key),
            getContext().getDispatcher(),
            ActorRef.noSender()
        );
    }

    // Handles an InternalGetMsg by replying with the local value (if exists) after a random delay.
    private void handleInternalGet(InternalGetMsg msg) {
        if (values.containsKey(msg.key)) {
            Pair<Integer, String> pair = values.get(msg.key);
            int delayMs = 100 + random.nextInt(2901); // Delay between 100ms and 3000ms

            ActorRef originalSender = getSender();

            getContext().getSystem().scheduler().scheduleOnce(
                scala.concurrent.duration.Duration.create(delayMs, "milliseconds"),
                () -> originalSender.tell(new Actor.ReceiveMsg(msg.key, pair.getLeft(), pair.getRight()), getSelf()),
                getContext().getDispatcher()
            );
        }
    }

    // Handles ReceiveMsg replies from nodes. If enough responses are received, selects the one with the highest version and returns it to the client.
    private void receiveResponses(ReceiveMsg msg) {
        pendingReads.get(msg.key).add(Pair.of(msg.version, msg.value));

        if (pendingReads.get(msg.key).size() >= R && pendingReadOperations.contains(msg.key)) {
            Pair<Integer, String> best = pendingReads.get(msg.key).stream()
                .max(Comparator.comparingInt(Pair::getLeft)) // choose highest version
                .orElse(null);

            if (best != null) {
                pendingClients.get(msg.key).tell(new SendMsg(best.getRight()), getSelf());
                pendingReadOperations.remove(msg.key);
            }
        }
    }

    private void updateValue(UpdateMsg updateMsg){ 
        // To be implemented
    }

    // Called when timeout occurs for a pending read. If quorum was not reached, responds to the client with null.
    private void onTimeout(Timeout timeout) {
        if (pendingReads.get(timeout.key).size() < R && pendingReadOperations.contains(timeout.key)) {
            pendingClients.get(timeout.key).tell(new SendMsg(null), getSelf());
            pendingReads.get(timeout.key).clear();
        }
    }

    
    // Updates the view of known nodes (excluding itself)
    private void updateView(UpdateView msg) {
        for (ActorRef node : msg.nodes) {
            if (!node.equals(getSelf())) { 
                currentView.add(node);
            }
        }
    }

    // Sets the local key-value store with initial values.
    private void setValues(SetValues msg) {
        this.values.putAll(msg.values);
    }

    // ---- Message classes below ----

    public static class Timeout implements Serializable {
        public final int key;
        public Timeout(int key) {
            this.key = key;
        }
    }

    public static class UpdateView implements Serializable {
        public final ArrayList<ActorRef> nodes;
        public UpdateView(ArrayList<ActorRef> nodes) {
            this.nodes = nodes;
        }
    }

    public static class SetValues implements Serializable {
        public final Map<Integer,Pair<Integer,String>> values;
        public SetValues(Map<Integer,Pair<Integer,String>> values) {
            this.values = values;
        }
    }

    public static class SendMsg implements Serializable {
        public String value = new String();
        public SendMsg(String value) {
            this.value = value;
        }
    }

    public static class InternalGetMsg implements Serializable {
        public final int key;
        public InternalGetMsg(int key) {
            this.key = key;
        }
    }

    public static class InternalUpdateMsg implements Serializable {
        public final int key;
        public final String value;
        public InternalUpdateMsg(int key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class ReceiveMsg implements Serializable {
        public int key;
        public int version;
        public String value;
        public ReceiveMsg(int key, int version, String value) {
            this.key = key;
            this.version = version;
            this.value = value;
        }
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetMsg.class, this::getValue)
                .match(UpdateMsg.class, this::updateValue)
                .match(UpdateView.class, this::updateView)
                .match(SetValues.class, this::setValues)
                .match(InternalGetMsg.class, this::handleInternalGet)
                .match(ReceiveMsg.class, this::receiveResponses)
                .match(Timeout.class, this::onTimeout)
                .build();
    }
}
