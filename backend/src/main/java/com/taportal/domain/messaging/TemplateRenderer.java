package com.taportal.domain.messaging;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@code {{merge_field}}} placeholders in a template. Pure: given the
 * template and the values, the result is always the same.
 *
 * <p>It never leaves a placeholder in the result. A field it has no value for
 * is reported in {@link Rendered#missing()}, so the caller can refuse to send
 * rather than text a candidate a half-filled message.
 */
public final class TemplateRenderer {

    /** Names older templates use for fields that have one name now. */
    static final Map<String, String> ALIASES = Map.of(
            "participant_name", "candidate_name",
            "register_link", "link",
            "status", "status_summary");

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    /** The email editor wraps a field in a chip; only the field itself belongs in a sent email. */
    private static final Pattern CHIP = Pattern.compile(
            "<span\\b[^>]*\\bmerge-field-chip\\b[^>]*>\\s*(\\{\\{\\s*[A-Za-z0-9_]+\\s*}})\\s*</span>",
            Pattern.CASE_INSENSITIVE);

    /**
     * @param text    the template with every placeholder replaced
     * @param missing fields the template uses that had no value, in order of first use
     */
    public record Rendered(String text, List<String> missing) {

        public boolean complete() {
            return missing.isEmpty();
        }
    }

    private TemplateRenderer() {
    }

    /** For a text message or an email subject: values go in as they are. */
    public static Rendered plain(String template, Map<String, String> values) {
        return fill(template == null ? "" : template, values, false);
    }

    /** For an email body: values are escaped, and line breaks in a value become line breaks in the email. */
    public static Rendered html(String template, Map<String, String> values) {
        String unwrapped = CHIP.matcher(template == null ? "" : template).replaceAll("$1");
        return fill(unwrapped, values, true);
    }

    /** The fields a template uses, by the name they have now. */
    public static Set<String> fieldsIn(String template) {
        Set<String> fields = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(template == null ? "" : template);
        while (m.find()) {
            fields.add(canonical(m.group(1)));
        }
        return fields;
    }

    static String canonical(String field) {
        String name = field.toLowerCase();
        return ALIASES.getOrDefault(name, name);
    }

    private static Rendered fill(String template, Map<String, String> values, boolean html) {
        List<String> missing = new ArrayList<>();
        Matcher m = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String field = canonical(m.group(1));
            String value = values.get(field);
            if (value == null || value.isBlank()) {
                if (!missing.contains(field)) {
                    missing.add(field);
                }
                value = "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(html ? escape(value) : value));
        }
        m.appendTail(out);
        return new Rendered(out.toString(), List.copyOf(missing));
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                case '\n' -> sb.append("<br>");
                case '\r' -> { }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
