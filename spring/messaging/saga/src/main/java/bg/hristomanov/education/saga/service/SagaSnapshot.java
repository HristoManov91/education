package bg.hristomanov.education.saga.service;

import bg.hristomanov.education.saga.domain.ParticipantStatus;
import bg.hristomanov.education.saga.domain.SagaStatus;

import java.util.List;

public record SagaSnapshot(
        long sagaId,
        SagaStatus sagaStatus,
        ParticipantStatus paymentStatus,
        ParticipantStatus inventoryStatus,
        ParticipantStatus shipmentStatus,
        String failureReason,
        List<String> trace
) {
}
