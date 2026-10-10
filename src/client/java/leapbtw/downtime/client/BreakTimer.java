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
        elapsed = Math.min(minutes * MINUTE, elapsed + Math.min(addedMinutes, minutes) * MINUTE);
        return checkThresholds(minutes);
    }

    private Notice checkThresholds(int minutes) {
        if (elapsed >= minutes * MINUTE) {
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
        challenge = null;
    }

    public boolean answer(int answer) {
        if (challenge == null || challenge.answer() != answer) return false;
        reset();
        return true;
    }

    public boolean isBreakDue() { return challenge != null; }
    public Challenge challenge() { return challenge; }
    public long remainingSeconds(int minutes) {
        return Math.max(0, (minutes * MINUTE - elapsed + 999_999_999L) / 1_000_000_000L);
    }

    public record Challenge(int a, int b, int c) {
        public static Challenge create(RandomGenerator random) {
            return new Challenge(
                    random.nextInt(100, 1000),
                    random.nextInt(100, 1000),
                    random.nextInt(100, 1000));
        }
        public int answer() { return a * b - c; }
        public String expression() { return a + " × " + b + " − " + c; }
    }
}
