package com.taportal.domain.briefing;

import com.taportal.api.MessageDtos.MessageRow;
import com.taportal.api.MessageDtos.Timeline;
import com.taportal.api.SchedulingDtos.AttentionItem;
import com.taportal.api.SchedulingDtos.InterviewerLoad;
import com.taportal.api.SchedulingDtos.Overview;
import com.taportal.domain.application.Application;
import com.taportal.domain.application.ApplicationRepository;
import com.taportal.domain.application.ScreeningAnswer;
import com.taportal.domain.application.ScreeningAnswerRepository;
import com.taportal.domain.briefing.Briefing.Link;
import com.taportal.domain.briefing.Briefing.Section;
import com.taportal.domain.briefing.Briefing.Why;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.interview.InterviewRepository;
import com.taportal.domain.interview.SchedulingOverviewService;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.job.JobService;
import com.taportal.domain.job.KnockoutQuestion;
import com.taportal.domain.job.KnockoutQuestionRepository;
import com.taportal.domain.journey.JourneyQueries;
import com.taportal.domain.messaging.CandidateMessage;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link Briefing} the recruiter assistant answers from: what the records say about the
 * day's interviews, one candidate, or one job, and the reasons the records give for what is not
 * moving.
 *
 * <p>This is where the assistant's rules live. Which facts a recruiter is told, which items count
 * as waiting, and which next step is offered are decided here, from the records, before any model
 * is involved. The assistant only words the result, and cannot add to it. The lanes of the
 * interview desk are taken from {@link SchedulingOverviewService} and the message timeline from
 * {@link JourneyQueries}, so a briefing never disagrees with the screens.
 *
 * <p>Read-only: nothing here changes a record.
 *
 * <h3>Privacy</h3>
 * People are placeholders ({@link Placeholders}); phone numbers and email addresses are never
 * copied in, and a candidate's own words have anything that looks like either taken out.
 */
@Service
@Transactional(readOnly = true)
public class BriefingService {

    /** Items listed per lane or list; the rest are counted, not listed. */
    static final int MAX_ITEMS = 6;
    /** Reasons given per briefing. */
    static final int MAX_WHYS = 8;
    /** Most recent texts and emails listed. */
    static final int MAX_MESSAGES = 8;
    /** Most recent applications described. */
    static final int MAX_APPLICATIONS = 4;

    private static final String DESK = "/interviews";

    private final SchedulingOverviewService overview;
    private final CandidateRepository candidates;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final InterviewRepository interviews;
    private final ScreeningAnswerRepository answers;
    private final KnockoutQuestionRepository questions;
    private final RecruiterUserRepository users;
    private final JourneyQueries journey;
    private final Clock clock;

    @Autowired
    public BriefingService(
            SchedulingOverviewService overview,
            CandidateRepository candidates,
            ApplicationRepository applications,
            JobRepository jobs,
            InterviewRepository interviews,
            ScreeningAnswerRepository answers,
            KnockoutQuestionRepository questions,
            RecruiterUserRepository users,
            JourneyQueries journey) {
        this(overview, candidates, applications, jobs, interviews, answers, questions, users, journey,
                Clock.systemDefaultZone());
    }

    /** For tests: the same service on a fixed clock. */
    BriefingService(
            SchedulingOverviewService overview,
            CandidateRepository candidates,
            ApplicationRepository applications,
            JobRepository jobs,
            InterviewRepository interviews,
            ScreeningAnswerRepository answers,
            KnockoutQuestionRepository questions,
            RecruiterUserRepository users,
            JourneyQueries journey,
            Clock clock) {
        this.overview = overview;
        this.candidates = candidates;
        this.applications = applications;
        this.jobs = jobs;
        this.interviews = interviews;
        this.answers = answers;
        this.questions = questions;
        this.users = users;
        this.journey = journey;
        this.clock = clock;
    }

    /**
     * The briefing for what is on the recruiter's screen. An open application is the most specific
     * thing a screen can show, so it wins; then a candidate; then a job; with none of them, the day.
     *
     * @throws EntityNotFoundException when an id given is not on file (404)
     */
    public Briefing forContext(UUID candidateId, UUID applicationId, UUID jobId) {
        if (applicationId != null) {
            return forApplication(applicationId);
        }
        if (candidateId != null) {
            return forCandidate(candidateId, null);
        }
        if (jobId != null) {
            return forJob(jobId);
        }
        return forDay();
    }

