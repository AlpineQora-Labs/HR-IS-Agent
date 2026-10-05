package com.taportal.domain.engagement;

import com.taportal.api.CampusDtos.RegisterRequest;
import com.taportal.api.CampusDtos.RegistrationRow;
import com.taportal.api.CampusDtos.SchoolRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.forms.FormDefinitionRepository;
import com.taportal.domain.forms.FormLogicService;
import com.taportal.domain.forms.FormResponse;
import com.taportal.domain.forms.FormResponseRepository;
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
 *
 * <p>Rules kept here:</p>
 * <ul>
 *   <li><b>Seats.</b> A student who registers gets a seat while there is one, joins the waitlist
 *       when the event is full and has one, and is refused when it is full without one or
 *       registration has closed ({@link EventSeats}). A walk-in added by a recruiter at the booth
 *       is seated regardless: the recruiter is looking at the room.</li>
 *   <li><b>Waitlist.</b> When a seated student cancels, the student who has waited longest takes
 *       the seat. A recruiter can also seat a waitlisted student by hand.</li>
 *   <li><b>Consent.</b> Nobody is registered without agreeing to their details being kept; when
 *       they agreed is recorded.</li>
 *   <li><b>One form per event.</b> An event registers students with its own form when it has
 *       one, the default otherwise; either way its School question lists the schools on record.</li>
 *   <li><b>One student, one record.</b> A student already on record under another address (their
 *       own email beside the school one) or under the same phone number and name is that
 *       candidate, not a new one.</li>
 *   <li><b>Told what happened.</b> Registering, being waitlisted, being seated and being
 *       cancelled each send the student an email ({@link EventMessages}).</li>
 *   <li><b>Evaluation.</b> A rating, notes and a shortlist mark per student, and an application
 *       made for a student put forward for a job (one per student and job).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CampusService {

    private final SchoolRepository schools;
    private final EventRegistrationRepository registrations;
    private final RecruitingEventRepository events;
    private final com.taportal.domain.approval.ApprovalRequestRepository approvalRequests;
    private final com.taportal.domain.notification.NotificationService notifications;
    private final CandidateRepository candidates;
    private final FormDefinitionRepository formDefinitions;
    private final FormLogicService formLogic;
    private final FormResponseRepository formResponses;
    private final ObjectMapper objectMapper;
    private final com.taportal.domain.application.ApplicationRepository applications;
    private final com.taportal.domain.job.JobRepository jobs;
    private final String companyName;
    private final EventMessages messages;

    public CampusService(
            SchoolRepository schools,
            EventRegistrationRepository registrations,
            RecruitingEventRepository events,
            CandidateRepository candidates,
            FormDefinitionRepository formDefinitions,
            FormLogicService formLogic,
            FormResponseRepository formResponses,
            ObjectMapper objectMapper,
            com.taportal.domain.approval.ApprovalRequestRepository approvalRequests,
            com.taportal.domain.notification.NotificationService notifications,
            com.taportal.domain.application.ApplicationRepository applications,
            com.taportal.domain.job.JobRepository jobs,
            @org.springframework.beans.factory.annotation.Value("${app.company-name:Bank of America Careers}")
                    String companyName,
            EventMessages messages) {
        this.messages = messages;
        this.applications = applications;
        this.jobs = jobs;
        this.companyName = companyName;
        this.schools = schools;
        this.approvalRequests = approvalRequests;
        this.notifications = notifications;
        this.registrations = registrations;
        this.events = events;
        this.candidates = candidates;
        this.formDefinitions = formDefinitions;
        this.formLogic = formLogic;
        this.formResponses = formResponses;
        this.objectMapper = objectMapper;
    }

    public List<SchoolRow> schools() {
        return schools.findByOrderByName().stream()
                .map(s -> new SchoolRow(s.getId(), s.getName(), s.getLocation(), s.getTier(), s.getCountry()))
                .toList();
    }

    /** Adds a school to the list. Its name is its own: a second school of the same name is refused. */
    @Transactional
    public SchoolRow addSchool(com.taportal.api.CampusDtos.SchoolCreate in) {
        String name = in.name().trim();
        if (schools.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, name + " is already on the list");
        }
        String tier = in.tier() == null || in.tier().isBlank() ? "TARGET" : in.tier().trim().toUpperCase();
        if (!List.of("CORE", "TARGET", "OPPORTUNISTIC").contains(tier)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Choose Core, Target or Opportunistic");
        }
        School s = new School();
        s.setName(name);
        s.setLocation(in.location() == null || in.location().isBlank() ? null : in.location().trim());
        s.setTier(tier);
        s.setCountry(in.country() == null || in.country().isBlank() ? null : in.country().trim().toUpperCase());
        s = schools.save(s);
        return new SchoolRow(s.getId(), s.getName(), s.getLocation(), s.getTier(), s.getCountry());
    }

    public List<RegistrationRow> roster(UUID eventId) {
        Map<UUID, String> names = schools.findAll().stream()
                .collect(Collectors.toMap(School::getId, School::getName));
        List<EventRegistration> rows = registrations.findByEventIdOrderByCreatedAt(eventId);
        // Rows come in the order students signed up, so the waitlist is numbered as it is met.
        int[] waiting = {0};
        return rows.stream()
                .map(r -> toRow(
                        r, r.getSchoolId() == null ? null : names.get(r.getSchoolId()),
                        "WAITLISTED".equals(r.getStatus()) ? ++waiting[0] : null))
                .toList();
    }

    /** The words a student agrees to when registering for an event: the event's own, or the standard ones. */
    public String consentText(UUID eventId) {
        return events.findById(eventId).map(RecruitingEvent::getConsentText)
                .filter(t -> t != null && !t.isBlank())
                .orElseGet(this::consentText);
    }

    /** The same agreement as a question, for the chat. An event's own wording is quoted as it stands. */
    public String consentQuestion(UUID eventId) {
        return events.findById(eventId).map(RecruitingEvent::getConsentText)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> "Please read this: \"" + t + "\" Do you agree?")
                .orElseGet(this::consentQuestion);
    }

    /** The standard words a student agrees to when registering. */
    public String consentText() {
        return "I agree that " + companyName + " may keep the details I give here to contact me about "
                + "this event and about jobs, and may share them with its recruiting team. "
                + "I can ask for them to be removed at any time.";
    }

    /** The same agreement, put as a question: what the assistant asks in chat. */
    public String consentQuestion() {
        return "Do you agree that " + companyName + " may keep the details you gave here to contact you about "
                + "this event and about jobs, and may share them with its recruiting team? "
                + "You can ask for them to be removed at any time.";
    }

    private static final List<String> SEATED = List.of("REGISTERED", "CHECKED_IN", "NO_SHOW");

    private long seatsTaken(UUID eventId) {
        return registrations.countByEventIdAndStatusIn(eventId, SEATED);
    }

    private EventSeats.State seatState(RecruitingEvent e) {
        return EventSeats.state(
                e.getCapacity(), e.isWaitlistEnabled(), e.getRegistrationClosesAt(), e.getStartsAt(),
                seatsTaken(e.getId()), OffsetDateTime.now());
    }

    /** Register a student (pre-registration or booth walk-in) and materialize the candidate. */
    @Transactional
    public RegistrationRow register(UUID eventId, RegisterRequest request) {
        RecruitingEvent event = events.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        String email = request.email() == null ? "" : request.email().trim();
        if (email.isEmpty() || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name and email are required");
        }
        if (!Boolean.TRUE.equals(request.consent())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The student must agree to their details being kept before they can be registered");
        }
        if (registrations.existsByEventIdAndEmailIgnoreCase(eventId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this event");
        }
        boolean walkIn = Boolean.TRUE.equals(request.walkIn());
        // A walk-in is standing at the booth: the recruiter seats them whatever the list says.
        EventSeats.State seats = walkIn ? EventSeats.State.OPEN : seatState(event);
        if (seats == EventSeats.State.CLOSED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Registration for this event has closed");
        }
        if (seats == EventSeats.State.FULL) {
            throw new ResponseStatusException(HttpStatus.GONE, "This event is full");
        }

        String personal = request.personalEmail() == null || request.personalEmail().isBlank()
                || request.personalEmail().trim().equalsIgnoreCase(email) ? null : request.personalEmail().trim();
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        Candidate candidate = knownStudent(request.name().trim(), email, personal, phone).orElseGet(() -> {
            Candidate c = new Candidate();
            c.setName(request.name().trim());
            c.setEmail(email);
            c.setYearsExperience(BigDecimal.ZERO);
            c.setSource("CAMPUS");
            c.setPreferredLanguage("en");
            c.setLifecycle("ACTIVE");
            return c;
        });
        // What the record did not have yet, it learns here: the other address, the phone number.
        String other = !email.equalsIgnoreCase(candidate.getEmail()) ? email : personal;
        if (other != null && (candidate.getAltEmail() == null || candidate.getAltEmail().isBlank())) {
            candidate.setAltEmail(other);
        }
        if (phone != null && (candidate.getPhone() == null || candidate.getPhone().isBlank())) {
            candidate.setPhone(phone);
        }
        candidate = candidates.save(candidate);
        if (registrations.existsByEventIdAndCandidateIdAndStatusNot(eventId, candidate.getId(), "CANCELLED")) {
            // The same student under their other address.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this event");
        }

        EventRegistration reg = new EventRegistration();
        reg.setEventId(eventId);
        reg.setCandidateId(candidate.getId());
        reg.setName(request.name().trim());
        reg.setEmail(email);
        reg.setSchoolId(request.schoolId());
        reg.setMajor(request.major());
        reg.setGradYear(request.gradYear());
        reg.setPersonalEmail(personal);
        reg.setPhone(phone);
        reg.setConsentAt(OffsetDateTime.now());
        reg.setSource(walkIn ? "WALK_IN" : "REGISTERED");
        // A walk-in is standing at the booth — they're checked in by definition.
        reg.setStatus(walkIn ? "CHECKED_IN" : seats == EventSeats.State.WAITLIST ? "WAITLISTED" : "REGISTERED");
        reg.setCheckedInAt(walkIn ? OffsetDateTime.now() : null);
        reg = registrations.save(reg);
        notifications.notifyRole("RECRUITER", "REGISTRATION",
                ("WAITLISTED".equals(reg.getStatus()) ? "Joined the waitlist: " : "New registration: ") + request.name(),
                event.getName(), "/events");
        RegistrationRow row = row(reg);
        if ("WAITLISTED".equals(reg.getStatus())) {
            messages.waitlisted(event, reg, row.waitlistPosition());
        } else if (!walkIn) {
            // A walk-in is already in the room: there is nothing to confirm to them.
            messages.registered(event, reg);
        }
        return row;
    }

    /**
     * The candidate a registering student already is, if they are on record: under the address
     * they gave, under their other address, or under the same phone number with the same name
     * (the rule {@link com.taportal.domain.journey.SamePerson} keeps for shared numbers: a phone
     * alone is not enough). The longest-standing record wins when several fit.
     */
    private java.util.Optional<Candidate> knownStudent(String name, String email, String personal, String phone) {
        List<Candidate> found = new java.util.ArrayList<>(candidates.findByEitherEmail(email));
        if (personal != null) {
            found.addAll(candidates.findByEitherEmail(personal));
        }
        if (found.isEmpty() && phone != null) {
            String e164 = com.taportal.domain.messaging.PhoneNumbers.normalise(phone);
            if (e164 != null) {
                Candidate asking = new Candidate();
                asking.setName(name);
                asking.setEmail(email);
                candidates.findByPhoneE164(e164).stream()
                        .filter(c -> com.taportal.domain.journey.SamePerson.same(asking, c))
                        .forEach(found::add);
            }
        }
        return found.stream().min(java.util.Comparator.comparing(
                Candidate::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
    }

    /* ---------- the form an event registers with ---------- */

    /**
     * The form students fill in for an event: the event's own when it has one, the default
     * Student registration form otherwise. Its School question (a choice question labelled
     * "school") lists the schools on record, plus "Other", whatever the form was saved with,
     * so the roster and the reports always name a school the same way.
     */
    public com.taportal.api.CampusDtos.RegistrationForm registrationForm(UUID eventId) {
        RecruitingEvent event = events.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        var definition = (event.getRegistrationFormId() == null ? java.util.Optional.<com.taportal.domain.forms.FormDefinition>empty()
                        : formDefinitions.findById(event.getRegistrationFormId()))
                .or(() -> formDefinitions.findFirstByPurposeAndDefaultForKindTrueAndTemplateFalse("EVENT_REGISTRATION"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No registration form is configured"));
        return new com.taportal.api.CampusDtos.RegistrationForm(
                definition.getId(), definition.getName(), withSchools(definition.getSchema()));
    }

    /** The public page's copy of {@link #registrationForm}: an event not yet approved has none. */
    public com.taportal.api.CampusDtos.RegistrationForm publicRegistrationForm(UUID eventId) {
        if (!publiclyVisible(eventId)) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
        return registrationForm(eventId);
    }

    private static final List<String> CHOICE_TYPES = List.of("dropdown", "single", "typeahead");

    /** The countries a form's Country question may name, as their ISO codes. Lower-cased names. */
    private static final Map<String, String> COUNTRY_CODES = Map.ofEntries(
            Map.entry("united states", "US"), Map.entry("usa", "US"), Map.entry("us", "US"),
            Map.entry("india", "IN"), Map.entry("united kingdom", "GB"), Map.entry("uk", "GB"),
            Map.entry("ireland", "IE"), Map.entry("germany", "DE"), Map.entry("france", "FR"),
            Map.entry("singapore", "SG"), Map.entry("hong kong", "HK"), Map.entry("japan", "JP"),
            Map.entry("australia", "AU"), Map.entry("canada", "CA"), Map.entry("united arab emirates", "AE"));

    private String withSchools(String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            List<School> all = schools.findByOrderByName();
            if (all.isEmpty()) {
                return schemaJson;
            }
            List<String> names = new java.util.ArrayList<>(all.stream().map(School::getName).toList());
            names.add("Other");
            JsonNode school = null;
            JsonNode country = null;
            for (JsonNode row : schema.path("rows")) {
                for (JsonNode slot : row.path("slots")) {
                    if (!slot.isObject() || !CHOICE_TYPES.contains(slot.path("type").asText())) {
                        continue;
                    }
                    String label = slot.path("label").asText("").toLowerCase(java.util.Locale.ROOT);
                    if (label.contains("school") && school == null) {
                        var options = ((com.fasterxml.jackson.databind.node.ObjectNode) slot).putArray("options");
                        names.forEach(options::add);
                        school = slot;
                    } else if (label.contains("country") && country == null) {
                        country = slot;
                    }
                }
            }
            // A form that also asks for the country offers only that country's schools: School
            // depends on Country. Written as one of the form's dependent choices, replacing any the
            // form was saved with for the School question, so the list is always the live one.
            if (school != null && country != null) {
                String schoolId = school.path("id").asText();
                var choices = objectMapper.createArrayNode();
                for (JsonNode c : schema.path("choices")) {
                    if (!schoolId.equals(c.path("targetId").asText())) {
                        choices.add(c);
                    }
                }
                var byCountry = objectMapper.createObjectNode();
                for (JsonNode option : country.path("options")) {
                    String said = option.asText("").trim();
                    String code = said.length() == 2 ? said.toUpperCase()
                            : COUNTRY_CODES.get(said.toLowerCase(java.util.Locale.ROOT));
                    var list = byCountry.putArray(said);
                    if (code != null) {
                        all.stream().filter(s -> code.equalsIgnoreCase(s.getCountry())).forEach(s -> list.add(s.getName()));
                    }
                    list.add("Other");
                }
                choices.addObject()
                        .put("id", "schools-by-country")
                        .put("targetId", schoolId)
                        .put("sourceId", country.path("id").asText())
                        .set("options", byCountry);
                ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("choices", choices);
            }
            return objectMapper.writeValueAsString(schema);
        } catch (Exception ex) {
            return schemaJson;
        }
    }

    /**
     * Register a student from an EVENT_REGISTRATION form submission — the single
     * business path shared by the public QR/web form and Aria's conversational
     * flow (three-tier rule: one authoritative service, many renderers).
     *
     * <p>Pipeline: validate against the form's if/then rules (hidden answers
     * stripped, required-if-visible enforced by {@link FormLogicService}) →
     * map well-known fields onto the roster row → {@link #register} (dedupes and
     * materializes the candidate) → attach the full sanitized answers to the
     * registration as a {@code form_response}.</p>
     *
     * <p>Field mapping is type-first (fullname → name, email → email — types are
     * unambiguous), then label heuristics for the rest (School / Major /
     * Graduation year). Unmapped answers still persist in the form response.</p>
     *
     * @param eventId     the event being registered for
     * @param answersJson submitted answers: [{fieldId, label, value}]
     * @return the created roster row
     * @throws ResponseStatusException 422 on rule violations, 409 on duplicates
     */
    /* ---------- public career-site surface (approval-gated) ---------- */

    /**
     * Is this event visible to candidates? Two things must both hold:
     *
     * <ul>
     *   <li><b>Approved.</b> Hidden while its latest approval request is PENDING or after a
     *       REJECTED decision, and when it was never sent because no workflow was switched on.
     *       Only events from before this rule that never went for approval are grandfathered.</li>
     *   <li><b>Published.</b> Its publishing date and time has come. With none set, it is
     *       published as soon as it is approved.</li>
     * </ul>
     */
    private boolean publiclyVisible(UUID eventId) {
        RecruitingEvent event = events.findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        String status = approvalRequests.findByItemTypeOrderByCreatedAtDesc("EVENT").stream()
                .filter((r) -> r.getItemRef().equals(eventId.toString()))
                .findFirst()
                .map((r) -> r.getStatus().name())
                .orElse(null);
        return isPublic(event, status);
    }

    /**
     * The same verdict for a caller that already knows the event's latest approval status
     * (null: it never went for approval).
     */
    public boolean isPublic(RecruitingEvent event, String approvalStatus) {
        // An event from before the rule that never went for approval stays as it was; every
        // other event needs an approved request, and having none (NOT_SENT) is not approval.
        boolean approved = (approvalStatus == null && !event.isApprovalRequired())
                || "APPROVED".equals(approvalStatus) || "AUTO_APPROVED".equals(approvalStatus);
        return approved && published(event.getPublishAt(), OffsetDateTime.now());
    }

    /** Whether a publishing moment has come. None set: yes. */
    static boolean published(OffsetDateTime publishAt, OffsetDateTime now) {
        return publishAt == null || !now.isBefore(publishAt);
    }

    /**
     * The public events board: upcoming, approved events, soonest first.
     *
     * @return public rows only — no funnel or hire data leaves this method
     */
    public List<com.taportal.api.CampusDtos.EventPublicInfo> publicBoard() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(1);
        return events.findByOrderByStartsAtDesc().stream()
                .filter((e) -> e.getStartsAt() != null && e.getStartsAt().isAfter(cutoff))
                .filter((e) -> publiclyVisible(e.getId()))
                .sorted(java.util.Comparator.comparing(RecruitingEvent::getStartsAt))
                .map(this::toPublicInfo)
                .toList();
    }

    /**
     * Public event header for the QR/registration page.
     *
     * @throws EntityNotFoundException when the event doesn't exist OR isn't
     *         publicly visible yet — unapproved events don't leak their existence
     */
    public com.taportal.api.CampusDtos.EventPublicInfo publicInfo(UUID eventId) {
        RecruitingEvent e = events.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        if (!publiclyVisible(eventId)) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
        return toPublicInfo(e);
    }

    private com.taportal.api.CampusDtos.EventPublicInfo toPublicInfo(RecruitingEvent e) {
        return new com.taportal.api.CampusDtos.EventPublicInfo(
                e.getId(), e.getName(), e.getType(), e.getLocation(),
                e.getStartsAt(), e.getEndsAt(), e.getTimezone(),
                seatState(e).name(), EventSeats.left(e.getCapacity(), seatsTaken(e.getId())),
                e.getRegistrationClosesAt(),
                e.getConsentText() != null && !e.getConsentText().isBlank() ? e.getConsentText() : consentText());
    }

    /**
     * @param consent whether the student agreed to {@link #consentText()}; registration is
     *                refused (422) without it
     */
    @Transactional
    public RegistrationRow registerFromForm(UUID eventId, String answersJson, boolean consent) {
        if (!publiclyVisible(eventId)) {
            throw new EntityNotFoundException("Event not found: " + eventId);
        }
        com.taportal.api.CampusDtos.RegistrationForm definition = registrationForm(eventId);
        String sanitized = formLogic.validateAndFilter(definition.schema(), answersJson);

        Map<String, String> byId = new java.util.HashMap<>();
        try {
            for (JsonNode a : objectMapper.readTree(sanitized)) {
                byId.put(a.path("fieldId").asText(""), a.path("value").asText(""));
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unparseable answers");
        }

        String name = null, email = null, major = null, schoolName = null, personal = null, phone = null;
        Integer gradYear = null;
        for (FormLogicService.Field f : formLogic.fields(definition.schema())) {
            String v = byId.get(f.id());
            if (v == null || v.isBlank()) {
                continue;
            }
            String label = f.label().toLowerCase(java.util.Locale.ROOT);
            if (f.type().equals("fullname") && name == null) {
                name = v;
            } else if (f.type().equals("email") && label.contains("personal") && personal == null) {
                // "Personal email": the student's own address, beside the school one.
                personal = v;
            } else if (f.type().equals("email") && email == null) {
                email = v;
            } else if (f.type().equals("email") && personal == null) {
                personal = v;
            } else if (f.type().equals("phone") && phone == null) {
                phone = v;
            } else if (label.contains("school") && schoolName == null) {
                schoolName = v;
            } else if (label.contains("grad") && gradYear == null) {
                try { gradYear = Integer.valueOf(v.replaceAll("\\D", "")); } catch (NumberFormatException ignored) { }
            } else if (label.contains("major") && major == null) {
                major = v;
            }
        }
        if (email == null && personal != null) {
            // A form that asks only for a personal address: that is the address.
            email = personal;
            personal = null;
        }
        if (name == null || email == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The registration form must collect a full name and an email");
        }
        final String wanted = schoolName;
        UUID schoolId = wanted == null ? null
                : schools.findAll().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(wanted))
                        .map(School::getId)
                        .findFirst().orElse(null);

        RegistrationRow row = register(
                eventId, new RegisterRequest(name, email, schoolId, major, gradYear, false, consent, personal, phone));

        // Keep the complete answer set (interests etc.) attached to the roster row.
        FormResponse response = new FormResponse();
        response.setFormPurpose("EVENT_REGISTRATION");
        response.setFormId(definition.formId());
        response.setSubjectType("REGISTRATION");
        response.setSubjectId(row.id());
        response.setAnswers(sanitized);
        formResponses.save(response);
        return row;
    }

    /**
     * Moves a student on the roster: checked in, no-show, back to registered, or cancelled.
     * Seating a waitlisted student is REGISTERED. Cancelling a seated student gives their seat
     * to whoever has waited longest.
     */
    @Transactional
    public RegistrationRow transition(UUID registrationId, String status) {
        if (!List.of("CHECKED_IN", "NO_SHOW", "REGISTERED", "CANCELLED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
        EventRegistration reg = registrations.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found: " + registrationId));
        boolean hadSeat = EventSeats.holdsSeat(reg.getStatus());
        boolean wasWaiting = "WAITLISTED".equals(reg.getStatus());
        boolean wasCancelled = "CANCELLED".equals(reg.getStatus());
        reg.setStatus(status);
        reg.setCheckedInAt("CHECKED_IN".equals(status) ? OffsetDateTime.now() : null);
        reg = registrations.saveAndFlush(reg);
        RecruitingEvent event = events.findById(reg.getEventId()).orElse(null);
        if (event != null) {
            if ("REGISTERED".equals(status) && wasWaiting) {
                messages.seated(event, reg);
            } else if ("CANCELLED".equals(status) && !wasCancelled) {
                messages.cancelled(event, reg);
            }
        }
        if ("CANCELLED".equals(status) && hadSeat) {
            seatNextInLine(reg.getEventId());
        }
        return row(reg);
    }

    /** A seat came free: the student who has waited longest takes it, if there is room for them. */
    private void seatNextInLine(UUID eventId) {
        RecruitingEvent event = events.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        if (event.getCapacity() != null && seatsTaken(eventId) >= event.getCapacity()) {
            return;
        }
        registrations.findByEventIdAndStatusOrderByCreatedAt(eventId, "WAITLISTED").stream().findFirst()
                .ifPresent(next -> {
                    next.setStatus("REGISTERED");
                    registrations.save(next);
                    messages.seated(event, next);
                    notifications.notifyRole("RECRUITER", "REGISTRATION",
                            "Seated from the waitlist: " + next.getName(), event.getName(), "/events");
                });
    }

    /* ---------- seat settings ---------- */

    /** Sets how many the event seats, when registration ends and whether it keeps a waitlist. */
    @Transactional
    public void seatSettings(UUID eventId, com.taportal.api.CampusDtos.SeatSettings in) {
        RecruitingEvent event = events.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        if (in.registrationClosesAt() != null && event.getStartsAt() != null
                && in.registrationClosesAt().isAfter(event.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Registration must close before the event starts");
        }
        event.setCapacity(in.capacity());
        event.setRegistrationClosesAt(in.registrationClosesAt());
        event.setWaitlistEnabled(in.waitlistEnabled());
        if (in.registrationFormId() != null) {
            var form = formDefinitions.findById(in.registrationFormId())
                    .orElseThrow(() -> new EntityNotFoundException("Form not found: " + in.registrationFormId()));
            if (!"EVENT_REGISTRATION".equals(form.getPurpose()) || form.isTemplate()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Choose a Student registration form, not a template or another kind of form");
            }
        }
        event.setRegistrationFormId(in.registrationFormId());
        if (in.publishAt() != null && event.getStartsAt() != null && !in.publishAt().isBefore(event.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The event must be published before it starts");
        }
        event.setPublishAt(in.publishAt());
        events.saveAndFlush(event);
        // More seats than before: the waitlist moves up, longest wait first.
        long free = in.capacity() == null ? Long.MAX_VALUE : in.capacity() - seatsTaken(eventId);
        for (EventRegistration next : registrations.findByEventIdAndStatusOrderByCreatedAt(eventId, "WAITLISTED")) {
            if (free-- <= 0) {
                break;
            }
            next.setStatus("REGISTERED");
            registrations.save(next);
            messages.seated(event, next);
        }
    }

    /* ---------- how each school has done ---------- */

    /**
     * Every school with what came of its students across all events: registered (not
     * cancelled), attended, shortlisted, put forward for a job, hired. Schools with no
     * students yet are listed too, so a target school with nothing to show is visible.
     * Busiest first.
     */
    public List<com.taportal.api.CampusDtos.SchoolReportRow> schoolReport() {
        Map<UUID, List<EventRegistration>> bySchool = registrations.findAll().stream()
                .filter(r -> r.getSchoolId() != null && !"CANCELLED".equals(r.getStatus()))
                .collect(Collectors.groupingBy(EventRegistration::getSchoolId));
        return schools.findByOrderByName().stream()
                .map(s -> {
                    List<EventRegistration> rows = bySchool.getOrDefault(s.getId(), List.of());
                    int hired = (int) rows.stream()
                            .map(EventRegistration::getApplicationId)
                            .filter(java.util.Objects::nonNull)
                            .map(id -> applications.findById(id).orElse(null))
                            .filter(a -> a != null && "HIRED".equals(a.getStage()))
                            .count();
                    return new com.taportal.api.CampusDtos.SchoolReportRow(
                            s.getId(), s.getName(), s.getLocation(), s.getTier(), s.getCountry(),
                            (int) rows.stream().map(EventRegistration::getEventId).distinct().count(),
                            rows.size(),
                            (int) rows.stream().filter(r -> "CHECKED_IN".equals(r.getStatus())).count(),
                            (int) rows.stream().filter(EventRegistration::isShortlisted).count(),
                            (int) rows.stream().filter(r -> r.getApplicationId() != null).count(),
                            hired);
                })
                .sorted(java.util.Comparator.comparingInt(com.taportal.api.CampusDtos.SchoolReportRow::registered).reversed())
                .toList();
    }

    /**
     * Reads the seat settings out of an event's intake answers, where the intake form asks for
     * them: a number labelled "capacity", a date labelled "deadline" (registration closes at the
     * end of that day, in the event's timezone) and a yes/no labelled "waitlist". An answer the
     * form does not ask for leaves that setting as it is.
     *
     * @param answersJson the stored answers: [{fieldId, label, value}]
     */
    @Transactional
    public void seatSettingsFromIntake(UUID eventId, String answersJson) {
        RecruitingEvent event = events.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        try {
            for (JsonNode a : objectMapper.readTree(answersJson)) {
                String label = a.path("label").asText("").toLowerCase(java.util.Locale.ROOT);
                String value = a.path("value").asText("").trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (label.contains("capacity")) {
                    String digits = value.replaceAll("\\D", "");
                    if (!digits.isEmpty() && Integer.parseInt(digits) > 0) {
                        event.setCapacity(Integer.parseInt(digits));
                    }
                } else if (label.contains("deadline")) {
                    java.time.ZoneId zone = java.time.ZoneId.of(event.getTimezone());
                    OffsetDateTime closes = java.time.LocalDate.parse(value.substring(0, Math.min(10, value.length())))
                            .plusDays(1).atStartOfDay(zone).toOffsetDateTime();
                    // A deadline after the event starts would close nothing: the start does that.
                    event.setRegistrationClosesAt(
                            event.getStartsAt() != null && closes.isAfter(event.getStartsAt()) ? null : closes);
                } else if (label.contains("waitlist")) {
                    event.setWaitlistEnabled(value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true"));
                }
            }
        } catch (Exception ex) {
            // An answer that is not a number or a date sets nothing; the recruiter can set it on the event.
            return;
        }
        events.save(event);
    }

    /* ---------- evaluation ---------- */

    /** Records what the recruiter made of a student: rating, notes, shortlist. */
    @Transactional
    public RegistrationRow evaluate(UUID registrationId, com.taportal.api.CampusDtos.EvaluationRequest in) {
        EventRegistration reg = registrations.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found: " + registrationId));
        reg.setRating(in.rating());
        reg.setNotes(in.notes() == null || in.notes().isBlank() ? null : in.notes().trim());
        if (in.shortlisted() != null) {
            reg.setShortlisted(in.shortlisted());
        }
        reg.setEvaluatedAt(OffsetDateTime.now());
        return row(registrations.save(reg));
    }

    /**
     * Puts a student forward for a job: an application is made for them, from this event, and
     * they are marked shortlisted. A student who already has an application for that job keeps
     * it; a second is never made.
     */
    @Transactional
    public RegistrationRow apply(UUID registrationId, UUID jobId) {
        EventRegistration reg = registrations.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found: " + registrationId));
        if (reg.getCandidateId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student has no candidate record");
        }
        jobs.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        com.taportal.domain.application.Application application = applications.findByCandidateId(reg.getCandidateId())
                .stream().filter(a -> jobId.equals(a.getJobId())).findFirst()
                .orElseGet(() -> {
                    com.taportal.domain.application.Application a = new com.taportal.domain.application.Application();
                    a.setCandidateId(reg.getCandidateId());
                    a.setJobId(jobId);
                    a.setStage("APPLIED");
                    a.setSource("CAMPUS_EVENT");
                    return applications.save(a);
                });
        reg.setApplicationId(application.getId());
        reg.setShortlisted(true);
        return row(registrations.save(reg));
    }

    /** Students from this event who were hired: their application reached HIRED. */
    public int hires(UUID eventId) {
        return (int) registrations.findByEventIdOrderByCreatedAt(eventId).stream()
                .map(EventRegistration::getApplicationId)
                .filter(java.util.Objects::nonNull)
                .map(id -> applications.findById(id).orElse(null))
                .filter(a -> a != null && "HIRED".equals(a.getStage()))
                .count();
    }

    /** One roster row with its school, waitlist place and application looked up. */
    private RegistrationRow row(EventRegistration reg) {
        String schoolName = reg.getSchoolId() == null ? null
                : schools.findById(reg.getSchoolId()).map(School::getName).orElse(null);
        Integer position = null;
        if ("WAITLISTED".equals(reg.getStatus())) {
            List<EventRegistration> waiting =
                    registrations.findByEventIdAndStatusOrderByCreatedAt(reg.getEventId(), "WAITLISTED");
            for (int i = 0; i < waiting.size(); i++) {
                if (waiting.get(i).getId().equals(reg.getId())) {
                    position = i + 1;
                }
            }
        }
        return toRow(reg, schoolName, position);
    }

    private RegistrationRow toRow(EventRegistration r, String schoolName, Integer waitlistPosition) {
        com.taportal.domain.application.Application application = r.getApplicationId() == null ? null
                : applications.findById(r.getApplicationId()).orElse(null);
        String jobTitle = application == null ? null
                : jobs.findById(application.getJobId()).map(com.taportal.domain.job.Job::getTitle).orElse(null);
        return new RegistrationRow(
                r.getId(), r.getEventId(), r.getCandidateId(), r.getName(), r.getEmail(),
                r.getSchoolId(), schoolName, r.getMajor(), r.getGradYear(),
                r.getSource(), r.getStatus(), r.getCheckedInAt(), waitlistPosition, r.getPersonalEmail(),
                r.getConsentAt(),
                r.getRating(), r.getNotes(), r.isShortlisted(),
                application == null ? null : application.getId(), jobTitle,
                application == null ? null : application.getStage());
    }
}
