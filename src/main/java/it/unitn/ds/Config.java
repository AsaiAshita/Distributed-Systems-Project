package it.unitn.ds;

import akka.actor.ActorRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class Config {
    //replication parameter
    public static final int N = 3;
    //write quorum
    public static final int W = calculateW();
    //read quorum
    public static final int R = calculateR();
    //timeouts
    public static final int TIMEOUT_MS = 1500;
    //value used to generate small random delays when no timeout is involved
    public static final int QUICK_COMMUNICATION = 400;
    //in order to not hinder normal operations, timeout_join needs to be higher than TIMEOUT_MS
    //so that it never gets fired, unless a node is indeed crashed (in real life we may not know this,
    //but this is used here as a proof-of-concept)
    public static final int TIMEOUT_JOIN = 6000;

    //used to keep track of the most recent version seen in the execution of the program.
    //Used to resolve conflicts and enforce sequential consistency
    public static final Map<Integer, Integer> MOST_RECENT_VERSION = new HashMap<>();
    //Used to keep track of the operations of every client and of their order.
    //Used to enforce FIFO order.
    public static final Map<ActorRef, ArrayList<String>> FIFO = new HashMap<>();
    //Used to generate unique requests for every client by keeping track of the logical clock
    //of every client
    public static final Map<ActorRef, Integer> VECTOR_CLOCK = new HashMap<>();


    private Config() {} 
    private static int calculateW() {
        int w = N/2 + 1;
        validateQuorum(w, "W");
        return w;
    }

    private static int calculateR() {
        int r = N/2 + 1;
        validateQuorum(r, "R");
        return r;
    }

    private static void validateQuorum(int value, String name) {
        if (value > N) {
            throw new IllegalStateException(name + " cannot exceed N");
        }
    }

    public static void init() {
        System.out.println("[Config] Initialized");
    }
}