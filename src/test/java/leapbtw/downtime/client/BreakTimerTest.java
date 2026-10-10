package leapbtw.downtime.client;

import java.util.Random;

public final class BreakTimerTest {
    private static final long MINUTE = 60_000_000_000L;
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        BreakTimer timer = new BreakTimer();
        timer.tick(0, false, 60);
        timer.tick(100 * MINUTE, false, 60);
        check(timer.remainingSeconds(60) == 3600, "Main menu must not count");
        timer.tick(100 * MINUTE, true, 60);
        check(timer.tick(149 * MINUTE, true, 60) == BreakTimer.Notice.NONE, "No early warning");
        check(timer.tick(150 * MINUTE, true, 60) == BreakTimer.Notice.WARNING, "Warning at 50 minutes");
        check(timer.tick(150 * MINUTE, true, 60) == BreakTimer.Notice.NONE, "Warn only once");
        // A dimension/proxy loading gap suspends counting without resetting the session.
        timer.tick(150 * MINUTE, false, 60);
        timer.tick(170 * MINUTE, false, 60);
        timer.tick(170 * MINUTE, true, 60);
        check(timer.remainingSeconds(60) == 600, "Loading time excluded; progress preserved");
        check(timer.tick(180 * MINUTE, true, 60) == BreakTimer.Notice.BREAK, "Break at 60 active minutes");
        var challenge = timer.challenge();
        timer.tick(181 * MINUTE, false, 60);
        timer.tick(190 * MINUTE, true, 60);
        check(timer.challenge() == challenge, "Warp must preserve pending challenge");
        check(!timer.answer(challenge.answer() + 1), "Reject wrong answer");
        check(timer.isBreakDue(), "Wrong answer retains grayscale");
        check(timer.answer(challenge.answer()), "Accept correct answer");
        check(!timer.isBreakDue() && timer.remainingSeconds(60) == 3600, "Correct answer restarts cycle");
        timer.tick(200 * MINUTE, true, 30);
        check(timer.tick(220 * MINUTE, true, 30) == BreakTimer.Notice.WARNING, "30 minute setting warning");
        timer.tick(230 * MINUTE, true, 30);
        timer.reset();
        check(!timer.isBreakDue() && timer.remainingSeconds(30) == 1800, "Logout clears challenge and clock");
        timer.tick(0, true, 240);
        check(timer.tick(230 * MINUTE, true, 240) == BreakTimer.Notice.WARNING, "4 hour warning");
        check(timer.tick(240 * MINUTE, true, 240) == BreakTimer.Notice.BREAK, "4 hour break");
        timer.reset();
        check(timer.addMinutes(49, 60) == BreakTimer.Notice.NONE, "Added time before warning");
        check(timer.remainingSeconds(60) == 660, "Added minutes advance internal counter");
        check(timer.addMinutes(1, 60) == BreakTimer.Notice.WARNING, "Added time triggers warning immediately");
        check(timer.tick(0, true, 60) == BreakTimer.Notice.NONE, "Added time warning is not repeated");
        check(timer.addMinutes(10, 60) == BreakTimer.Notice.BREAK, "Added time triggers break immediately");
        var addedChallenge = timer.challenge();
        check(timer.addMinutes(100, 60) == BreakTimer.Notice.NONE && timer.challenge() == addedChallenge,
                "Added time preserves an active challenge");
        timer.reset();
        check(timer.addMinutes(Integer.MAX_VALUE, 60) == BreakTimer.Notice.BREAK,
                "Large additions cannot overflow and trigger break directly");
        timer.reset();
        check(timer.remainingSeconds(60) == 3600, "Reset clears artificially added time");
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            var quiz = BreakTimer.Challenge.create(random);
            for (int value : new int[]{quiz.a(), quiz.b(), quiz.c()})
                check(value >= 100 && value <= 999, "All operands have three digits");
            check(quiz.answer() == quiz.a() * quiz.b() - quiz.c(), "Multiplication precedence");
        }
        System.out.println("Timer checks passed: menus, thresholds, warp gaps, answers, logout, 30–240 min, quiz.");
    }
}
