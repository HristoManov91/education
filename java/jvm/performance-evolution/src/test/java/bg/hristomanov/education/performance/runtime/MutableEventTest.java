package bg.hristomanov.education.performance.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MutableEventTest {

    @Test
    void freshEventsPreservePreviousBusinessState() {
        MutableEvent first = new MutableEvent("order-1", "accepted", 100);
        MutableEvent second = new MutableEvent("order-2", "cancelled", 200);

        assertThat(first.key()).isEqualTo("order-1");
        assertThat(first.amount()).isEqualTo(100);
        assertThat(second.detail()).isEqualTo("cancelled");
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void reusingObjectMutatesAllAliasesAndIsNotEquivalentToFreshDto() {
        MutableEvent reused = new MutableEvent("order-1", "accepted", 100);
        MutableEvent retainedResponse = reused;
        reused.overwrite("order-2", "cancelled", 200);

        assertThat(retainedResponse).isSameAs(reused);
        assertThat(retainedResponse.key()).isEqualTo("order-2");
        assertThat(retainedResponse.amount()).isEqualTo(200);
    }

    @Test
    void invalidReferencesAreRejected() {
        assertThatThrownBy(() -> new MutableEvent(null, "ok", 1))
                .isInstanceOf(NullPointerException.class);
        MutableEvent event = new MutableEvent("id", "ok", 1);
        assertThatThrownBy(() -> event.overwrite("id", null, 2))
                .isInstanceOf(NullPointerException.class);
        assertThat(event.key()).isEqualTo("id");
        assertThat(event.detail()).isEqualTo("ok");
        assertThat(event.amount()).isEqualTo(1);
    }
}
