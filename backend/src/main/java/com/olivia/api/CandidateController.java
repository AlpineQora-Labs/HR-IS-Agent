package com.olivia.api;

import com.olivia.api.CandidateDtos.CandidateDetail;
import com.olivia.api.CandidateDtos.CandidateSummary;
import com.olivia.domain.candidate.CandidateService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/candidates")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public List<CandidateSummary> list() {
        return candidateService.list();
    }

    @GetMapping("/{id}")
    public CandidateDetail get(@PathVariable UUID id) {
        return candidateService.get(id);
    }
}
