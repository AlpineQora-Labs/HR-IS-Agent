package com.taportal.domain.engagement;

import com.taportal.api.CampusDtos.RegisterRequest;
import com.taportal.api.CampusDtos.RegistrationRow;
import com.taportal.api.CampusDtos.SchoolRow;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Campus module (the Oleeo-replacement): schools + per-student event rosters.
 * Every registration becomes a real candidate (deduped by email), so campus
 * events feed the same pipeline, CRM pools and Aria journeys as every other
 * source — no separate silo.
 */
@Service
@Transactional(readOnly = true)
public class CampusService {

    private final SchoolRepository schools;
    private final EventRegistrationRepository registrations;
    private final RecruitingEventRepository events;
    private final CandidateRepository candidates;

    public CampusService(
            SchoolRepository schools,
            EventRegistrationRepository registrations,
            RecruitingEventRepository events,
            CandidateRepository candidates) {
        this.schools = schools;
        this.registrations = registrations;
        this.events = events;
        this.candidates = candidates;
    }

    public List<SchoolRow> schools() {
        return schools.findByOrderByName().stream()
                .map(s -> new SchoolRow(s.getId(), s.getName(), s.getLocation(), s.getTier()))
                .toList();
    }

    public List<RegistrationRow> roster(UUID eventId) {
        Map<UUID, String> names = schools.findAll().stream()
                .collect(Collectors.toMap(School::getId, School::getName));
        return registrations.findByEventIdOrderByCreatedAt(eventId).stream()
                .map(r -> toRow(r, r.getSchoolId() == null ? null : names.get(r.getSchoolId())))
                .toList();
    }

    /** Register a student (pre-registration or booth walk-in) and materialize the candidate. */
    @Transactional
    public RegistrationRow register(UUID eventId, RegisterRequest request) {
        events.findById(eventId).orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        String email = request.email() == null ? "" : request.email().trim();
        if (email.isEmpty() || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name and email are required");
        }
        if (registrations.existsByEventIdAndEmailIgnoreCase(eventId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this event");
        }

        Candidate candidate = candidates.findByEmailIgnoreCase(email).orElseGet(() -> {
            Candidate c = new Candidate();
            c.setName(request.name().trim());
            c.setEmail(email);
            c.setYearsExperience(BigDecimal.ZERO);
            c.setSource("CAMPUS");
            c.setPreferredLanguage("en");
            c.setLifecycle("ACTIVE");
            return candidates.save(c);
        });

        EventRegistration reg = new EventRegistration();
        reg.setEventId(eventId);
        reg.setCandidateId(candidate.getId());
        reg.setName(request.name().trim());
        reg.setEmail(email);
        reg.setSchoolId(request.schoolId());
        reg.setMajor(request.major());
        reg.setGradYear(request.gradYear());
        boolean walkIn = Boolean.TRUE.equals(request.walkIn());
        reg.setSource(walkIn ? "WALK_IN" : "REGISTERED");
        // A walk-in is standing at the booth — they're checked in by definition.
        reg.setStatus(walkIn ? "CHECKED_IN" : "REGISTERED");
        reg.setCheckedInAt(walkIn ? OffsetDateTime.now() : null);
        reg = registrations.save(reg);

        String schoolName = reg.getSchoolId() == null ? null
                : schools.findById(reg.getSchoolId()).map(School::getName).orElse(null);
        return toRow(reg, schoolName);
    }

    @Transactional
    public RegistrationRow transition(UUID registrationId, String status) {
        if (!List.of("CHECKED_IN", "NO_SHOW", "REGISTERED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
        EventRegistration reg = registrations.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found: " + registrationId));
        reg.setStatus(status);
        reg.setCheckedInAt("CHECKED_IN".equals(status) ? OffsetDateTime.now() : null);
        reg = registrations.save(reg);
        String schoolName = reg.getSchoolId() == null ? null
                : schools.findById(reg.getSchoolId()).map(School::getName).orElse(null);
        return toRow(reg, schoolName);
    }

    private static RegistrationRow toRow(EventRegistration r, String schoolName) {
        return new RegistrationRow(
                r.getId(), r.getEventId(), r.getCandidateId(), r.getName(), r.getEmail(),
                r.getSchoolId(), schoolName, r.getMajor(), r.getGradYear(),
                r.getSource(), r.getStatus(), r.getCheckedInAt());
    }
}
