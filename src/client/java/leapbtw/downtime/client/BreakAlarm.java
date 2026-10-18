package leapbtw.downtime.client;

/** Five bell strikes at 0, 0.6, 1.2, 1.8 and 2.4 seconds, driven by the client tick. */
public final class BreakAlarm {
    private static final long INTERVAL = 600_000_000L;
    private long nextStrike;
    private int remaining;

    public void start(long now) {
        nextStrike = now;
        remaining = 5;
    }

    public int poll(long now) {
        int due = 0;
        while (remaining > 0 && now - nextStrike >= 0) {
            due++;
            remaining--;
            nextStrike += INTERVAL;
        }
        return due;
    }

    public void reset() {
        remaining = 0;
    }
}
