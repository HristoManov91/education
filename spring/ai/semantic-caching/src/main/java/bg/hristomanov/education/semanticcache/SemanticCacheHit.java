package bg.hristomanov.education.semanticcache;

public record SemanticCacheHit(
        String response,
        String matchedQuery,
        double similarity
) {
}
