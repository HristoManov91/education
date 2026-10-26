package bg.hristomanov.education.dop;

public record Infusion(int millilitres, int minutes) implements Dose {

    public Infusion {
        if (millilitres <= 0 || minutes <= 0) {
            throw new IllegalArgumentException("Infusion volume and duration must be positive");
        }
    }
}