    // ------------------------------------------------------------------ the day

    /** Every job's interviews: the four lanes of the interview desk, today, and who is loaded. */
    public Briefing forDay() {
        Overview o = overview.overview();
        Draft d = new Draft();
        d.about = "Interviews across every job";
        d.section("At a glance", List.of(
                Words.count(o.needsOutcome().size(), "interview needs", "interviews need") + " an outcome recorded.",
                Words.count(o.awaitingCandidate().size(), "candidate has", "candidates have")
                        + " been offered times and not picked one.",
                Words.count(o.noShows().size(), "interview is", "interviews are") + " marked a no-show.",
                Words.count(o.today().size(), "interview is", "interviews are") + " booked for the rest of today."));
        lanes(d, o, item -> true);

        List<String> today = listed(o.today(), item -> Words.clock(item.scheduledAt()) + ": "
                + d.names.candidate(item.candidateName()) + ", " + item.jobTitle() + panel(d, item.interviewers()) + ".");
        d.section("Today", today);

        List<InterviewerLoad> loaded = o.load().stream().filter(l -> l.next7Days() > 0).toList();
        d.section("Interviews booked per interviewer, next 7 days", loaded.isEmpty()
                ? List.of("Nobody has an interview booked in the next 7 days.")
                : listed(loaded, l -> d.names.staff(l.name()) + ": " + l.next7Days() + "."));

        d.gaps.add("These lists cover every recruiter's interviews, not only yours.");
        d.link("Open the interview desk", DESK);
        return d.build(Briefing.DAY, "on the interview desk");
    }

    // ------------------------------------------------------------------ a candidate

