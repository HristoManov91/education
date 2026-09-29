package bg.hristomanov.education.patterns.behavioral.command;

import java.util.ArrayList;
import java.util.List;

public class CommandBus {

    private final List<String> executionHistory = new ArrayList<>();

    public <R> R execute(Command<R> command) {
        executionHistory.add(command.name());
        return command.execute();
    }

    public List<String> executionHistory() {
        return List.copyOf(executionHistory);
    }
}
