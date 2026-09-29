package com.taportal.domain.journey;

import com.taportal.domain.application.Application;
import com.taportal.domain.candidate.Candidate;
import com.taportal.domain.interview.Interview;
import com.taportal.domain.job.Job;
import com.taportal.domain.recruiter.RecruiterUser;
import com.taportal.domain.recruiter.RecruiterUserRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The values a journey message can carry — one vocabulary for text and
 * email. A value that is not known is left out, so the renderer reports the
 * field as missing instead of sending something made up.
 */
@Service
public class JourneyValues {

    private final RecruiterUserRepository people;
    private final String publicBaseUrl;
    private final String companyName;

    public JourneyValues(
            RecruiterUserRepository people,
            @Value("${app.public-base-url:http://localhost:5177}") String publicBaseUrl,
            @Value("${app.company-name:Bank of America Careers}") String companyName) {
        this.people = people;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.companyName = companyName;
    }

    /** One field and what it holds, for the screens that offer fields to insert. */
    public record Field(String name, String holds) {
    }

    public static final List<Field> FIELDS = List.of(
            new Field("first_name", "The candidate's first name"),
            new Field("candidate_name", "The candidate's full name"),
            new Field("job_title", "The role applied for"),
            new Field("company_name", "The name messages are sent under"),
            new Field("status_summary", "Where each application stands, in words"),
            new Field("slot_options", "The times offered, numbered from 1"),
            new Field("slot_choices", "The replies that pick a time, for example 1, 2 or 3"),
            new Field("interview_time", "When the interview is, in Eastern Time"),
            new Field("interview_type", "The kind of interview"),
            new Field("interviewers", "Who the candidate will meet"),
            new Field("meeting_link", "The link to join the interview"),
            new Field("link", "The page where the candidate picks or changes a time"),
            new Field("recruiter_name", "The recruiter for the role"),
            new Field("sender_name", "Who the message is signed by"),
            new Field("reason", "Why a change could not be made"));

    public Map<String, String> of(Candidate candidate, Application application, Job job, Interview interview) {
        Map<String, String> v = new HashMap<>();
        v.put("company_name", companyName);
        v.put("sender_name", companyName);
        if (candidate != null) {
            put(v, "candidate_name", known(candidate.getName()));
            v.put("first_name", firstName(candidate.getName()));
        } else {
            v.put("first_name", "there");
        }
        if (job != null) {
            put(v, "job_title", job.getTitle());
            if (job.getRecruiterId() != null) {
                people.findById(job.getRecruiterId()).map(RecruiterUser::getName).ifPresent(name -> {
                    v.put("recruiter_name", name);
                    v.put("sender_name", name + ", " + companyName);
                });
            }
        }
        if (interview != null) {
            put(v, "interview_time", JourneyTimes.when(interview.getScheduledAt()));
            v.put("interview_type", typeInWords(interview.getType()));
            v.put("interviewers", names(interview.getInterviewers()));
            v.put("link", publicBaseUrl + "/schedule/" + interview.getId());
            // Until a meeting exists, the page that shows the interview is where to go.
            v.put("meeting_link", interview.getMeetingLink() != null && !interview.getMeetingLink().isBlank()
                    ? interview.getMeetingLink()
                    : publicBaseUrl + "/schedule/" + interview.getId());
        }
        return v;
    }

    /** Adds the times offered, and the replies that pick one. */
    public static void offer(Map<String, String> values, List<OffsetDateTime> times) {
        if (!times.isEmpty()) {
            values.put("slot_options", JourneyTimes.numbered(times));
            values.put("slot_choices", JourneyTimes.choices(times.size()));
        }
    }

    static String firstName(String name) {
        String n = known(name);
        if (n == null) {
            return "there";
        }
        return n.trim().split("\\s+")[0];
    }

    /** "David Okafor, Marcus Bell" reads "David Okafor and Marcus Bell". */
    static String names(String csv) {
        List<String> names = csv == null ? List.of()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (names.isEmpty()) {
            return "the hiring team";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    static String typeInWords(String type) {
        if (type == null || type.isBlank()) {
            return "interview";
        }
        return switch (type) {
            case "AI_PHONE_SCREEN" -> "phone screen";
            case "RECRUITER_SCREEN" -> "recruiter screen";
            case "AI_INTERVIEW", "LIVE_VIDEO" -> "video interview";
            case "ONE_WAY_VIDEO" -> "recorded video interview";
            case "PANEL" -> "panel interview";
            case "ONSITE" -> "on-site interview";
            // A round of the job's interview plan carries the name it was given.
            default -> type.contains("_") ? type.replace('_', ' ').toLowerCase() : type;
        };
    }

    private static String known(String name) {
        return name == null || name.isBlank() || "Unknown".equalsIgnoreCase(name.trim()) ? null : name.trim();
    }

    private static void put(Map<String, String> v, String field, String value) {
        if (value != null && !value.isBlank()) {
            v.put(field, value);
        }
    }
}
