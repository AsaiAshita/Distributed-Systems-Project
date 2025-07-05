package it.unitn.ds;

import akka.actor.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import it.unitn.ds.Actor.SendMsg;

public class Client extends AbstractActor {

    public static final Object GetMsg = null;
    private int id;
    private final ArrayList<ActorRef> currentView;

    public Client(int id, ArrayList<ActorRef> nodes) {
        this.id = id;
        this.currentView = new ArrayList<>(nodes);
    }

    // Select a random coordinator from the list of nodes
    private ActorRef getCoordinator () {
        if (currentView.isEmpty()) return null;
        Random random = new Random();
        return currentView.get(random.nextInt(currentView.size()));
    }

    // Forward a GetMsg to the coordinator
    private void forwardGetMsg(GetMsg msg) {
        ActorRef coordinator = getCoordinator();
        if (coordinator != null) {
            coordinator.tell(msg, getSelf());
        } else {
            System.out.println("No coordinator available for GetMsg.");
        }
    }

    // Forward an UpdateMsg to the coordinator
    private void forwardUpdateMsg(UpdateMsg msg) {
        ActorRef coordinator = getCoordinator();
        if (coordinator != null) {
            coordinator.tell(msg, getSelf());
        } else {
            System.out.println("No coordinator available for UpdateMsg.");
        }
    }

    // Print the received value to the console
    private void receiveMsg(SendMsg msg) {
        System.out.println(msg.value);
    }

    // Message for requesting a value
    public static class GetMsg implements Serializable {
        public final int key;
        public GetMsg(int key) {
            this.key = key;
        }
    }

    // Message for updating a value
    public static class UpdateMsg implements Serializable {
        public final int key;
        public final String value;
        public UpdateMsg(int key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetMsg.class, this::forwardGetMsg)
                .match(UpdateMsg.class, this::forwardUpdateMsg)
                .match(SendMsg.class, this::receiveMsg)
                .build();
    }

    public static Props props(int id, ArrayList<ActorRef> nodes) {
        return Props.create(Client.class, () -> new Client(id, nodes));
    }
}
