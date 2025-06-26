package it.unitn.ds;

import akka.actor.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Client extends AbstractActor {

    private int id;
    private final Set<ActorRef> currentView;

    public Client(int id) {
        this.id = id;
        this.currentView = new HashSet<>();
    }

    public static class GetMsg implements Serializable {
        public final int key;
        public GetMsg(int key) {
            this.key = key;
        }
    }

    public static class UpdateMsg implements Serializable {
        public final int key;
        public final String value;
        public UpdateMsg(int key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static Props props(ActorRef coordinator) {
        return Props.create(Client.class, () -> new Client(2));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .build();
    }
}