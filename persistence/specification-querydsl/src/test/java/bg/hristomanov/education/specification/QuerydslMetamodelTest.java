package bg.hristomanov.education.specification;

import bg.hristomanov.education.specification.domain.QPaymentEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuerydslMetamodelTest {

    @Test
    void annotationProcessingGeneratesTypedPaymentPaths() {
        QPaymentEntity payment = QPaymentEntity.paymentEntity;

        assertThat(payment.status.getType()).isNotNull();
        assertThat(payment.amount.getType()).isNotNull();
        assertThat(payment.createdAt.getType()).isNotNull();
    }
}
