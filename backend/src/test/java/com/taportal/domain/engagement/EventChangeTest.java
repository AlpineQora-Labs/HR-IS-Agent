package com.taportal.domain.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Which changes to an event are to when or where it is held (and so need approving again)")
class EventChangeTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-12-03T17:00:00Z");
    private static final OffsetDateTime END = START.plusHours(3);

    private static RecruitingEvent event() {
        RecruitingEvent e = new RecruitingEvent();
        e.setName("LSE Campus Drive");
        e.setStartsAt(START);
        e.setEndsAt(END);
        e.setTimezone("Europe/London");
        e.setLocation("London, United Kingdom");
        e.setFormat("IN_PERSON");
        return e;
    }

    private static boolean changed(OffsetDateTime start, OffsetDateTime end, String tz, String place, String format) {
        return EngagementService.whenOrWhereChanged(event(), start, end, tz, place, format);
    }

    @Test
    @DisplayName("nothing changed")
    void same() {
        assertThat(changed(START, END, "Europe/London", "London, United Kingdom", "IN_PERSON")).isFalse();
    }

    @Test
    @DisplayName("the same moment written in another offset is not a change")
    void sameInstantOtherOffset() {
        assertThat(changed(OffsetDateTime.parse("2026-12-03T12:00:00-05:00"), END, "Europe/London",
                "London, United Kingdom", "IN_PERSON")).isFalse();
    }

    @Test
    @DisplayName("spaces around the place are not a change")
    void tidyPlace() {
        assertThat(changed(START, END, "Europe/London", "  London, United Kingdom ", "IN_PERSON")).isFalse();
    }

    @Test
    @DisplayName("a new date, end time, timezone, place or format each is")
    void each() {
        assertThat(changed(START.plusDays(1), END.plusDays(1), "Europe/London", "London, United Kingdom", "IN_PERSON")).isTrue();
        assertThat(changed(START, END.plusHours(1), "Europe/London", "London, United Kingdom", "IN_PERSON")).isTrue();
        assertThat(changed(START, END, "Europe/Dublin", "London, United Kingdom", "IN_PERSON")).isTrue();
        assertThat(changed(START, END, "Europe/London", "Manchester, United Kingdom", "IN_PERSON")).isTrue();
        assertThat(changed(START, END, "Europe/London", "London, United Kingdom", "VIRTUAL")).isTrue();
        assertThat(changed(START, null, "Europe/London", "London, United Kingdom", "IN_PERSON")).isTrue();
    }
}
