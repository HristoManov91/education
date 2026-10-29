package bg.hristomanov.education.boot4;

/** Само временна грешка: подходяща е за ограничен retry на read-only операция. */
public class TransientCatalogException extends RuntimeException {

    public TransientCatalogException(String message) {
        super(message);
    }
}
