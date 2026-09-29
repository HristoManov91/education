package bg.hristomanov.education.semanticcache;

public interface TextEmbeddingModel {

    double[] embed(String text);
}
