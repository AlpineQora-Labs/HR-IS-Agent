package com.olivia.api;

import com.olivia.api.InterviewDtos.CreateInterviewRequest;
import com.olivia.api.InterviewDtos.InterviewResponse;
import com.olivia.api.InterviewDtos.ScheduleInterviewRequest;
import com.olivia.api.InterviewDtos.SlotResponse;
import com.olivia.domain.interview.InterviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/v1/interviews")
    public List<InterviewResponse> list(@RequestParam UUID applicationId) {
        return interviewService.listByApplication(applicationId);
    }

    @PostMapping("/v1/interviews")
    public InterviewResponse create(@Valid @RequestBody CreateInterviewRequest request) {
        return interviewService.create(request);
    }

    @PostMapping("/v1/interviews/{id}/schedule")
    public InterviewResponse schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleInterviewRequest request) {
        return interviewService.schedule(id, request);
    }

    @GetMapping("/v1/slots")
    public List<SlotResponse> slots(@RequestParam UUID jobId) {
        return interviewService.openSlots(jobId);
    }
}