    /** A candidate seen through one application: the one open in the drawer. */
    public Briefing forApplication(UUID applicationId) {
        Application a = applications.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("No application " + applicationId));
        return forCandidate(a.getCandidateId(), a.getId());
    }

    /**
     * A candidate: who they are, each application and its interviews, screening, and their texts
     * and emails.
     *
     * @param focusApplicationId the application on screen, described first and in most detail; or null
     */
    public Briefing forCandidate(UUID candidateId, UUID focusApplicationId) {
        Candidate c = candidates.findById(candidateId)
                .orElseThrow(() -> new EntityNotFoundException("No candidate " + candidateId));
        Draft d = new Draft();
        String who = d.names.candidate(c.getName());

        List<Application> apps = new ArrayList<>(applications.findByCandidateId(c.getId()));
        apps.sort(Comparator
                .comparing((Application a) -> !a.getId().equals(focusApplicationId))
                .thenComparing(Application::getAppliedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        Application focus = apps.stream().filter(a -> a.getId().equals(focusApplicationId)).findFirst().orElse(null);
        Map<UUID, Job> jobById = byId(jobs.findAllById(apps.stream().map(Application::getJobId).collect(Collectors.toSet())), Job::getId);
        d.about = focus == null ? who : who + ", applying for " + title(jobById.get(focus.getJobId()));

        List<String> about = new ArrayList<>();
        if (notBlank(c.getHeadline())) {
            about.add("Headline: " + Words.sentence(d.names.hideIn(Words.scrub(c.getHeadline()))));
        }
        if (notBlank(c.getLocation())) {
            about.add("Location: " + Words.sentence(c.getLocation()));
        }
        if (c.getYearsExperience() != null) {
            about.add("Experience: " + Words.years(c.getYearsExperience()) + " years.");
        }
        if (notBlank(c.getSource())) {
            about.add("Came to us through: " + Words.sentence(Words.plain(c.getSource()).toLowerCase()));
        }
        about.add(Words.count(apps.size(), "application", "applications") + " on file.");
        d.section("About " + who, about);

        List<Application> described = apps.subList(0, Math.min(apps.size(), MAX_APPLICATIONS));
        Map<UUID, List<Interview>> ivsByApp = interviews
                .findByApplicationIdIn(described.stream().map(Application::getId).toList()).stream()
                .collect(Collectors.groupingBy(Interview::getApplicationId));
        boolean anyInterview = false;
        boolean anyFeedback = false;
        for (Application a : described) {
            Job job = jobById.get(a.getJobId());
            String jobTitle = title(job);
            List<Interview> ivs = new ArrayList<>(ivsByApp.getOrDefault(a.getId(), List.of()));
            ivs.sort(Comparator.comparing(this::latestTime, Comparator.nullsLast(Comparator.reverseOrder())));
            anyInterview |= !ivs.isEmpty();

            List<String> lines = new ArrayList<>();
            lines.add("Stage: " + Words.stage(a.getStage()) + ". Applied " + Words.day(a.getAppliedAt())
                    + (a.getUpdatedAt() != null ? ", last changed " + Words.day(a.getUpdatedAt()) : "") + ".");
            if (a.getFitScore() != null) {
                lines.add("Fit score: " + a.getFitScore() + ".");
            }
            lines.add("Screening: " + (a.getKnockoutPassed() == null ? "no result recorded"
                    : a.getKnockoutPassed() ? "passed" : "not passed") + ".");
            if (notBlank(a.getRejectionReason())) {
                lines.add("Reason recorded for not going ahead: " + Words.sentence(d.names.hideIn(Words.scrub(a.getRejectionReason()))));
            }
            for (Interview iv : ivs) {
                lines.add(interviewLine(d, iv));
                if (iv.getScore() != null || notBlank(iv.getRecommendation()) || notBlank(iv.getSummary())) {
                    anyFeedback = true;
                    lines.add(feedbackLine(d, iv));
                }
            }
            // The screening answers: they are what a screening result is explained by.
            List<String> failed = new ArrayList<>();
            Map<UUID, KnockoutQuestion> asked = byId(questions.findByJobIdOrderByOrdinal(a.getJobId()), KnockoutQuestion::getId);
            for (ScreeningAnswer ans : answers.findByApplicationId(a.getId())) {
                KnockoutQuestion q = asked.get(ans.getQuestionId());
                String prompt = q == null ? "a question no longer on the job" : "\"" + q.getPrompt() + "\"";
                String said = "\"" + d.names.hideIn(Words.quote(ans.getAnswer())) + "\"";
                lines.add("Screening answer to " + prompt + ": " + said
                        + (Boolean.FALSE.equals(ans.getPassed()) ? ", which does not meet the requirement." : "."));
                if (Boolean.FALSE.equals(ans.getPassed())) {
                    failed.add("the answer to " + prompt + " was " + said);
                }
            }
            d.section("Application for " + jobTitle, lines);
            applicationWhys(d, who, a, jobTitle, ivs, failed);
        }
        if (apps.size() > described.size()) {
            d.gaps.add("Only the latest " + described.size() + " of " + apps.size()
                    + " applications are described here; the rest are on the candidate's record.");
        }

        messages(d, who, journey.timeline(c.getId()));

        if (anyInterview && !anyFeedback) {
            d.gaps.add("No interview feedback is recorded for these interviews.");
        }
        d.gaps.add("Phone numbers and email addresses are left out here; they are on the candidate's record.");
        d.gaps.add("The careers-chat transcript is not included here; it is on the candidate's record.");

        d.link("Open " + who + "'s record", "/candidates/" + c.getId());
        if (focus != null) {
            d.link("Open the pipeline for " + title(jobById.get(focus.getJobId())), "/pipeline?job=" + focus.getJobId());
        }
        if (anyInterview) {
            d.link("Open the interview desk", DESK);
        }
        return d.build(Briefing.CANDIDATE, "on the candidate's record");
    }

    // ------------------------------------------------------------------ a job

    /** A job: the requisition, who is at which stage, its interviews, and what of it is waiting on the desk. */
    public Briefing forJob(UUID jobId) {
        Job j = jobs.findById(jobId).orElseThrow(() -> new EntityNotFoundException("No job " + jobId));
        Draft d = new Draft();
        String jobTitle = title(j);
        d.about = jobTitle;

        List<String> req = new ArrayList<>();
        req.add("Status: " + Words.plain(j.getStatus()) + ".");
        if (notBlank(j.getLocation())) {
            req.add("Location: " + j.getLocation()
                    + (notBlank(j.getWorkMode()) ? " (" + Words.plain(j.getWorkMode()).toLowerCase() + ")" : "") + ".");
        }
        req.add("Openings: " + j.getOpenings() + ".");
        if (j.getPostedAt() != null) {
            req.add("Posted " + Words.day(j.getPostedAt()) + ".");
        }
        staffLine(d, "Recruiter", j.getRecruiterId()).ifPresent(req::add);
        staffLine(d, "Hiring manager", j.getHiringManagerId()).ifPresent(req::add);
        d.section("The requisition", req);

        List<Application> apps = applications.findByJobId(jobId);
        Map<String, Long> byStage = apps.stream().collect(Collectors.groupingBy(
                a -> a.getStage() == null ? "" : a.getStage(), LinkedHashMap::new, Collectors.counting()));
        List<String> stages = new ArrayList<>();
        stages.add(Words.count(apps.size(), "applicant", "applicants") + " in all.");
        for (String s : JobService.STAGES) {
            if (byStage.containsKey(s)) {
                stages.add(Words.stage(s) + ": " + byStage.get(s) + ".");
            }
        }
        byStage.keySet().stream().filter(s -> !JobService.STAGES.contains(s))
                .forEach(s -> stages.add(Words.stage(s) + ": " + byStage.get(s) + "."));
        if (j.getOpenings() > 0) {
            stages.add("Openings filled: " + byStage.getOrDefault("HIRED", 0L) + " of " + j.getOpenings() + ".");
        }
        d.section("Applicants by stage", stages);

        Set<UUID> appIds = apps.stream().map(Application::getId).collect(Collectors.toSet());
        Map<UUID, Application> appById = byId(apps, Application::getId);
        List<Interview> ivs = appIds.isEmpty() ? List.of() : interviews.findByApplicationIdIn(appIds);
        List<String> ivLines = new ArrayList<>();
        Map<String, Long> byStatus = ivs.stream().collect(Collectors.groupingBy(
                iv -> iv.getStatus() == null ? "" : iv.getStatus(), LinkedHashMap::new, Collectors.counting()));
        byStatus.forEach((status, n) -> ivLines.add(Words.upperFirst(Words.interviewStatus(status)) + ": " + n + "."));
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Interview> upcoming = ivs.stream()
                .filter(iv -> "SCHEDULED".equals(iv.getStatus()) && iv.getScheduledAt() != null && iv.getScheduledAt().isAfter(now))
                .sorted(Comparator.comparing(Interview::getScheduledAt)).toList();
        Map<UUID, Candidate> people = byId(candidates.findAllById(upcoming.stream().limit(MAX_ITEMS)
                .map(iv -> appById.get(iv.getApplicationId())).filter(Objects::nonNull)
                .map(Application::getCandidateId).collect(Collectors.toSet())), Candidate::getId);
        ivLines.addAll(listed(upcoming, iv -> {
            Application a = appById.get(iv.getApplicationId());
            Candidate c = a == null ? null : people.get(a.getCandidateId());
            return "Coming up, " + Words.when(iv.getScheduledAt()) + ": " + d.names.candidate(c == null ? null : c.getName())
                    + ", " + Words.interviewType(iv.getType()) + panel(d, names(iv.getInterviewers())) + ".";
        }));
        d.section("Interviews", ivLines.isEmpty() ? List.of("No interviews are on file for this job.") : ivLines);

        lanes(d, overview.overview(), item -> appIds.contains(item.applicationId()));

        if (!ivs.isEmpty() && ivs.stream().noneMatch(iv -> iv.getScore() != null || notBlank(iv.getRecommendation()) || notBlank(iv.getSummary()))) {
            d.gaps.add("No interview feedback is recorded for this job's interviews.");
        }
        d.link("Open the job", "/jobs/" + j.getId());
        d.link("Open the pipeline", "/pipeline?job=" + j.getId());
        d.link("Open the interview desk", DESK);
        return d.build(Briefing.JOB, "on the interview desk");
    }

    // ------------------------------------------------------------------ the desk's lanes

    /**
     * The interview desk's lanes, as sections and reasons. The lanes themselves are the overview's:
     * needs an outcome (the time has passed and nothing is recorded), awaiting the candidate (times
     * offered, none picked) and no-shows.
     */
    private void lanes(Draft d, Overview o, Predicate<AttentionItem> in) {
        List<AttentionItem> outcome = o.needsOutcome().stream().filter(in).toList();
        List<AttentionItem> awaiting = o.awaitingCandidate().stream().filter(in).toList();
        List<AttentionItem> noShows = o.noShows().stream().filter(in).toList();

        d.section("Needs an outcome", listed(outcome, item -> d.names.candidate(item.candidateName()) + ", "
                + item.jobTitle() + ": booked for " + Words.when(item.scheduledAt()) + ", "
                + Words.span(item.waitingHours()) + " ago."));
        d.section("Waiting for the candidate to pick a time", listed(awaiting, item -> d.names.candidate(item.candidateName())
                + ", " + item.jobTitle() + ": times offered " + Words.span(item.waitingHours()) + " ago."));
        d.section("No-shows", listed(noShows, item -> d.names.candidate(item.candidateName()) + ", "
                + item.jobTitle() + ": missed the interview booked for " + Words.when(item.scheduledAt()) + "."));

        for (AttentionItem item : outcome) {
            d.why(d.names.candidate(item.candidateName()) + "'s interview for " + item.jobTitle() + " is under Needs an outcome",
                    "it was booked for " + Words.when(item.scheduledAt()) + " and nobody has recorded whether it took place",
                    "Open it on the interview desk and mark it completed or a no-show.");
        }
        for (AttentionItem item : awaiting) {
            d.why(d.names.candidate(item.candidateName()) + " (" + item.jobTitle() + ") is under Awaiting candidate",
                    "times were offered " + Words.span(item.waitingHours()) + " ago and none has been picked",
                    "Open the scheduling drawer on the interview desk to offer other times or book one for them.");
        }
        for (AttentionItem item : noShows) {
            d.why(d.names.candidate(item.candidateName()) + " (" + item.jobTitle() + ") is under No-shows",
                    "the interview booked for " + Words.when(item.scheduledAt()) + " was marked a no-show", null);
        }
    }

    // ------------------------------------------------------------------ one application

    private void applicationWhys(Draft d, String who, Application a, String jobTitle, List<Interview> ivs,
            List<String> failed) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Interview iv : ivs) {
            String type = Words.interviewType(iv.getType());
            switch (iv.getStatus() == null ? "" : iv.getStatus()) {
                case "SLOTS_PROPOSED" -> d.why("No time is set for the " + type + " for " + jobTitle,
                        "times were offered " + Words.span(hoursSince(iv.getCreatedAt(), now)) + " ago and " + who
                                + " has not picked one",
                        "Open the scheduling drawer on the interview desk to offer other times or book one for them.");
                case "REQUESTED" -> d.why("No times are offered yet for the " + type + " for " + jobTitle,
                        "the interview was requested and no times have been offered", null);
                case "SCHEDULED" -> {
                    if (iv.getScheduledAt() != null && iv.getScheduledAt().isBefore(now)) {
                        d.why("The " + type + " for " + jobTitle + " has no outcome recorded",
                                "it was booked for " + Words.when(iv.getScheduledAt())
                                        + " and nobody has recorded whether it took place",
                                "Open it on the interview desk and mark it completed or a no-show.");
                    } else {
                        String via = Words.bookedVia(iv.getBookedVia());
                        d.why("The " + type + " for " + jobTitle + " is booked for " + Words.when(iv.getScheduledAt()),
                                via != null ? via : "the records do not say who set the time", null);
                    }
                }
                case "NO_SHOW" -> d.why(who + " missed the " + type + " for " + jobTitle,
                        "it was booked for " + Words.when(iv.getScheduledAt()) + " and was marked a no-show", null);
                default -> {
                    // Completed and cancelled interviews are history, not something waiting.
                }
            }
        }
        if (Boolean.FALSE.equals(a.getKnockoutPassed())) {
            d.why(who + " did not pass screening for " + jobTitle,
                    !failed.isEmpty() ? String.join("; ", failed)
                            : "no screening answers are recorded to say which question",
                    null);
        }
        if ("REJECTED".equals(a.getStage()) || "WITHDRAWN".equals(a.getStage())) {
            d.why("The application for " + jobTitle + " is closed: " + Words.stage(a.getStage()).toLowerCase(),
                    notBlank(a.getRejectionReason()) ? Words.lowerFirst(d.names.hideIn(Words.scrub(a.getRejectionReason())))
                            : "no reason is recorded",
                    null);
        }
    }

    private String interviewLine(Draft d, Interview iv) {
        StringBuilder s = new StringBuilder(Words.upperFirst(Words.interviewType(iv.getType())))
                .append(": ").append(Words.interviewStatus(iv.getStatus()));
        if (iv.getScheduledAt() != null) {
            s.append(", ").append(Words.when(iv.getScheduledAt()));
        }
        String via = Words.bookedVia(iv.getBookedVia());
        if (via != null && iv.getScheduledAt() != null) {
            s.append("; ").append(via);
        }
        s.append(panel(d, names(iv.getInterviewers())).replace(", with ", "; panel "));
        if (iv.getRescheduleCount() > 0) {
            s.append("; rescheduled ").append(Words.count(iv.getRescheduleCount(), "time", "times"));
        }
        return s.append('.').toString();
    }

    private String feedbackLine(Draft d, Interview iv) {
        List<String> parts = new ArrayList<>();
        if (notBlank(iv.getRecommendation())) {
            parts.add("recommendation " + Words.plain(iv.getRecommendation()).toLowerCase());
        }
        if (iv.getScore() != null) {
            parts.add("score " + iv.getScore());
        }
        String head = "Feedback on the " + Words.interviewType(iv.getType()) + ": "
                + (parts.isEmpty() ? "notes only" : String.join(", ", parts)) + ".";
        return notBlank(iv.getSummary())
                ? head + " Notes: " + Words.sentence(d.names.hideIn(Words.scrub(iv.getSummary())))
                : head;
    }

    // ------------------------------------------------------------------ texts and emails

    private void messages(Draft d, String who, Timeline t) {
        List<String> lines = new ArrayList<>();
        lines.add(t.canBeTexted() ? "Texts can be sent to " + who + "." : "Texts to " + who + " are held back.");
        if (!t.journeyOn()) {
            lines.add("The candidate journey is switched off, so nothing is sent unprompted.");
        }
        List<MessageRow> recent = t.messages().stream()
                .sorted(Comparator.comparing(MessageRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (recent.isEmpty()) {
            lines.add("No texts or emails are on file.");
        }
        recent.stream().limit(MAX_MESSAGES).forEach(m -> lines.add(messageLine(d, who, m)));
        if (recent.size() > MAX_MESSAGES) {
            lines.add("And " + Words.count(recent.size() - MAX_MESSAGES, "earlier message", "earlier messages") + ".");
        }
        d.section("Texts and emails, most recent first", lines);

        if (!t.canBeTexted()) {
            d.why("Texts to " + who + " are held back",
                    notBlank(t.whyNot()) ? Words.lowerFirst(Words.scrub(t.whyNot())) : "the records do not say why", null);
        }
        if (!t.journeyOn()) {
            d.why("Nothing is sent to candidates unprompted", "the candidate journey is switched off", null);
        }
        if (notBlank(t.note())) {
            d.why("Texts from " + who + "'s number are not acted on", Words.lowerFirst(Words.scrub(t.note())), null);
        }
        recent.stream().limit(MAX_MESSAGES)
                .filter(m -> CandidateMessage.FAILED.equals(m.status()) || CandidateMessage.SUPPRESSED.equals(m.status()))
                .forEach(m -> d.why("The " + channel(m.channel()) + (notBlank(m.sentWhen()) ? " sent " + Words.lowerFirst(m.sentWhen()) : "")
                                + " on " + Words.day(m.createdAt()) + " did not go out",
                        notBlank(m.reason()) ? Words.lowerFirst(Words.scrub(m.reason())) : "the records do not say why", null));
    }

    private String messageLine(Draft d, String who, MessageRow m) {
        String when = Words.when(m.sentAt() != null ? m.sentAt() : m.createdAt());
        if (CandidateMessage.INBOUND.equals(m.direction())) {
            return when + ", " + channel(m.channel()) + " from " + who + ": \"" + d.names.hideIn(Words.quote(m.text())) + "\".";
        }
        String what = notBlank(m.sentWhen()) ? Words.lowerFirst(m.sentWhen())
                : notBlank(m.templateName()) ? "\"" + m.templateName() + "\"" : "a message from the team";
        String status = notBlank(m.statusText()) ? m.statusText().toLowerCase() : Words.plain(m.status()).toLowerCase();
        boolean wentOut = CandidateMessage.SENT.equals(m.status());
        return when + ", " + channel(m.channel()) + " " + what + ": " + status
                + (!wentOut && notBlank(m.reason()) ? " (" + Words.scrub(m.reason()) + ")" : "") + ".";
    }

    // ------------------------------------------------------------------ helpers

    private java.util.Optional<String> staffLine(Draft d, String role, UUID userId) {
        if (userId == null) {
            return java.util.Optional.empty();
        }
        return users.findById(userId).map(RecruiterUser::getName).map(n -> role + ": " + d.names.staff(n) + ".");
    }

    /** ", with [S1] and [S2]", or nothing when nobody is named. */
    private static String panel(Draft d, List<String> names) {
        List<String> tokens = names.stream().map(d.names::staff).toList();
        return tokens.isEmpty() ? "" : ", with " + Words.and(tokens);
    }

    /** The names in an interview's comma-separated interviewer list. */
    private static List<String> names(String commaList) {
        return commaList == null || commaList.isBlank() ? List.of()
                : Arrays.stream(commaList.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Up to {@link #MAX_ITEMS} items as lines, and a count of the rest. */
    private static <T> List<String> listed(List<T> items, Function<T, String> line) {
        List<String> out = new ArrayList<>(items.stream().limit(MAX_ITEMS).map(line).toList());
        if (items.size() > MAX_ITEMS) {
            out.add("And " + (items.size() - MAX_ITEMS) + " more.");
        }
        return out;
    }

    private OffsetDateTime latestTime(Interview iv) {
        return iv.getScheduledAt() != null ? iv.getScheduledAt() : iv.getCreatedAt();
    }

    private static long hoursSince(OffsetDateTime then, OffsetDateTime now) {
        return then == null ? 0 : Math.max(0, Duration.between(then, now).toHours());
    }

    private static String channel(String code) {
        return CandidateMessage.EMAIL.equals(code) ? "email" : "text";
    }

    private static String title(Job j) {
        if (j == null) {
            return "a job no longer on file";
        }
        return j.getTitle() + (notBlank(j.getJobNo()) ? " (" + j.getJobNo() + ")" : "");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static <T> Map<UUID, T> byId(Iterable<T> items, Function<T, UUID> id) {
        Map<UUID, T> out = new LinkedHashMap<>();
        items.forEach(t -> out.put(id.apply(t), t));
        return out;
    }

    /** A briefing being put together. */
    private static final class Draft {
        final Placeholders names = new Placeholders();
        final List<Section> sections = new ArrayList<>();
        final List<Why> whys = new ArrayList<>();
        final List<String> gaps = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        String about;
        int whysLeftOut;

        /** A section, unless it has nothing to say. */
        void section(String title, List<String> lines) {
            if (!lines.isEmpty()) {
                sections.add(new Section(title, List.copyOf(lines)));
            }
        }

        void why(String what, String because, String next) {
            // The same reason twice (two texts on one day that failed alike) is said once.
            if (whys.stream().anyMatch(w -> w.what().equals(what) && w.because().equals(because))) {
                return;
            }
            if (whys.size() < MAX_WHYS) {
                whys.add(new Why(what, because, next));
            } else {
                whysLeftOut++;
            }
        }

        void link(String label, String path) {
            links.add(new Link(label, path));
        }

        /** @param whereTheRestIs where a recruiter finds the reasons left out, for example "on the interview desk" */
        Briefing build(String scope, String whereTheRestIs) {
            List<String> allGaps = new ArrayList<>(gaps);
            if (whysLeftOut > 0) {
                allGaps.add(0, Words.count(whysLeftOut, "more item like these is", "more items like these are")
                        + " left out of the reasons here; they are " + whereTheRestIs + ".");
            }
            return new Briefing(scope, about, List.copyOf(sections), List.copyOf(whys), List.copyOf(allGaps),
                    names.people(), List.copyOf(links));
        }
    }
}
