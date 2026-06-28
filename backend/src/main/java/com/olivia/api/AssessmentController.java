package com.olivia.api;

import com.olivia.api.AssessmentDtos.AssessmentResponse;
import com.olivia.domain.assessment.AssessmentService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping
    public List<AssessmentResponse> list(@RequestParam UUID applicationId) {
        return assessmentService.listByApplication(applicationId);
    }
}
