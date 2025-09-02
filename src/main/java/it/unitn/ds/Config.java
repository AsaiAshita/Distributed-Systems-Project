package it.unitn.ds;

import akka.actor.ActorRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class Config {
    // Parametri di replica 
    public static final int N = 3;
    public static final int W = calculateW();
    public static final int R = calculateR();
    public static final int TIMEOUT_MS = 1500;
    public static final int TIMEOUT_JOIN = 3000;

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