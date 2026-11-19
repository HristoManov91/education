package bg.hristomanov.education.patterns.mastery.render;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Strategy registry; fail fast on ambiguous registrations. */
public final class RendererRegistry {
    private final Map<String, ReportRenderer> renderers;

    public RendererRegistry(List<ReportRenderer> rendererList) {
        Objects.requireNonNull(rendererList, "rendererList");
        Map<String, ReportRenderer> byFormat = new HashMap<>();
        for (ReportRenderer renderer : rendererList) {
            Objects.requireNonNull(renderer, "renderer");
            String key = normalize(renderer.format());
            if (byFormat.putIfAbsent(key, renderer) != null) {
                throw new IllegalArgumentException("duplicate renderer: " + key);
            }
        }
        renderers = Map.copyOf(byFormat);
    }

    public ReportRenderer forFormat(String format) {
        String key = normalize(format);
        ReportRenderer renderer = renderers.get(key);
        if (renderer == null) {
            throw new IllegalArgumentException("unsupported report format: " + key);
        }
        return renderer;
    }

    private static String normalize(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("format is required");
        }
        return format.trim().toUpperCase(Locale.ROOT);
    }
}
