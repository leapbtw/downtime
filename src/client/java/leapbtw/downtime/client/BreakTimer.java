package leapbtw.downtime.client;

import java.util.random.RandomGenerator;

/** Session state deliberately has no dependency on a world, dimension or player instance. */
public final class BreakTimer {
    public enum Notice { NONE, WARNING, BREAK }
    public static final int BREAK_MINIMUM_SECONDS = 120;
    private static final long MINUTE = 60_000_000_000L;
    private long elapsed;
    private long lastSample;
    private boolean sampled;
    private boolean wasPlaying;
    private boolean warned;
    private boolean postponed;
    private Challenge challenge;
    private long breakStartedAtMillis = -1;
    private long leftAtMillis = -1;

    public Notice tick(long nowNanos, long nowMillis, boolean playing, int minutes) {
        if (sampled && wasPlaying && playing && challenge == null) {
            elapsed += Math.max(0, nowNanos - lastSample);
        }
        lastSample = nowNanos;
        sampled = true;
        wasPlaying = playing;
        if (!playing || challenge != null) return Notice.NONE;
        return checkThresholds(minutes, nowMillis);
    }

    public Notice addMinutes(int addedMinutes, int minutes, long nowMillis) {
        if (addedMinutes < 0) throw new IllegalArgumentException("Minutes number must be positive");
        if (challenge != null) return Notice.NONE;
        // The clock only needs to reach the current deadline, even for very large input.
        int deadline = postponed ? 10 : minutes;
        elapsed = Math.min(deadline * MINUTE, elapsed + Math.min(addedMinutes, deadline) * MINUTE);
        return checkThresholds(minutes, nowMillis);
    }

    private Notice checkThresholds(int minutes, long nowMillis) {
        int deadline = postponed ? 10 : minutes;
        if (elapsed >= deadline * MINUTE) {
            challenge = Challenge.create(RandomGenerator.getDefault());
            breakStartedAtMillis = nowMillis;
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
        clearBreak();
    }

    public boolean leaveWorld(long nowMillis) {
        if (!isBreakDue()) {
            reset();
            return false;
        }
        if (leftAtMillis >= 0) return false;
        leftAtMillis = nowMillis;
        return true;
    }

    public boolean returnToWorld(long nowMillis) {
        if (leftAtMillis < 0) return false;
        if (breakAwaySeconds(nowMillis) >= BREAK_MINIMUM_SECONDS) reset();
        else leftAtMillis = -1;
        return true;
    }

    public void restoreBreak(Challenge challenge, long startedAtMillis, long leftAtMillis) {
        reset();
        this.challenge = challenge;
        this.breakStartedAtMillis = startedAtMillis;
        this.leftAtMillis = leftAtMillis;
    }

    private void clearBreak() {
        challenge = null;
        breakStartedAtMillis = -1;
        leftAtMillis = -1;
    }

    public boolean answer(int answer, long nowMillis) {
        if (!isBreakDue() || breakElapsedSeconds(nowMillis) < BREAK_MINIMUM_SECONDS
                || challenge.answer() != answer) return false;
        // Solving the challenge only grants ten more active minutes in sepia.
        elapsed = 0;
        sampled = false;
        wasPlaying = false;
        warned = true;
        postponed = true;
        clearBreak();
        return true;
    }

    public boolean isBreakDue() { return challenge != null; }
    public boolean isPreBreak() { return warned && challenge == null; }
    public Challenge challenge() { return challenge; }
    public long breakStartedAtMillis() { return breakStartedAtMillis; }
    public long leftAtMillis() { return leftAtMillis; }

    public long breakElapsedSeconds(long nowMillis) {
        return secondsSince(breakStartedAtMillis, nowMillis);
    }

    public long breakAwaySeconds(long nowMillis) {
        return secondsSince(leftAtMillis, nowMillis);
    }

    private static long secondsSince(long startMillis, long nowMillis) {
        return startMillis < 0 ? -1 : Math.max(0, nowMillis - startMillis) / 1000;
    }

    public long remainingSeconds(int minutes) {
        if (isBreakDue()) return 0;
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
