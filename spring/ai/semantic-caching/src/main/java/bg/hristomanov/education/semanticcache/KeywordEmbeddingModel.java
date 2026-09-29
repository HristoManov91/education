package bg.hristomanov.education.semanticcache;

import java.util.Locale;

/**
 * Deterministic educational embedding model.
 *
 * <p>This is deliberately NOT a production embedding model. It gives the lab
 * stable semantic neighborhoods without network/API keys so tests can prove
 * threshold and false-hit semantics. In production the same cache policy can
 * be backed by Spring AI EmbeddingModel + Redis vector search.</p>
 */
public class KeywordEmbeddingModel
        implements TextEmbeddingModel {

    @Override
    public double[] embed(String text) {
        String normalized =
                text.toLowerCase(Locale.ROOT);

        double[] vector = new double[8];

        add(
                vector,
                0,
                normalized,
                "capital",
                "capital city",
                "france",
                "paris"
        );
        add(
                vector,
                1,
                normalized,
                "password",
                "reset",
                "login",
                "credential"
        );
        add(
                vector,
                2,
                normalized,
                "account",
                "balance",
                "funds"
        );
        add(
                vector,
                3,
                normalized,
                "yesterday",
                "historical",
                "previous",
                "last day"
        );
        add(
                vector,
                4,
                normalized,
                "product",
                "feature",
                "features",
                "offers"
        );
        add(
                vector,
                5,
                normalized,
                "weather",
                "temperature",
                "forecast"
        );
        add(
                vector,
                6,
                normalized,
                "today",
                "current",
                "now"
        );
        add(
                vector,
                7,
                normalized,
                "how",
                "what",
                "tell",
                "which"
        );

        normalize(vector);
        return vector;
    }

    private void add(
            double[] vector,
            int dimension,
            String text,
            String... terms
    ) {
        for (String term : terms) {
            if (text.contains(term)) {
                vector[dimension] += 1.0;
            }
        }
    }

    private void normalize(double[] vector) {
        double squaredLength = 0.0;

        for (double value : vector) {
            squaredLength += value * value;
        }

        if (squaredLength == 0.0) {
            return;
        }

        double length = Math.sqrt(squaredLength);

        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / length;
        }
    }
}
