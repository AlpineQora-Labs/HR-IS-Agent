package com.taportal.domain.engagement;

import com.taportal.domain.forms.FormDefinition;
import com.taportal.domain.forms.FormDefinitionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Event templates, and what a new event takes from one.
 *
 * <p>Rules: a template's name is its own; its forms must be of the right kind (an event intake
 * form, a student registration form) and not themselves form templates; its timezone must be a
 * real one. A template is switched off, not deleted, once events were made from it, so those
 * events can still say where they came from.
 */
@Service
@Transactional(readOnly = true)
public class EventTemplateService {

    /** What a template holds, as the screens send and read it. */
    public record TemplateDto(
            UUID id, String name, String eventType, String format, String country, String timezone,
            UUID intakeFormId, UUID registrationFormId, Integer capacity, boolean waitlistEnabled,
            Integer closeDaysBefore, Integer publishDaysBefore, String consentText, boolean active, long eventsMade) {
    }

    private static final List<String> TYPES = List.of("CAMPUS", "CAREER_FAIR", "HIRING_EVENT", "WEBINAR");
    private static final List<String> FORMATS = List.of("IN_PERSON", "VIRTUAL", "HYBRID");

    private final EventTemplateRepository templates;
    private final RecruitingEventRepository events;
    private final FormDefinitionRepository forms;

    public EventTemplateService(
            EventTemplateRepository templates, RecruitingEventRepository events, FormDefinitionRepository forms) {
        this.templates = templates;
        this.events = events;
        this.forms = forms;
    }

    public List<TemplateDto> list() {
        return templates.findByOrderByName().stream().map(this::toDto).toList();
    }

    @Transactional
    public TemplateDto create(TemplateDto in) {
        String name = name(in);
        if (templates.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is already a template called " + name);
        }
        EventTemplate t = new EventTemplate();
        apply(t, in, name);
        return toDto(templates.save(t));
    }

    @Transactional
    public TemplateDto update(UUID id, TemplateDto in) {
        EventTemplate t = require(id);
        String name = name(in);
        if (templates.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is already a template called " + name);
        }
        apply(t, in, name);
        return toDto(templates.save(t));
    }

    /** Deletes a template nothing was made from; one that events came from can only be switched off. */
    @Transactional
    public void delete(UUID id) {
        EventTemplate t = require(id);
        if (events.countByTemplateId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Events were created from this template, so it cannot be deleted. Switch it off instead.");
        }
        templates.delete(t);
    }

    /**
     * Gives a new event what its template holds: country, format, the registration form, seats,
     * waitlist, when registration closes and the consent wording. What the caller already set on
     * the event (a registration form picked by hand) is kept.
     */
    public void applyTo(RecruitingEvent event, UUID templateId) {
        EventTemplate t = require(templateId);
        if (!t.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The template " + t.getName() + " is switched off");
        }
        event.setTemplateId(t.getId());
        event.setCountry(t.getCountry());
        event.setFormat(t.getFormat());
        if (event.getRegistrationFormId() == null) {
            event.setRegistrationFormId(t.getRegistrationFormId());
        }
        event.setCapacity(t.getCapacity());
        event.setWaitlistEnabled(t.isWaitlistEnabled());
        event.setConsentText(t.getConsentText());
        event.setRegistrationClosesAt(closesAt(event.getStartsAt(), t.getCloseDaysBefore()));
        if (event.getPublishAt() == null) {
            // Unless a moment was chosen by hand. One already past simply means "as soon as approved".
            event.setPublishAt(closesAt(event.getStartsAt(), t.getPublishDaysBefore()));
        }
    }

    /** So many whole days before the start; null when the template sets none. */
    static OffsetDateTime closesAt(OffsetDateTime startsAt, Integer daysBefore) {
        return startsAt == null || daysBefore == null ? null : startsAt.minusDays(daysBefore);
    }

    private EventTemplate require(UUID id) {
        return templates.findById(id).orElseThrow(() -> new EntityNotFoundException("Event template not found: " + id));
    }

    private static String name(TemplateDto in) {
        if (in.name() == null || in.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Give the template a name");
        }
        return in.name().trim();
    }

    private void apply(EventTemplate t, TemplateDto in, String name) {
        if (in.eventType() == null || !TYPES.contains(in.eventType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose what kind of event this is");
        }
        if (in.format() == null || !FORMATS.contains(in.format())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose in person, virtual or hybrid");
        }
        try {
            ZoneId.of(in.timezone());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose a timezone");
        }
        if (in.capacity() != null && in.capacity() < 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Seats must be at least 1, or empty for no limit");
        }
        if (in.closeDaysBefore() != null && in.closeDaysBefore() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Days before the event cannot be negative");
        }
        if (in.publishDaysBefore() != null && in.publishDaysBefore() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Days before the event cannot be negative");
        }
        if (in.publishDaysBefore() != null && in.closeDaysBefore() != null && in.publishDaysBefore() < in.closeDaysBefore()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The event must be published before registration closes");
        }
        requireForm(in.intakeFormId(), "EVENT_INTAKE", "an event intake form");
        requireForm(in.registrationFormId(), "EVENT_REGISTRATION", "a student registration form");
        t.setName(name);
        t.setEventType(in.eventType());
        t.setFormat(in.format());
        t.setCountry(in.country() == null || in.country().isBlank() ? null : in.country().trim().toUpperCase());
        t.setTimezone(in.timezone());
        t.setIntakeFormId(in.intakeFormId());
        t.setRegistrationFormId(in.registrationFormId());
        t.setCapacity(in.capacity());
        t.setWaitlistEnabled(in.waitlistEnabled());
        t.setCloseDaysBefore(in.closeDaysBefore());
        t.setPublishDaysBefore(in.publishDaysBefore());
        t.setConsentText(in.consentText() == null || in.consentText().isBlank() ? null : in.consentText().trim());
        t.setActive(in.active());
    }

    private void requireForm(UUID formId, String purpose, String what) {
        if (formId == null) {
            return;
        }
        FormDefinition form = forms.findById(formId)
                .orElseThrow(() -> new EntityNotFoundException("Form not found: " + formId));
        if (!purpose.equals(form.getPurpose()) || form.isTemplate()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose " + what + " from the form library");
        }
    }

    private TemplateDto toDto(EventTemplate t) {
        return new TemplateDto(
                t.getId(), t.getName(), t.getEventType(), t.getFormat(), t.getCountry(), t.getTimezone(),
                t.getIntakeFormId(), t.getRegistrationFormId(), t.getCapacity(), t.isWaitlistEnabled(),
                t.getCloseDaysBefore(), t.getPublishDaysBefore(), t.getConsentText(), t.isActive(), events.countByTemplateId(t.getId()));
    }
}
