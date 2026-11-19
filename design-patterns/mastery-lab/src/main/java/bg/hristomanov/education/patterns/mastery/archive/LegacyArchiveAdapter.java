package bg.hristomanov.education.patterns.mastery.archive;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Adapter: our ArchiveGateway -> incompatible legacy saveLegacy(String, byte[]). */
public final class LegacyArchiveAdapter implements ArchiveGateway {
    private final LegacyArchiveClient legacy;

    public LegacyArchiveAdapter(LegacyArchiveClient legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
    }

    @Override public String archive(String reference, String content) {
        Objects.requireNonNull(content, "content");
        return legacy.saveLegacy(reference, content.getBytes(StandardCharsets.UTF_8));
    }
}
