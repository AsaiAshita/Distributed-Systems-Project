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

    public static final Map<Integer, Integer> MOST_RECENT_VERSION = new HashMap<>();
    public static final Map<ActorRef, ArrayList<String>> FIFO = new HashMap<>();
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