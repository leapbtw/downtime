package leapbtw.downtime.client;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Random;

public final class BreakTimerTest {
    private static final long MINUTE = 60_000_000_000L;
    private static final long MINUTE_MILLIS = 60_000L;

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static BreakTimer.Notice tick(BreakTimer timer, int minute, boolean playing, int interval) {
        return timer.tick(minute * MINUTE, minute * MINUTE_MILLIS, playing, interval);
    }

    public static void main(String[] args) {
        BreakTimer timer = new BreakTimer();
        tick(timer, 0, false, 60);
        tick(timer, 100, false, 60);
        check(timer.remainingSeconds(60) == 3600, "Main menu must not count");
        check(!timer.isPreBreak(), "No sepia before the warning");
        tick(timer, 100, true, 60);
        check(tick(timer, 149, true, 60) == BreakTimer.Notice.NONE, "No early warning");
        check(tick(timer, 150, true, 60) == BreakTimer.Notice.WARNING, "Warning at 50 minutes");
        check(timer.isPreBreak(), "Warning enables sepia");
        check(tick(timer, 150, true, 60) == BreakTimer.Notice.NONE, "Warn only once");
        // A dimension/proxy loading gap suspends counting without resetting the session.
        tick(timer, 150, false, 60);
        tick(timer, 170, false, 60);
        tick(timer, 170, true, 60);
        check(timer.remainingSeconds(60) == 600, "Loading time excluded; progress preserved");
        check(tick(timer, 180, true, 60) == BreakTimer.Notice.BREAK, "Break at 60 active minutes");
        check(!timer.isPreBreak(), "Break replaces sepia with grayscale");
        var challenge = timer.challenge();
        tick(timer, 181, false, 60);
        tick(timer, 190, true, 60);
        check(timer.challenge() == challenge, "Warp must preserve pending challenge");
        check(!timer.answer(challenge.answer() + 1, 190 * MINUTE_MILLIS), "Reject wrong answer");
        check(timer.isBreakDue(), "Wrong answer retains grayscale");
        check(timer.answer(challenge.answer(), 190 * MINUTE_MILLIS), "Accept correct answer");
        check(!timer.isBreakDue() && timer.isPreBreak() && timer.remainingSeconds(60) == 600,
                "Correct answer returns to sepia with ten minutes remaining");
        check(timer.remainingSeconds(240) == 600, "Settings cannot extend a postponed break");
        check(tick(timer, 190, true, 60) == BreakTimer.Notice.NONE,
                "Answer does not count time spent in the break or repeat the warning");
        check(tick(timer, 199, true, 60) == BreakTimer.Notice.NONE, "No early postponed break");
        check(tick(timer, 200, true, 60) == BreakTimer.Notice.BREAK, "Break returns after ten minutes");
        check(timer.answer(timer.challenge().answer(), 202 * MINUTE_MILLIS), "Can postpone another break");
        check(timer.isPreBreak() && timer.remainingSeconds(60) == 600, "Repeated answers never fully reset");
        check(timer.addMinutes(10, 60, 202 * MINUTE_MILLIS) == BreakTimer.Notice.BREAK,
                "Added time uses postponed deadline");
        timer.reset();
        check(!timer.isPreBreak(), "A full reset removes sepia");
        tick(timer, 200, true, 30);
        check(tick(timer, 220, true, 30) == BreakTimer.Notice.WARNING, "30 minute setting warning");
        tick(timer, 230, true, 30);
        timer.reset();
        check(!timer.isBreakDue() && timer.remainingSeconds(30) == 1800, "A full reset clears challenge and clock");
        tick(timer, 0, true, 240);
        check(tick(timer, 230, true, 240) == BreakTimer.Notice.WARNING, "4 hour warning");
        check(tick(timer, 240, true, 240) == BreakTimer.Notice.BREAK, "4 hour break");
        timer.reset();
        check(timer.addMinutes(49, 60, 0) == BreakTimer.Notice.NONE, "Added time before warning");
        check(timer.remainingSeconds(60) == 660, "Added minutes advance internal counter");
        check(timer.addMinutes(1, 60, 0) == BreakTimer.Notice.WARNING, "Added time triggers warning immediately");
        check(tick(timer, 0, true, 60) == BreakTimer.Notice.NONE, "Added time warning is not repeated");
        check(timer.addMinutes(10, 60, 0) == BreakTimer.Notice.BREAK, "Added time triggers break immediately");
        var addedChallenge = timer.challenge();
        check(timer.addMinutes(100, 60, 0) == BreakTimer.Notice.NONE && timer.challenge() == addedChallenge,
                "Added time preserves an active challenge");
        timer.reset();
        check(timer.addMinutes(Integer.MAX_VALUE, 60, 0) == BreakTimer.Notice.BREAK,
                "Large additions cannot overflow and trigger break directly");
        timer.reset();
        check(timer.remainingSeconds(60) == 3600, "Reset clears artificially added time");

        testMinimumBreak();
        testRestoredBreak();
        testSepiaSchedule();

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
        System.out.println("Checks passed: menu stopwatch, reconnects, restored breaks, thresholds, alarms, quiz, sepia schedule.");
    }

