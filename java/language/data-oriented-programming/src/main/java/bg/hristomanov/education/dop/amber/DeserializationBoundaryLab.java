package bg.hristomanov.education.dop.amber;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Учебен контраст: валидиращият constructor на обикновен Serializable клас
 * НЕ е задължително да бъде извикан от ObjectInputStream.
 *
 * Тези класове не са API за доверяване на непроверени байтови потоци.
 * Java native deserialization на недоверени данни остава опасна
 * независимо от наличието на constructor checks.
 */
public final class DeserializationBoundaryLab {

    private DeserializationBoundaryLab() {
    }

    private static void validateAdultAge(int age) {
        if (age < 18 || age > 120) {
            throw new IllegalArgumentException("Adult age must be between 18 and 120");
        }
    }

    /**
     * BAD legacy пример: constructor validation се заобикаля при native
     * deserialization. Field не е final, за да няма зависимост от reflection
     * правила при създаването на тестовия невалиден поток.
     */
    public static final class LegacyAdult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private int age;

        public LegacyAdult(int age) {
            validateAdultAge(age);
            this.age = age;
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        public int age() {
            return age;
        }

        public static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }

    /**
     * GOOD, когато transparent value shape е желан: при native record
     * deserialization ObjectInputStream използва canonical constructor.
     */
    public record RecordAdult(int age) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public RecordAdult {
            validateAdultAge(age);
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        public static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }

    /**
     * Good алтернатива, когато class identity/representation трябва да останат:
     * проверка при readObject, а не сляпо доверие на обикновения constructor.
     * Не заменя ObjectInputFilter и проверката на произхода на потока.
     */
    public static final class GuardedAdult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private int age;

        public GuardedAdult(int age) {
            validateAdultAge(age);
            this.age = age;
        }

        public int age() {
            return age;
        }

        @Serial
        private void readObject(ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            try {
                validateAdultAge(age);
            } catch (IllegalArgumentException violation) {
                InvalidObjectException rejected =
                        new InvalidObjectException("Invalid restored Adult state");
                rejected.initCause(violation);
                throw rejected;
            }
        }
    }
}
