package com.taportal.api;

import com.taportal.domain.comms.SmsTemplate;
import com.taportal.domain.comms.SmsTemplateRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** SMS templates for the candidate journey (Admin -> Communications). */
@RestController
@RequestMapping("/v1/sms-templates")
public class SmsTemplateController {

    public record SmsDto(UUID id, String name, String category, String body, String status,
            java.time.OffsetDateTime createdAt, java.time.OffsetDateTime updatedAt) {}

    public record SmsSave(String name, String category, String body, String status) {}

    private final SmsTemplateRepository repository;

    public SmsTemplateController(SmsTemplateRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SmsDto> list() {
        return repository.findByOrderByNameAsc().stream().map(SmsTemplateController::toDto).toList();
    }

    @GetMapping("/{id}")
    public SmsDto get(@PathVariable UUID id) {
        return toDto(load(id));
    }

    @PostMapping
    public SmsDto create(@RequestBody SmsSave req) {
        SmsTemplate t = new SmsTemplate();
        apply(t, req);
        return toDto(repository.save(t));
    }

    @PutMapping("/{id}")
    public SmsDto update(@PathVariable UUID id, @RequestBody SmsSave req) {
        SmsTemplate t = load(id);
        apply(t, req);
        return toDto(repository.save(t));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        repository.deleteById(id);
    }

    private SmsTemplate load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    }

    private static void apply(SmsTemplate t, SmsSave req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A template needs a name.");
        }
        if (req.body() == null || req.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The message text is empty.");
        }
        t.setName(req.name().trim());
        t.setCategory(req.category() == null || req.category().isBlank() ? "Candidate journey" : req.category().trim());
        t.setBody(req.body());
        t.setStatus(req.status() == null ? "ACTIVE" : req.status());
    }

    private static SmsDto toDto(SmsTemplate t) {
        return new SmsDto(t.getId(), t.getName(), t.getCategory(), t.getBody(), t.getStatus(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
