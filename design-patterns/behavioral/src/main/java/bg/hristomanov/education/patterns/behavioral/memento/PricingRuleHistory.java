package bg.hristomanov.education.patterns.behavioral.memento;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Caretaker: пази snapshots, но не знае какво има вътре в тях.
 */
public class PricingRuleHistory {

    private final Deque<PricingRuleEditor.Memento> history = new ArrayDeque<>();

    public void checkpoint(PricingRuleEditor editor) {
        history.push(editor.save());
    }

    public void undo(PricingRuleEditor editor) {
        if (history.isEmpty()) {
            throw new IllegalStateException("No snapshot available");
        }
        editor.restore(history.pop());
    }

    public int snapshotCount() {
        return history.size();
    }
}
