package bg.hristomanov.education.ai.claims;

/** Синтетичен contract: суми в центове, без currency conversion. */
public record Claim(long amountCents, boolean preferredPartner,
                    boolean documentsComplete, boolean suspectedFraud) {
    public Claim {
        if (amountCents < 0) {
            throw new IllegalArgumentException("Negative claim amount");
        }
    }
}
