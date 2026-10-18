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
        check(!timer.isPreBreak(), "No sepia before the warning");
        timer.tick(100 * MINUTE, true, 60);
        check(timer.tick(149 * MINUTE, true, 60) == BreakTimer.Notice.NONE, "No early warning");
        check(timer.tick(150 * MINUTE, true, 60) == BreakTimer.Notice.WARNING, "Warning at 50 minutes");
        check(timer.isPreBreak(), "Warning enables sepia");
        check(timer.tick(150 * MINUTE, true, 60) == BreakTimer.Notice.NONE, "Warn only once");
        // A dimension/proxy loading gap suspends counting without resetting the session.
        timer.tick(150 * MINUTE, false, 60);
        timer.tick(170 * MINUTE, false, 60);
        timer.tick(170 * MINUTE, true, 60);
        check(timer.remainingSeconds(60) == 600, "Loading time excluded; progress preserved");
        check(timer.tick(180 * MINUTE, true, 60) == BreakTimer.Notice.BREAK, "Break at 60 active minutes");
        check(!timer.isPreBreak(), "Break replaces sepia with grayscale");
        var challenge = timer.challenge();
        timer.tick(181 * MINUTE, false, 60);
        timer.tick(190 * MINUTE, true, 60);
        check(timer.challenge() == challenge, "Warp must preserve pending challenge");
        check(!timer.answer(challenge.answer() + 1), "Reject wrong answer");
        check(timer.isBreakDue(), "Wrong answer retains grayscale");
        check(timer.answer(challenge.answer()), "Accept correct answer");
        check(!timer.isBreakDue() && timer.isPreBreak() && timer.remainingSeconds(60) == 600,
                "Correct answer returns to sepia with ten minutes remaining");
        check(timer.remainingSeconds(240) == 600, "Settings cannot extend a postponed break");
        check(timer.tick(190 * MINUTE, true, 60) == BreakTimer.Notice.NONE,
                "Answer does not count time spent in the break or repeat the warning");
        check(timer.tick(199 * MINUTE, true, 60) == BreakTimer.Notice.NONE, "No early postponed break");
        check(timer.tick(200 * MINUTE, true, 60) == BreakTimer.Notice.BREAK, "Break returns after ten minutes");
        check(timer.answer(timer.challenge().answer()), "Can postpone another break");
        check(timer.isPreBreak() && timer.remainingSeconds(60) == 600, "Repeated answers never fully reset");
        check(timer.addMinutes(10, 60) == BreakTimer.Notice.BREAK, "Added time uses postponed deadline");
        timer.reset();
        check(!timer.isPreBreak(), "Logout removes sepia");
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
        BreakAlarm alarm = new BreakAlarm();
        check(alarm.poll(0) == 0, "Alarm is initially silent");
        alarm.start(0);
        check(alarm.poll(0) == 1, "First bell is immediate");
        check(alarm.poll(599_999_999L) == 0, "No early bell");
        for (int i = 1; i < 5; i++) {
            check(alarm.poll(i * 600_000_000L) == 1, "Bell at each 600ms interval");
        }
        check(alarm.poll(3_000_000_000L) == 0, "Exactly five bells within three seconds");
        alarm.start(0);
        check(alarm.poll(2_400_000_000L) == 5, "Delayed ticks catch up due strikes");
        alarm.start(0);
        alarm.reset();
        check(alarm.poll(3_000_000_000L) == 0, "Logout cancels pending bells");
        Random random = new Random(42);
        var seen = java.util.EnumSet.noneOf(BreakTimer.Challenge.Operation.class);
        for (int i = 0; i < 10_000; i++) {
            var quiz = BreakTimer.Challenge.create(random);
            seen.add(quiz.operation());
            check(quiz.a() >= 0 && quiz.a() < 100 && quiz.b() >= 0 && quiz.b() < 100,
                    "Operands stay below 100");
            check(quiz.answer() >= 0 && quiz.answer() < 100, "Result stays between 0 and 99");
            switch (quiz.operation()) {
                case ADD -> {
                    check(quiz.answer() == quiz.a() + quiz.b(), "Addition answer");
                    check(quiz.expression().equals(quiz.a() + " + " + quiz.b()), "Addition expression");
                }
                case SUBTRACT -> {
                    check(quiz.answer() == quiz.a() - quiz.b(), "Subtraction answer");
                    check(quiz.expression().equals(quiz.a() + " − " + quiz.b()), "Subtraction expression");
                }
                case MULTIPLY -> {
                    check(quiz.a() <= 9 && quiz.b() <= 9, "Multiplication uses small times tables");
                    check(quiz.answer() == quiz.a() * quiz.b(), "Multiplication answer");
                    check(quiz.expression().equals(quiz.a() + " × " + quiz.b()), "Multiplication expression");
                }
            }
        }
        check(seen.size() == 3, "All three operations are generated");
        System.out.println("Timer checks passed: menus, thresholds, warp gaps, answers, logout, 30–240 min, quiz.");
    }
}
