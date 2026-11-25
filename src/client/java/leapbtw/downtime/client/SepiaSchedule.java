package leapbtw.downtime.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;

public record SepiaSchedule(boolean enabled, LocalTime start, LocalTime end) {
    public static final SepiaSchedule DEFAULT = new SepiaSchedule(false, LocalTime.of(22, 0), LocalTime.of(6, 0));

    private static final DateTimeFormatter TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(' ')
            .appendText(ChronoField.AMPM_OF_DAY, TextStyle.SHORT)
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    public SepiaSchedule {
        Objects.requireNonNull(start, "Start time");
        Objects.requireNonNull(end, "End time");
        if (start.equals(end)) throw new IllegalArgumentException("Start and end must be different");
    }

    public static LocalTime parseTime(String input) {
        return LocalTime.parse(input.strip(), TIME_FORMAT);
    }

    public static String formatTime(LocalTime time) {
        return TIME_FORMAT.format(time);
    }

    public boolean isActive(LocalTime now) {
        if (!enabled) return false;
        boolean atOrAfterStart = !now.isBefore(start);
        boolean beforeEnd = now.isBefore(end);
        return start.isBefore(end) ? atOrAfterStart && beforeEnd : atOrAfterStart || beforeEnd;
    }
}
