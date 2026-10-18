package leapbtw.downtime.client;

import java.util.random.RandomGenerator;

/** Session state deliberately has no dependency on a world, dimension or player instance. */
public final class BreakTimer {
    public enum Notice { NONE, WARNING, BREAK }
    private static final long MINUTE = 60_000_000_000L;
    private long elapsed;
    private long lastSample;
    private boolean sampled;
    private boolean wasPlaying;
    private boolean warned;
    private boolean postponed;
    private Challenge challenge;

    public Notice tick(long now, boolean playing, int minutes) {
        if (sampled && wasPlaying && playing && challenge == null) {
            elapsed += Math.max(0, now - lastSample);
        }
        lastSample = now;
        sampled = true;
        wasPlaying = playing;
        if (!playing || challenge != null) return Notice.NONE;
        return checkThresholds(minutes);
    }

    public Notice addMinutes(int addedMinutes, int minutes) {
        if (addedMinutes < 0) throw new IllegalArgumentException("Minutes number must be positive");
        if (challenge != null) return Notice.NONE;
        // The clock only needs to reach the current deadline, even for very large input.
        int deadline = postponed ? 10 : minutes;
        elapsed = Math.min(deadline * MINUTE, elapsed + Math.min(addedMinutes, deadline) * MINUTE);
        return checkThresholds(minutes);
    }

    private Notice checkThresholds(int minutes) {
        int deadline = postponed ? 10 : minutes;
        if (elapsed >= deadline * MINUTE) {
            challenge = Challenge.create(RandomGenerator.getDefault());
            return Notice.BREAK;
        }
        if (!warned && elapsed >= (minutes - 10) * MINUTE) {
            warned = true;
            return Notice.WARNING;
        }
        return Notice.NONE;
    }

    public void reset() {
        elapsed = 0;
        sampled = false;
        wasPlaying = false;
        warned = false;
        postponed = false;
        challenge = null;
    }

    public boolean answer(int answer) {
        if (challenge == null || challenge.answer() != answer) return false;
        // Solving the challenge only grants ten more active minutes in sepia.
        elapsed = 0;
        sampled = false;
        wasPlaying = false;
        warned = true;
        postponed = true;
        challenge = null;
        return true;
    }

    public boolean isBreakDue() { return challenge != null; }
    public boolean isPreBreak() { return warned && challenge == null; }
    public Challenge challenge() { return challenge; }
    public long remainingSeconds(int minutes) {
        int deadline = postponed ? 10 : minutes;
        return Math.max(0, (deadline * MINUTE - elapsed + 999_999_999L) / 1_000_000_000L);
    }

    public record Challenge(int a, int b, Operation operation) {
        public enum Operation { ADD, SUBTRACT, MULTIPLY }

        public static Challenge create(RandomGenerator random) {
            Operation operation = Operation.values()[random.nextInt(Operation.values().length)];
            return switch (operation) {
                case ADD -> {
                    int a = random.nextInt(100);
                    yield new Challenge(a, random.nextInt(100 - a), operation);
                }
                case SUBTRACT -> {
                    int a = random.nextInt(100);
                    yield new Challenge(a, random.nextInt(a + 1), operation);
                }
                case MULTIPLY -> new Challenge(random.nextInt(1, 10), random.nextInt(1, 10), operation);
            };
        }
        public int answer() {
            return switch (operation) {
                case ADD -> a + b;
                case SUBTRACT -> a - b;
                case MULTIPLY -> a * b;
            };
        }
        public String expression() {
            String symbol = switch (operation) {
                case ADD -> "+";
                case SUBTRACT -> "−";
                case MULTIPLY -> "×";
            };
            return a + " " + symbol + " " + b;
        }
    }
}
