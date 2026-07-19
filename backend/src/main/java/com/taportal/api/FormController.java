package com.taportal.api;

import com.taportal.domain.forms.FormDefinition;
import com.taportal.domain.forms.FormDefinitionRepository;
import com.taportal.domain.forms.FormResponse;
import com.taportal.domain.forms.FormResponseRepository;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forms as a platform capability: purpose-keyed definitions (edited in the
 * Admin builder) + responses attached to subjects. Thin CRUD — the schema is
 * the builder's JSON, opaque to the backend.
 */
@RestController
public class FormController {

    public record FormDefinitionDto(String purpose, String name, String schema, OffsetDateTime updatedAt) {}

    public record SaveFormRequest(@NotBlank String name, @NotBlank String schema) {}

    public record SubmitResponseRequest(@NotBlank String subjectType, UUID subjectId, @NotBlank String answers) {}

    public record FormResponseDto(UUID id, String formPurpose, String answers, OffsetDateTime createdAt) {}

    private final FormDefinitionRepository definitions;
    private final FormResponseRepository responses;

    public FormController(FormDefinitionRepository definitions, FormResponseRepository responses) {
        this.definitions = definitions;
        this.responses = responses;
    }

    @GetMapping("/v1/forms/{purpose}")
    public FormDefinitionDto get(@PathVariable String purpose) {
        FormDefinition def = definitions.findByPurpose(purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No form for purpose " + purpose));
        return new FormDefinitionDto(def.getPurpose(), def.getName(), def.getSchema(), def.getUpdatedAt());
    }

    @PutMapping("/v1/forms/{purpose}")
    @Transactional
    public FormDefinitionDto save(@PathVariable String purpose, @RequestBody SaveFormRequest request) {
        FormDefinition def = definitions.findByPurpose(purpose).orElseGet(() -> {
            FormDefinition d = new FormDefinition();
            d.setPurpose(purpose);
            return d;
        });
        def.setName(request.name());
        def.setSchema(request.schema());
        def = definitions.save(def);
        return new FormDefinitionDto(def.getPurpose(), def.getName(), def.getSchema(), def.getUpdatedAt());
    }

    @PostMapping("/v1/forms/{purpose}/responses")
    @Transactional
    public FormResponseDto submit(@PathVariable String purpose, @RequestBody SubmitResponseRequest request) {
        FormResponse r = new FormResponse();
        r.setFormPurpose(purpose);
        r.setSubjectType(request.subjectType());
        r.setSubjectId(request.subjectId());
        r.setAnswers(request.answers());
        r = responses.save(r);
        return new FormResponseDto(r.getId(), r.getFormPurpose(), r.getAnswers(), r.getCreatedAt());
    }

    @GetMapping("/v1/form-responses")
    public List<FormResponseDto> bySubject(@RequestParam String subjectType, @RequestParam UUID subjectId) {
        return responses.findBySubjectTypeAndSubjectIdOrderByCreatedAt(subjectType, subjectId).stream()
                .map(r -> new FormResponseDto(r.getId(), r.getFormPurpose(), r.getAnswers(), r.getCreatedAt()))
                .toList();
    }
}
