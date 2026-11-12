package bg.hristomanov.education.privacy;

/**
 * Измислени учебни показатели; не използвай стойности/коефициенти за диагнози.
 *
 * Валидацията е само на client boundary. Сървърът на FHE лабораторията
 * приема ciphertext и не може самостоятелно да провери тези диапазони.
 */
public record RiskInputs(int age,
                         int totalCholesterol,
                         int hdlCholesterol,
                         int systolicPressure,
                         int hypertension,
                         int smoker) {

    public RiskInputs {
        range("age", age, 0, 120);
        range("totalCholesterol", totalCholesterol, 0, 400);
        range("hdlCholesterol", hdlCholesterol, 0, 200);
        range("systolicPressure", systolicPressure, 0, 250);
        range("hypertension", hypertension, 0, 1);
        range("smoker", smoker, 0, 1);
    }

    public int[] asVector() {
        return new int[]{age, totalCholesterol, hdlCholesterol, systolicPressure,
                hypertension, smoker};
    }

    private static void range(String name, int actual, int min, int max) {
        if (actual < min || actual > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]");
        }
    }
}
