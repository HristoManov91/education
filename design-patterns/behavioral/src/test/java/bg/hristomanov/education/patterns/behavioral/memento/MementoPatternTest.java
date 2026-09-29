package bg.hristomanov.education.patterns.behavioral.memento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MementoPatternTest {

    @Test
    void caretakerCanUndoWithoutReadingOriginatorPrivateState() {
        PricingRuleEditor editor = new PricingRuleEditor("Autumn campaign");
        editor.setDiscount("VIP", new BigDecimal("10.00"));

        PricingRuleHistory history = new PricingRuleHistory();
        history.checkpoint(editor);

        editor.rename("Broken edit");
        editor.setDiscount("VIP", new BigDecimal("95.00"));
        editor.setDiscount("REGULAR", new BigDecimal("90.00"));

        history.undo(editor);

        assertThat(editor.name()).isEqualTo("Autumn campaign");
        assertThat(editor.discounts())
                .containsEntry("VIP", new BigDecimal("10.00"))
                .doesNotContainKey("REGULAR");
        assertThat(history.snapshotCount()).isZero();
    }
}
