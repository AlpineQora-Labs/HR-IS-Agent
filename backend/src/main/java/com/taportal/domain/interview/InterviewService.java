package com.taportal.domain.interview;

import com.taportal.api.InterviewDtos.CreateInterviewRequest;
import com.taportal.api.InterviewDtos.InterviewResponse;
import com.taportal.api.InterviewDtos.ScheduleInterviewRequest;
import com.taportal.api.InterviewDtos.SlotResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewSlotRepository slotRepository;

    public InterviewService(InterviewRepository interviewRepository, InterviewSlotRepository slotRepository) {
        this.interviewRepository = interviewRepository;
        this.slotRepository = slotRepository;
    }

    public List<InterviewResponse> listByApplication(UUID applicationId) {
        return interviewRepository.findByApplicationId(applicationId).stream().map(InterviewService::toResponse).toList();
    }

    public List<SlotResponse> openSlots(UUID jobId) {
        return slotRepository.findByJobIdAndBookedFalse(jobId).stream().map(InterviewService::toSlotResponse).toList();
    }

    @Transactional
    public InterviewResponse create(CreateInterviewRequest request) {
        Interview interview = new Interview();
        interview.setApplicationId(request.applicationId());
        interview.setType(request.type());
        interview.setDurationMin(request.durationMin() != null ? request.durationMin() : 30);
        interview.setStatus("SCHEDULED");
        interview.setInterviewers(request.interviewers());
        return toResponse(interviewRepository.save(interview));
    }

    @Transactional
    public InterviewResponse schedule(UUID id, ScheduleInterviewRequest request) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Interview not found: " + id));
        InterviewSlot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + request.slotId()));

        interview.setSlotId(slot.getId());
        interview.setScheduledAt(request.scheduledAt() != null ? request.scheduledAt() : slot.getStartsAt());
        slot.setBooked(true);
        slotRepository.save(slot);
        return toResponse(interviewRepository.save(interview));
    }

    static InterviewResponse toResponse(Interview i) {
        return new InterviewResponse(
                i.getId(),
                i.getApplicationId(),
                i.getType(),
                i.getScheduledAt(),
                i.getDurationMin(),
                i.getStatus(),
                splitNames(i.getInterviewers()),
                i.getScore(),
                i.getRecommendation(),
                i.getSummary());
    }

    static SlotResponse toSlotResponse(InterviewSlot s) {
        return new SlotResponse(
                s.getId(),
                s.getJobId(),
                s.getInterviewerId(),
                null,
                s.getStartsAt(),
                s.getEndsAt(),
                s.isBooked());
    }

    private static List<String> splitNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
