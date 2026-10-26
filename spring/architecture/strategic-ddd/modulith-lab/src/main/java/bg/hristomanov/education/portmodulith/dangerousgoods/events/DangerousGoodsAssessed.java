package bg.hristomanov.education.portmodulith.dangerousgoods.events;

public record DangerousGoodsAssessed(String vesselId, boolean accepted) {

    public DangerousGoodsAssessed {
        if (vesselId == null || vesselId.isBlank()) {
            throw new IllegalArgumentException("vesselId must be present");
        }
    }
}