    private static void testMinimumBreak() {
        BreakTimer timer = new BreakTimer();
        long start = 1_000_000L;
        check(timer.breakElapsedSeconds(start) == -1, "Stopwatch is hidden before the break");
        timer.addMinutes(60, 60, start);
        var challenge = timer.challenge();
        check(timer.breakElapsedSeconds(start) == 0, "Stopwatch starts with grayscale");
        check(timer.breakAwaySeconds(start) == -1, "Grayscale alone does not start time away from the world");
        check(timer.breakElapsedSeconds(start + 61_000) == 61, "Stopwatch advances with no active-play ticks");
        check(!timer.answer(challenge.answer(), start + 119_999), "Correct answer cannot end a break early");

        long firstExit = start + 121_000;
        timer.leaveWorld(firstExit);
        timer.leaveWorld(firstExit + 30_000);
        check(timer.leftAtMillis() == firstExit, "Changing menus does not restart time away from the world");
        check(timer.breakAwaySeconds(firstExit + 119_999) == 119, "Ready status uses uninterrupted time away");
        timer.returnToWorld(firstExit + 119_999);
        check(timer.isBreakDue() && timer.challenge() == challenge, "Early return preserves grayscale and challenge");
        check(timer.breakAwaySeconds(firstExit + 119_999) == -1,
                "Time away stops counting as soon as the player returns");

        long secondExit = firstExit + 123_000;
        timer.leaveWorld(secondExit);
        timer.returnToWorld(secondExit + 119_999);
        check(timer.isBreakDue(), "Separate short exits cannot add up to a valid break");

        long lastExit = secondExit + 123_000;
        timer.leaveWorld(lastExit);
        check(timer.breakAwaySeconds(lastExit + 120_000) == BreakTimer.BREAK_MINIMUM_SECONDS,
                "Menu recognizes the threshold without dismissing the break");
        check(timer.isBreakDue(), "Break stays active while still in the menu");
        timer.returnToWorld(lastExit + 120_000);
        check(!timer.isBreakDue() && !timer.isPreBreak() && timer.remainingSeconds(60) == 3600,
                "Exactly two uninterrupted minutes away restore normal colors and a full timer");
        check(timer.breakElapsedSeconds(lastExit + 120_000) == -1, "Returning after a valid break hides the stopwatch");
    }

