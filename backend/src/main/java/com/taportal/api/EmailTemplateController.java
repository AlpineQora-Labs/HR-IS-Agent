package com.taportal.api;

import com.taportal.domain.comms.EmailTemplate;
import com.taportal.domain.comms.EmailTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email-template CRUD backing the Communications component (lifted 1:1 from
 * teammate-voices). List, get, create, update, delete, duplicate.
 */
@RestController
public class EmailTemplateController {

    /**
     * @param templateKey set when the template words a point of the candidate journey
     * @param sentWhen    that point, in words; null otherwise
     */
    public record TemplateDto(
            UUID templateId, String name, String description, String category,
            String subject, String fromName, String bodyHtml, String status,
            boolean isDefault, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String templateKey, String sentWhen) {

        static TemplateDto of(EmailTemplate t) {
            return new TemplateDto(t.getId(), t.getName(), t.getDescription(), t.getCategory(),
                    t.getSubject(), t.getFromName(), t.getBodyHtml(), t.getStatus(),
                    t.isDefaultTemplate(), t.getCreatedAt(), t.getUpdatedAt(), t.getTemplateKey(),
                    com.taportal.domain.journey.JourneyPoint.ofTemplateKey(t.getTemplateKey())
                            .map(com.taportal.domain.journey.JourneyPoint::label).orElse(null));
        }
    }

    public record TemplateSave(
            String name, String description, String category, String subject,
            String fromName, String bodyHtml, String status) {
    }

    private final EmailTemplateRepository templates;
    private final com.taportal.domain.comms.TemplateRules rules;

    public EmailTemplateController(EmailTemplateRepository templates, com.taportal.domain.comms.TemplateRules rules) {
        this.templates = templates;
        this.rules = rules;
    }

    @GetMapping("/v1/email-templates")
    public List<TemplateDto> list() {
        return templates.findByOrderByUpdatedAtDesc().stream().map(TemplateDto::of).toList();
    }

    @GetMapping("/v1/email-templates/{id}")
    public TemplateDto get(@PathVariable UUID id) {
        return TemplateDto.of(load(id));
    }

    @PostMapping("/v1/email-templates")
    @Transactional
    public TemplateDto create(@RequestBody TemplateSave req) {
        EmailTemplate t = new EmailTemplate();
        apply(t, req);
        return TemplateDto.of(templates.save(t));
    }

    @PutMapping("/v1/email-templates/{id}")
    @Transactional
    public TemplateDto update(@PathVariable UUID id, @RequestBody TemplateSave req) {
        EmailTemplate t = load(id);
        rules.mayChange(t.getTemplateKey(), t.getName(), req.status() == null ? "DRAFT" : req.status(),
                req.bodyHtml(), false);
        apply(t, req);
        return TemplateDto.of(templates.save(t));
    }

    @DeleteMapping("/v1/email-templates/{id}")
    @Transactional
    public void delete(@PathVariable UUID id) {
        EmailTemplate t = load(id);
        rules.mayDelete(t.getTemplateKey(), t.getName());
        templates.delete(t);
    }

    /** Clone with a "(Copy)" suffix, always starting as DRAFT. */
    @PostMapping("/v1/email-templates/{id}/duplicate")
    @Transactional
    public TemplateDto duplicate(@PathVariable UUID id) {
        EmailTemplate src = load(id);
        EmailTemplate copy = new EmailTemplate();
        copy.setName(src.getName() + " (Copy)");
        copy.setDescription(src.getDescription());
        copy.setCategory(src.getCategory());
        copy.setSubject(src.getSubject());
        copy.setFromName(src.getFromName());
        copy.setBodyHtml(src.getBodyHtml());
        copy.setStatus("DRAFT");
        return TemplateDto.of(templates.save(copy));
    }

    private void apply(EmailTemplate t, TemplateSave req) {
        t.setName(req.name());
        t.setDescription(req.description());
        t.setCategory(req.category() == null ? "CUSTOM" : req.category());
        t.setSubject(req.subject());
        t.setFromName(req.fromName());
        t.setBodyHtml(req.bodyHtml() == null ? "" : req.bodyHtml());
        t.setStatus(req.status() == null ? "DRAFT" : req.status());
    }

    private EmailTemplate load(UUID id) {
        return templates.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + id));
    }
}
