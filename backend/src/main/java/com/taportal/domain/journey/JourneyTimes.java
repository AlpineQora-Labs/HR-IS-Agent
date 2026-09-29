package com.taportal.domain.journey;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * How the journey writes a time. Every time a candidate is told is in Eastern
 * Time and says so, in plain characters a text message can carry. Pure.
 */
public final class JourneyTimes {

    public static final ZoneId ZONE = ZoneId.of("America/New_York");

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH).withZone(ZONE);

    private JourneyTimes() {
    }

    /** "Tue, Oct 6, 10:00 AM ET" */
    public static String when(OffsetDateTime time) {
        return time == null ? "" : WHEN.format(time) + " ET";
    }

    /**
     * The times offered, numbered from 1, one to a line:
     * <pre>
     * 1) Tue, Oct 6, 10:00 AM ET
     * 2) Wed, Oct 7, 2:30 PM ET
     * </pre>
     */
    public static String numbered(List<OffsetDateTime> times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(") ").append(when(times.get(i)));
        }
        return sb.toString();
    }

    /** "1, 2 or 3" — the replies that pick one of {@code count} times. */
    public static String choices(int count) {
        if (count <= 1) {
            return "1";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                sb.append(i == count ? " or " : ", ");
            }
            sb.append(i);
        }
        return sb.toString();
    }
}
