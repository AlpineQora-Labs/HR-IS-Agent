package com.taportal.api;

import com.taportal.domain.engagement.EventTemplateService;
import com.taportal.domain.engagement.EventTemplateService.TemplateDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Event templates: what a kind of event always has. The rules are in {@link EventTemplateService}. */
@RestController
public class EventTemplateController {

    private final EventTemplateService service;

    public EventTemplateController(EventTemplateService service) {
        this.service = service;
    }

    @GetMapping("/v1/event-templates")
    public List<TemplateDto> list() {
        return service.list();
    }

    @PostMapping("/v1/event-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDto create(@RequestBody TemplateDto request) {
        return service.create(request);
    }

    @PutMapping("/v1/event-templates/{id}")
    public TemplateDto update(@PathVariable UUID id, @RequestBody TemplateDto request) {
        return service.update(id, request);
    }

    @DeleteMapping("/v1/event-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
