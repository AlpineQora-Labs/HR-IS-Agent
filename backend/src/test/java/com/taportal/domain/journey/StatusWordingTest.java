package com.taportal.domain.journey;

import static org.assertj.core.api.Assertions.assertThat;

import com.taportal.domain.job.JobService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusWordingTest {

    private static final OffsetDateTime TUESDAY_10 = OffsetDateTime.parse("2026-10-06T14:00:00Z");

    @Test
    @DisplayName("every stage the pipeline has is worded, and none falls to the catch-all")
    void everyStageIsWorded() {
        String fallback = StatusWording.of("SOMETHING_NEW", null, null);
        for (String stage : JobService.STAGES) {
            assertThat(StatusWording.of(stage, null, null)).as(stage).isNotBlank().isNotEqualTo(fallback);
        }
    }

    @Test
    void anInterviewStageDependsOnTheInterview() {
        assertThat(StatusWording.of("INTERVIEW", "SLOTS_PROPOSED", null))
                .isEqualTo("you are invited to interview. Reply TIMES to see the times");
        assertThat(StatusWording.of("INTERVIEW", "SCHEDULED", TUESDAY_10, TUESDAY_10.minusDays(2)))
                .isEqualTo("your interview is confirmed for Tue, Oct 6, 10:00 AM ET. Reply RESCHEDULE to change it");
        assertThat(StatusWording.of("INTERVIEW", "SCHEDULED", TUESDAY_10, TUESDAY_10.plusHours(3)))
                .as("a time gone by is an interview held, not one to look forward to")
                .isEqualTo("your interview has taken place and the team is reviewing");
        assertThat(StatusWording.of("INTERVIEW", "CANCELED", null))
                .isEqualTo("your interview was cancelled, and your recruiter will be in touch");
        assertThat(StatusWording.of("INTERVIEW", "REQUESTED", null))
                .isEqualTo("you are selected for interview and we are arranging times");
        assertThat(StatusWording.of("INTERVIEW", null, null))
                .isEqualTo("you are selected for interview and we are arranging times");
    }

    @Test
    @DisplayName("wording is plain characters a text message can carry")
    void wordingIsPlainText() {
        for (String stage : JobService.STAGES) {
            for (String iv : new String[] {null, "REQUESTED", "SLOTS_PROPOSED", "SCHEDULED", "COMPLETED", "NO_SHOW", "CANCELED"}) {
                assertThat(StatusWording.of(stage, iv, TUESDAY_10)).as(stage + "/" + iv).matches("[\\x20-\\x7E]+");
            }
        }
    }

    @Test
    void timesAreEasternAndSaySo() {
        assertThat(JourneyTimes.when(TUESDAY_10)).isEqualTo("Tue, Oct 6, 10:00 AM ET");
        assertThat(JourneyTimes.when(OffsetDateTime.parse("2026-12-01T19:30:00Z"))).isEqualTo("Tue, Dec 1, 2:30 PM ET");
        assertThat(JourneyTimes.when(null)).isEmpty();
    }

    @Test
    void offeredTimesAreNumberedFromOne() {
        assertThat(JourneyTimes.numbered(java.util.List.of(TUESDAY_10, TUESDAY_10.plusDays(1).plusMinutes(270))))
                .isEqualTo("1) Tue, Oct 6, 10:00 AM ET\n2) Wed, Oct 7, 2:30 PM ET");
        assertThat(JourneyTimes.choices(3)).isEqualTo("1, 2 or 3");
        assertThat(JourneyTimes.choices(2)).isEqualTo("1 or 2");
        assertThat(JourneyTimes.choices(1)).isEqualTo("1");
    }

    @Test
    void everyPointHasAKeyAndALabel() {
        for (JourneyPoint p : JourneyPoint.values()) {
            assertThat(p.templateKey()).isEqualTo("journey." + p.name().toLowerCase());
            assertThat(p.label()).isNotBlank();
            assertThat(JourneyPoint.of(p.name().toLowerCase())).contains(p);
        }
        assertThat(JourneyPoint.of("nope")).isEmpty();
        assertThat(JourneyPoint.of(null)).isEmpty();
        assertThat(JourneyPoint.onTheDrawing()).hasSize(7);
    }

    @Test
    void closedStagesAreTheOnesNoLongerRunning() {
        assertThat(JobService.STAGES.stream().filter(StatusWording::closed))
                .containsExactly("HIRED", "REJECTED", "WITHDRAWN");
    }
}
