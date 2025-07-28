package it.unitn.ds;

public final class Config {
    // Parametri di replica 
    public static final int N = 3;
    public static final int W = calculateW();
    public static final int R = calculateR();
    public static final int TIMEOUT_MS = 1500;

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