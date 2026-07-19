package com.taportal.api;

import com.taportal.api.CampusDtos.RegisterRequest;
import com.taportal.api.CampusDtos.RegistrationRow;
import com.taportal.api.CampusDtos.SchoolRow;
import com.taportal.api.CampusDtos.TransitionRequest;
import com.taportal.domain.engagement.CampusService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Campus module: schools + event rosters (register, walk-in, check-in). */
@RestController
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @GetMapping("/v1/schools")
    public List<SchoolRow> schools() {
        return campusService.schools();
    }

    @GetMapping("/v1/events/{id}/registrations")
    public List<RegistrationRow> roster(@PathVariable UUID id) {
        return campusService.roster(id);
    }

    @PostMapping("/v1/events/{id}/registrations")
    public RegistrationRow register(@PathVariable UUID id, @Valid @RequestBody RegisterRequest request) {
        return campusService.register(id, request);
    }

    @PostMapping("/v1/event-registrations/{id}/transition")
    public RegistrationRow transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return campusService.transition(id, request.status());
    }
}
