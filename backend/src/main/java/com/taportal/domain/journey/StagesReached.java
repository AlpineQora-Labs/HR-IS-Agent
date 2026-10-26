package com.taportal.domain.journey;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The applications that reached a stage the candidate is told of, waiting to be told
 * (table {@code journey_stage_reached}, V38).
 *
 * <p>Why the wait: the pipeline moves an application one stage at a time, so a recruiter taking a
 * candidate from Interview to Offer passes Assessment on the way, and one recording a hire passes
 * Offer. Told at once, the candidate would hear of every stage passed through. Noted here instead,
 * a stage is told by {@link JourneyService#tellStagesReached} once the move is a few minutes old, and
 * only if the application is still at that stage. A stage passed through, or a move undone, is
 * dropped unsaid.
 *
 * <p>One row per application and point: reaching the same stage again starts the wait again.
 */
@Repository
public class StagesReached {

    private final JdbcTemplate jdbc;

    public StagesReached(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * An application reached the stage this point tells of.
     *
     * @param fromStage the stage it left, kept so that a candidate who was offered the job or hired is
     *                  never told by template that they were not taken forward
     */
    public void note(UUID applicationId, JourneyPoint point, String fromStage) {
        jdbc.update("""
                INSERT INTO journey_stage_reached (application_id, point, from_stage, reached_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (application_id, point)
                DO UPDATE SET from_stage = EXCLUDED.from_stage, reached_at = EXCLUDED.reached_at""",
                applicationId, point.name(), fromStage);
    }

    /** The stages reached at or before this moment, oldest first: their wait is over. */
    public List<Reached> reachedBefore(OffsetDateTime cutoff) {
        return jdbc.query("""
                SELECT application_id, point, from_stage FROM journey_stage_reached
                WHERE reached_at <= ? ORDER BY reached_at""",
                (rs, n) -> new Reached(rs.getObject("application_id", UUID.class), rs.getString("point"),
                        rs.getString("from_stage")),
                cutoff);
    }

    /** The stage has been dealt with: told, or dropped because the application moved on. */
    public void done(UUID applicationId, String point) {
        jdbc.update("DELETE FROM journey_stage_reached WHERE application_id = ? AND point = ?", applicationId, point);
    }

    /** A stage waiting to be told. {@code point} is a {@link JourneyPoint} name. */
    public record Reached(UUID applicationId, String point, String fromStage) {
    }
}
