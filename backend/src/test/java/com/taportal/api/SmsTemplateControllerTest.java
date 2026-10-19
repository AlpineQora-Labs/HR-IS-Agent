package com.taportal.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taportal.domain.comms.SmsTemplateRepository;
import com.taportal.domain.comms.TemplateRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The editor's count comes from here, so it is the count the candidate's record shows. */
class SmsTemplateControllerTest {

    private SmsTemplateRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(SmsTemplateRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new SmsTemplateController(repository, new TemplateRules())).build();
    }

    private ResultActions preview(String body) throws Exception {
        String json = "{\"body\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return mvc.perform(post("/v1/sms-templates/preview").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("one part up to 160 characters, then 153 to a part")
    void partsAreCountedTheWayTheRecordCountsThem() throws Exception {
        preview("a".repeat(160)).andExpect(jsonPath("$.characters").value(160)).andExpect(jsonPath("$.parts").value(1));
        preview("a".repeat(161)).andExpect(jsonPath("$.parts").value(2));
        preview("a".repeat(306)).andExpect(jsonPath("$.parts").value(2)).andExpect(jsonPath("$.note").doesNotExist());
        preview("a".repeat(307)).andExpect(jsonPath("$.parts").value(3))
                .andExpect(jsonPath("$.note").value("keep candidate texts short"));
    }

    @Test
    void aEuroSignCountsTwice() throws Exception {
        preview("€" + "a".repeat(159)).andExpect(jsonPath("$.characters").value(161)).andExpect(jsonPath("$.parts").value(2));
    }

    @Test
    @DisplayName("curly quotes are counted as the straight ones that are sent")
    void curlyQuotesAreMadeStraight() throws Exception {
        preview("You’re “in”, {{candidate_name}}")
                .andExpect(jsonPath("$.plainText").value("You're \"in\", {{candidate_name}}"))
                .andExpect(jsonPath("$.characters").value(35));
    }

    @Test
    void aPreviewSavesNothing() throws Exception {
        preview("Hi {{candidate_name}}");
        verifyNoInteractions(repository);
    }
}
