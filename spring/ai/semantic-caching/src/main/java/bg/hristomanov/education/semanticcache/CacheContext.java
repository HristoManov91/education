package bg.hristomanov.education.semanticcache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record CacheContext(
        String tenantId,
        String systemPromptVersion,
        String modelVersion,
        String knowledgeBaseVersion,
        String locale
) {

    public String fingerprint() {
        String canonical = String.join(
                "|",
                tenantId,
                systemPromptVersion,
                modelVersion,
                knowledgeBaseVersion,
                locale
        );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(
                            canonical.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }
}
