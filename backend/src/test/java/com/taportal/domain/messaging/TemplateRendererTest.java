package com.taportal.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.taportal.domain.messaging.TemplateRenderer.Rendered;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    private static final Map<String, String> VALUES = Map.of(
            "candidate_name", "Raj Patel",
            "first_name", "Raj",
            "job_title", "Summer Analyst Intern",
            "link", "https://careers.example/schedule/abc?x=1&y=2",
            "slot_options", "1) Tue, Oct 6, 10:00 AM ET\n2) Wed, Oct 7, 2:30 PM ET",
            "status_summary", "your application is under review");

    @Test
    void fieldsAreFilled() {
        Rendered r = TemplateRenderer.plain("Hi {{first_name}}, we received your application for {{job_title}}.", VALUES);

        assertThat(r.text()).isEqualTo("Hi Raj, we received your application for Summer Analyst Intern.");
        assertThat(r.complete()).isTrue();
    }

    @Test
    void spacesInsideTheBracesAndCapitalsAreForgiven() {
        assertThat(TemplateRenderer.plain("{{ First_Name }}", VALUES).text()).isEqualTo("Raj");
    }

    @Test
    @DisplayName("a field with no value is never sent as a placeholder: it is reported")
    void aMissingFieldIsReportedNotSent() {
        Rendered r = TemplateRenderer.plain("Join: {{meeting_link}}. See you, {{first_name}}. {{meeting_link}}", VALUES);

        assertThat(r.text()).doesNotContain("{{").isEqualTo("Join: . See you, Raj. ");
        assertThat(r.missing()).containsExactly("meeting_link");
        assertThat(r.complete()).isFalse();
    }

    @Test
    void aFieldTheSystemDoesNotKnowIsReportedToo() {
        assertThat(TemplateRenderer.plain("See you at {{event_name}}", VALUES).missing()).containsExactly("event_name");
    }

    @Test
    void aBlankValueCountsAsMissing() {
        assertThat(TemplateRenderer.plain("{{x}}", Map.of("x", "  ")).missing()).containsExactly("x");
    }

    @Test
    @DisplayName("older field names still work")
    void olderNamesAreUnderstood() {
        Rendered r = TemplateRenderer.plain("{{participant_name}} | {{register_link}} | {{status}}", VALUES);

        assertThat(r.text()).isEqualTo(
                "Raj Patel | https://careers.example/schedule/abc?x=1&y=2 | your application is under review");
        assertThat(r.complete()).isTrue();
    }

    @Test
    void aTextMessageGetsValuesAsTheyAre() {
        assertThat(TemplateRenderer.plain("{{link}}", VALUES).text())
                .isEqualTo("https://careers.example/schedule/abc?x=1&y=2");
    }

    @Test
    @DisplayName("in an email a value can never become markup")
    void emailValuesAreEscaped() {
        Rendered r = TemplateRenderer.html("<p>Hi {{candidate_name}}</p>",
                Map.of("candidate_name", "<script>alert('x')</script> & \"co\""));

        assertThat(r.text()).isEqualTo("<p>Hi &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;co&quot;</p>");
    }

    @Test
    void aLinkInsideAnEmailLinkStaysALink() {
        assertThat(TemplateRenderer.html("<a href=\"{{register_link}}\">Pick a time</a>", VALUES).text())
                .isEqualTo("<a href=\"https://careers.example/schedule/abc?x=1&amp;y=2\">Pick a time</a>");
    }

    @Test
    void linesInAValueBecomeLinesInTheEmail() {
        assertThat(TemplateRenderer.html("<p>{{slot_options}}</p>", VALUES).text())
                .isEqualTo("<p>1) Tue, Oct 6, 10:00 AM ET<br>2) Wed, Oct 7, 2:30 PM ET</p>");
    }

    @Test
    @DisplayName("the editor's chip around a field is not sent, and nothing lands in an attribute")
    void editorChipsAreUnwrapped() {
        String saved = "<p>Hi <span class=\"merge-field-chip\" data-field=\"{{candidate_name}}\" "
                + "contenteditable=\"false\">{{candidate_name}}</span>,</p>";

        assertThat(TemplateRenderer.html(saved, VALUES).text()).isEqualTo("<p>Hi Raj Patel,</p>");
    }

    @Test
    void aTemplateWithNothingToFillIsLeftAlone() {
        assertThat(TemplateRenderer.plain("Reply STOP to opt out.", Map.of()).text()).isEqualTo("Reply STOP to opt out.");
        assertThat(TemplateRenderer.plain(null, Map.of()).text()).isEmpty();
    }

    @Test
    void aValueContainingBracesOrDollarSignsIsNotReadAgain() {
        assertThat(TemplateRenderer.plain("{{a}}", Map.of("a", "{{b}} costs $1 \\ more", "b", "no")).text())
                .isEqualTo("{{b}} costs $1 \\ more");
    }

    @Test
    void theFieldsATemplateUsesCanBeListed() {
        assertThat(TemplateRenderer.fieldsIn("{{participant_name}} {{job_title}} {{ job_title }}"))
                .containsExactly("candidate_name", "job_title");
    }
}
