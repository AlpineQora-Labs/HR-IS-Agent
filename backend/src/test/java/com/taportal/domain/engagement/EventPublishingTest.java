package com.taportal.domain.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("When an event goes on the careers site, and what it takes from its template")
class EventPublishingTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-05T10:00:00Z");

    @Test
    @DisplayName("with no publishing moment set, it is published straight away")
    void noMomentMeansNow() {
        assertThat(CampusService.published(null, NOW)).isTrue();
    }

    @Test
    @DisplayName("it is not published before its moment, and is from that moment on")
    void fromItsMoment() {
        assertThat(CampusService.published(NOW.plusSeconds(1), NOW)).isFalse();
        assertThat(CampusService.published(NOW, NOW)).isTrue();
        assertThat(CampusService.published(NOW.minusDays(3), NOW)).isTrue();
    }

    @Test
    @DisplayName("a template's days-before becomes a moment that many days ahead of the start")
    void daysBeforeTheStart() {
        OffsetDateTime starts = OffsetDateTime.parse("2026-11-20T14:00:00Z");
        assertThat(EventTemplateService.closesAt(starts, 30)).isEqualTo(OffsetDateTime.parse("2026-10-21T14:00:00Z"));
        assertThat(EventTemplateService.closesAt(starts, 0)).isEqualTo(starts);
        assertThat(EventTemplateService.closesAt(starts, null)).isNull();
    }
}
