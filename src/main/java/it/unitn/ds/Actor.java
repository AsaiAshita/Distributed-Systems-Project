package it.unitn.ds;

import akka.actor.*;

import java.util.*;

import it.unitn.ds.Client.GetMsg;
import it.unitn.ds.Client.UpdateMsg;

public class Actor extends AbstractActor{

    private int id;
    private final Set<ActorRef> currentView;

    public Actor(int id) {
        this.id = id;
        this.currentView = new HashSet<>();
    }

    static public Props props(int id) {
        return Props.create(
                Actor.class,
                () -> new Actor(id));
    }

    private void getValue(GetMsg getMsg){

    }

    private void updateValue(UpdateMsg updateMsg){

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetMsg.class, this::getValue)
                .match(UpdateMsg.class, this::updateValue)
                .build();
    }
}
