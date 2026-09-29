package com.taportal.domain.application;

import com.taportal.api.ApplicationDtos.ApplicationRow;
import com.taportal.api.ApplicationDtos.PipelineColumn;
import com.taportal.api.ApplicationDtos.UpdateApplicationRequest;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.candidate.CandidateRepository;
import com.taportal.domain.events.ApplicationReceived;
import com.taportal.domain.events.ApplicationStageChanged;
import com.taportal.domain.interview.InterviewService;
import com.taportal.domain.job.Job;
import com.taportal.domain.job.JobRepository;
import com.taportal.domain.job.JobService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final JobRepository jobRepository;
    private final InterviewService interviewService;
    private final ApplicationEventPublisher events;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            CandidateRepository candidateRepository,
            JobRepository jobRepository,
            InterviewService interviewService,
            ApplicationEventPublisher events) {
        this.applicationRepository = applicationRepository;
        this.candidateRepository = candidateRepository;
        this.jobRepository = jobRepository;
        this.interviewService = interviewService;
        this.events = events;
    }

    /**
     * What a candidate gave when applying.
     *
     * @param phoneAsTyped     whatever was typed; it is kept as typed and, when it is a number a
     *                         text can reach, in one standard form as well
     * @param agreedToTexts    the candidate gave the number in answer to a question that said it
     *                         would be used to text them
     * @param phoneAnswer      what the candidate wrote in answer to that question, as they wrote
     *                         it: a number given with "please don't text me" is not agreement
     * @param screeningFollows screening questions come next; the application is announced as
     *                         received once they are passed, not before
     */
    public record NewApplication(
            UUID jobId, String name, String email, String phoneAsTyped, String language,
            String source, boolean agreedToTexts, boolean screeningFollows, String phoneAnswer) {

        public NewApplication(
                UUID jobId, String name, String email, String phoneAsTyped, String language,
                String source, boolean agreedToTexts, boolean screeningFollows) {
            this(jobId, name, email, phoneAsTyped, language, source, agreedToTexts, screeningFollows, phoneAsTyped);
        }
    }

    /**
     * A candidate applies. This is the one place an application comes into
     * being, whatever surface it came through.
     */
    @Transactional
    public Application receive(NewApplication in) {
        Candidate candidate = new Candidate();
        candidate.setName(in.name() == null || in.name().isBlank() ? "Unknown" : in.name().trim());
        candidate.setEmail(in.email() == null ? "" : in.email().trim());
        candidate.setPhone(in.phoneAsTyped());
        // Agreement counts only with a number it can apply to, and not when the answer refused it.
        if (in.agreedToTexts() && candidate.getPhoneE164() != null
                && !com.taportal.domain.messaging.TextAgreement.refused(in.phoneAnswer())) {
            candidate.setSmsConsentAt(java.time.OffsetDateTime.now());
        }
        candidate.setYearsExperience(java.math.BigDecimal.ZERO);
        candidate.setSource("CAREER_SITE");
        candidate.setPreferredLanguage(in.language() == null || in.language().isBlank() ? "en" : in.language());
        candidate.setLifecycle("ACTIVE");
        candidate = candidateRepository.save(candidate);

        Application app = new Application();
        app.setCandidateId(candidate.getId());
        app.setJobId(in.jobId());
        app.setStage("APPLIED");
        app.setSource(in.source());
        app = applicationRepository.save(app);
        if (!in.screeningFollows()) {
            events.publishEvent(new ApplicationReceived(app.getId()));
        }
        return app;
    }

    /** The candidate passed screening: the application is in, and they are told so. */
    @Transactional
    public void passedScreening(UUID applicationId) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        app.setStage("SCREENED");
        app.setKnockoutPassed(true);
        applicationRepository.save(app);
        events.publishEvent(new ApplicationReceived(applicationId));
    }

    /** The candidate did not pass screening. The conversation told them; nothing else is sent. */
    @Transactional
    public void failedScreening(UUID applicationId, String reason) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        app.setStage("REJECTED");
        app.setKnockoutPassed(false);
        app.setRejectionReason(reason);
        applicationRepository.save(app);
        interviewService.cancelForApplication(applicationId);
    }

    public List<ApplicationRow> list(UUID jobId, String stage) {
        List<Application> apps;
        if (jobId != null && stage != null) {
            apps = applicationRepository.findByJobIdAndStage(jobId, stage);
        } else if (jobId != null) {
            apps = applicationRepository.findByJobId(jobId);
        } else {
            apps = applicationRepository.findAll();
            if (stage != null) {
                apps = apps.stream().filter(a -> stage.equals(a.getStage())).toList();
            }
        }
        return apps.stream().map(this::toRow).toList();
    }

    /** Rows for a single candidate (used by CandidateService). */
    public List<ApplicationRow> listByCandidate(UUID candidateId) {
        return applicationRepository.findByCandidateId(candidateId).stream().map(this::toRow).toList();
    }

    public ApplicationRow get(UUID id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        return toRow(app);
    }

    @Transactional
    public ApplicationRow update(UUID id, UpdateApplicationRequest req) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (req.stage() != null) {
            if (!JobService.STAGES.contains(req.stage())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stage: " + req.stage());
            }
        }
        String before = app.getStage();
        boolean moved = req.stage() != null && !req.stage().equals(before);
        if (moved) {
            app.setStage(req.stage());
        }
        if (req.fitScore() != null) app.setFitScore(req.fitScore());
        if (req.rejectionReason() != null) app.setRejectionReason(req.rejectionReason());
        Application saved = applicationRepository.save(app);
        if (moved) {
            // An application that is closed takes its interview with it: nobody is
            // reminded of, or can book, an interview for an application no longer running.
            if ("REJECTED".equals(req.stage()) || "WITHDRAWN".equals(req.stage())) {
                interviewService.cancelForApplication(id);
            }
            events.publishEvent(new ApplicationStageChanged(id, before, req.stage()));
        }
        return toRow(saved);
    }

    public List<PipelineColumn> pipeline(UUID jobId) {
        List<Application> apps = (jobId != null)
                ? applicationRepository.findByJobId(jobId)
                : applicationRepository.findAll();
        Map<String, List<ApplicationRow>> byStage = apps.stream()
                .map(this::toRow)
                .collect(Collectors.groupingBy(ApplicationRow::stage));
        return JobService.STAGES.stream()
                .map(stage -> {
                    List<ApplicationRow> cards = byStage.getOrDefault(stage, List.of());
                    return new PipelineColumn(stage, cards.size(), cards);
                })
                .toList();
    }

    private ApplicationRow toRow(Application a) {
        String candidateName = candidateRepository.findById(a.getCandidateId())
                .map(Candidate::getName)
                .orElse(null);
        String jobTitle = jobRepository.findById(a.getJobId())
                .map(Job::getTitle)
                .orElse(null);
        return new ApplicationRow(
                a.getId(),
                a.getCandidateId(),
                candidateName,
                a.getJobId(),
                jobTitle,
                a.getStage(),
                a.getSource(),
                a.getFitScore(),
                a.getKnockoutPassed(),
                a.getAppliedAt(),
                a.getUpdatedAt());
    }
}
