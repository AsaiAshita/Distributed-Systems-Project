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
    private final Random random = new Random();

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
            String request_id = msg.key + "id" + this.id + "c" + Config.VECTOR_CLOCK.get(getSelf());
            Config.VECTOR_CLOCK.put(getSelf(), Config.VECTOR_CLOCK.get(getSelf())+1);
            Config.FIFO.get(getSelf()).add(request_id);
            //System.out.println(Config.VECTOR_CLOCK);
            //System.out.println(Config.FIFO);
            int delayMs = 100 + random.nextInt(Config.QUICK_COMMUNICATION);
            getContext().getSystem().scheduler().scheduleOnce(
                    scala.concurrent.duration.Duration.create(delayMs, "milliseconds"),
                    () -> coordinator.tell(new GetMsg(msg.key, request_id), getSelf()),
                    getContext().getDispatcher()
            );
        } else {
            System.out.println("No coordinator available for GetMsg.");
        }
    }

    // Forward an UpdateMsg to the coordinator
    private void forwardUpdateMsg(UpdateMsg msg) {
        ActorRef coordinator = getCoordinator();
        if (coordinator != null) {
            String request_id = msg.key + "id" + this.id + "c" + Config.VECTOR_CLOCK.get(getSelf());
            Config.VECTOR_CLOCK.put(getSelf(), Config.VECTOR_CLOCK.get(getSelf())+1);
            Config.FIFO.get(getSelf()).add(request_id);
            //System.out.println(Config.VECTOR_CLOCK);
            //System.out.println(Config.FIFO);
            int delayMs = 100 + random.nextInt(Config.QUICK_COMMUNICATION);
            getContext().getSystem().scheduler().scheduleOnce(
                    scala.concurrent.duration.Duration.create(delayMs, "milliseconds"),
                    () -> coordinator.tell(new UpdateMsg(msg.key, msg.value, request_id), getSelf()),
                    getContext().getDispatcher()
            );
        } else {
            System.out.println("No coordinator available for UpdateMsg.");
        }
    }

    // Print the received value to the console
    private void receiveMsg(SendMsg msg) {
        System.out.println(getSelf() + " received " + msg.value);
    }

    // Update the view
    private void updateView(UpdateClientView msg) {
        if (msg.isLeaving) {
            this.currentView.remove(msg.node);
            //System.out.println("Did it for node" + msg.node + " and for client " + getSelf());
        } else {
            this.currentView.add(msg.node);
            //System.out.println("Did it for node" + msg.node + " and for client " + getSelf());
        }
    }


    // Message for requesting a value
    public static class GetMsg implements Serializable {
        public final int key;
        public String request_id;
        public GetMsg(int key) {
            this.key = key;
        }
        public GetMsg(int key, String request_id) {
            this.key = key;
            this.request_id = request_id;
        }

    }

    // Message for updating a value
    public static class UpdateMsg implements Serializable {
        public final int key;
        public final String value;
        public String request_id;
        public UpdateMsg(int key, String value) {
            this.key = key;
            this.value = value;
        }
        public UpdateMsg(int key, String value, String request_id){
            this.key = key;
            this.value = value;
            this.request_id = request_id;
        }
    }

    // Message for updating the view
    public static class UpdateClientView implements Serializable {
        public final ActorRef node;
        public final boolean isLeaving;
        public UpdateClientView(ActorRef node, boolean isLeaving) {
            this.node = node;
            this.isLeaving = isLeaving;
        }
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetMsg.class, this::forwardGetMsg)
                .match(UpdateMsg.class, this::forwardUpdateMsg)
                .match(SendMsg.class, this::receiveMsg)
                .match(UpdateClientView.class, this::updateView)
                .build();
    }

    public static Props props(int id, ArrayList<ActorRef> nodes) {
        return Props.create(Client.class, () -> new Client(id, nodes));
    }
}
