package bg.hristomanov.education.dop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatedSearchQueryTest {
    @Test
    void normalizesTextBeforeCallingParentConstructor() {
        var query = new ValidatedSearchQuery("  apple pie  ", 20);
        assertThat(query.text()).isEqualTo("apple pie");
        assertThat(query.limit()).isEqualTo(20);
    }

    @Test
    void rejectsBlankNullAndInvalidLimitsAtConstructionBoundary() {
        assertThatThrownBy(() -> new ValidatedSearchQuery("  ", 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
        assertThatThrownBy(() -> new ValidatedSearchQuery(null, 10))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("rawText");
        assertThatThrownBy(() -> new ValidatedSearchQuery("valid", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSearchQuery("valid", 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundaryLimitsRemainAllowed() {
        assertThat(new ValidatedSearchQuery("  kiwi  ", 1).limit()).isEqualTo(1);
        assertThat(new ValidatedSearchQuery("  kiwi  ", 100).limit()).isEqualTo(100);
    }
}
