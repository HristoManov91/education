package bg.hristomanov.education.patterns.creational.prototype;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry от предварително конфигурирани prototypes.
 *
 * <p>Caller-ът получава copy, а не shared template instance. Това е полезно,
 * когато initial configuration е сложна/скъпа или имаме често използвани presets.</p>
 */
public class ReportTemplateRegistry {

    private final Map<String, ReportTemplate> templates = new LinkedHashMap<>();

    public void register(ReportTemplate template) {
        templates.put(template.name(), template);
    }

    public ReportTemplate createFrom(String templateName) {
        ReportTemplate template = templates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Unknown report template: " + templateName);
        }
        return template.copy();
    }
}
