package com.olivia.api;

import com.olivia.api.EngagementDtos.Campaign;
import com.olivia.api.EngagementDtos.EventRow;
import com.olivia.api.EngagementDtos.Referral;
import com.olivia.api.EngagementDtos.SurveyRow;
import com.olivia.domain.engagement.EngagementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Engagement: nurture campaigns, referrals, events, surveys. */
@RestController
@RequestMapping("/v1")
public class EngagementController {

    private final EngagementService engagementService;

    public EngagementController(EngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @GetMapping("/campaigns")
    public List<Campaign> campaigns() {
        return engagementService.campaigns();
    }

    @GetMapping("/referrals")
    public List<Referral> referrals() {
        return engagementService.referrals();
    }

    @GetMapping("/events")
    public List<EventRow> events() {
        return engagementService.events();
    }

    @GetMapping("/surveys")
    public List<SurveyRow> surveys() {
        return engagementService.surveys();
    }
}
