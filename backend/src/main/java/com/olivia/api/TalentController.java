package com.olivia.api;

import com.olivia.api.TalentDtos.MatchRow;
import com.olivia.api.TalentDtos.MobilityRow;
import com.olivia.api.TalentDtos.Pool;
import com.olivia.api.TalentDtos.SourcingRow;
import com.olivia.domain.talent.MatchingService;
import com.olivia.domain.talent.TalentService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Talent intelligence: pools, internal mobility, matching, sourcing, reactivation. */
@RestController
@RequestMapping("/v1")
public class TalentController {

    private final TalentService talentService;
    private final MatchingService matchingService;

    public TalentController(TalentService talentService, MatchingService matchingService) {
        this.talentService = talentService;
        this.matchingService = matchingService;
    }

    @GetMapping("/pools")
    public List<Pool> pools() {
        return talentService.pools();
    }

    @GetMapping("/mobility")
    public List<MobilityRow> mobility() {
        return talentService.mobility();
    }

    @GetMapping("/match")
    public List<MatchRow> match(@RequestParam(required = false) UUID jobId) {
        return matchingService.match(jobId);
    }

    @GetMapping("/sourcing")
    public List<SourcingRow> sourcing(@RequestParam(required = false) UUID jobId) {
        return matchingService.sourcing(jobId);
    }

    @GetMapping("/reactivation")
    public List<SourcingRow> reactivation() {
        return matchingService.reactivation();
    }
}