    private static void testRestoredBreak() {
        long start = 1_000_000L;
        long exit = start + 30_000;
        BreakTimer beforeRestart = new BreakTimer();
        beforeRestart.addMinutes(60, 60, start);
        beforeRestart.leaveWorld(exit);

        BreakTimer afterQuickRestart = new BreakTimer();
        afterQuickRestart.restoreBreak(beforeRestart.challenge(), beforeRestart.breakStartedAtMillis(),
                beforeRestart.leftAtMillis());
        afterQuickRestart.returnToWorld(exit + 10_000);
        check(afterQuickRestart.isBreakDue() && afterQuickRestart.challenge().equals(beforeRestart.challenge()),
                "Quick restart preserves the pending break and challenge");
        check(afterQuickRestart.breakElapsedSeconds(exit + 10_000) == 40, "Restart preserves elapsed time");
        check(afterQuickRestart.remainingSeconds(60) == 0, "Restored break is due immediately");

        BreakTimer afterLongRestart = new BreakTimer();
        afterLongRestart.restoreBreak(beforeRestart.challenge(), beforeRestart.breakStartedAtMillis(),
                beforeRestart.leftAtMillis());
        long now = start + 15 * MINUTE_MILLIS;
        check(afterLongRestart.breakElapsedSeconds(now) == 900, "Stopwatch continues past two minutes in the menu");
        afterLongRestart.returnToWorld(now);
        check(!afterLongRestart.isBreakDue() && afterLongRestart.remainingSeconds(60) == 3600,
                "A long break qualifies even if Minecraft was closed");
    }

    private static void testSepiaSchedule() {
        check(SepiaSchedule.parseTime("12:00 AM").equals(LocalTime.MIDNIGHT), "12 AM is midnight");
        check(SepiaSchedule.parseTime("12:00 PM").equals(LocalTime.NOON), "12 PM is noon");
        check(SepiaSchedule.parseTime(" 6:45 pm ").equals(LocalTime.of(18, 45)),
                "AM/PM accepts lowercase and surrounding spaces");
        check(SepiaSchedule.formatTime(LocalTime.of(7, 5)).equals("7:05 AM"), "Time uses h:mm AM/PM");
        check(SepiaSchedule.formatTime(LocalTime.of(22, 7)).equals("10:07 PM"), "Evening time uses PM");

        for (String invalid : new String[]{"0:00 AM", "13:00 PM", "6:60 AM", "6:5 AM", "6:00", "6:00 PM extra"}) {
            try {
                SepiaSchedule.parseTime(invalid);
                throw new AssertionError("Invalid AM/PM time was accepted: " + invalid);
            } catch (DateTimeParseException expected) { }
        }

        SepiaSchedule daytime = new SepiaSchedule(true, LocalTime.of(8, 15), LocalTime.of(17, 30));
        check(!daytime.isActive(LocalTime.of(8, 14, 59)), "Sepia is off immediately before the start");
        check(daytime.isActive(LocalTime.of(8, 15)), "Sepia starts at the inclusive start time");
        check(daytime.isActive(LocalTime.of(17, 29, 59)), "Sepia is on immediately before the end");
        check(!daytime.isActive(LocalTime.of(17, 30)), "Sepia stops at the exclusive end time");

        SepiaSchedule overnight = new SepiaSchedule(true, LocalTime.of(22, 0), LocalTime.of(6, 0));
        check(!overnight.isActive(LocalTime.of(21, 59, 59)), "Overnight schedule waits for its PM start");
        check(overnight.isActive(LocalTime.of(22, 0)), "Overnight schedule starts in the evening");
        check(overnight.isActive(LocalTime.MIDNIGHT), "Overnight schedule spans midnight");
        check(overnight.isActive(LocalTime.of(5, 59, 59)), "Overnight schedule covers early morning");
        check(!overnight.isActive(LocalTime.of(6, 0)), "Overnight schedule ends in the morning");
        check(!SepiaSchedule.DEFAULT.enabled(), "New and existing installs default to off");
        SepiaSchedule disabled = new SepiaSchedule(false, overnight.start(), overnight.end());
        check(!disabled.isActive(LocalTime.MIDNIGHT), "A disabled schedule never activates in its range");

        try {
            new SepiaSchedule(true, LocalTime.NOON, LocalTime.NOON);
            throw new AssertionError("Identical start and end times were accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
