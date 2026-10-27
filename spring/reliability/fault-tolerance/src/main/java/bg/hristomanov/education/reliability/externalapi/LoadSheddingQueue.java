package bg.hristomanov.education.reliability.externalapi;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

/**
 * In-process учебен scheduler за noncritical refresh tasks.
 * Критична операция НИКОГА не се изхвърля тихомълком: ако няма капацитет,
 * връщаме explicit REJECTED_CRITICAL, за да може upstream durable queue
 * да запази/повтори задачата. НЕ е заместител на durable broker.
 */
public final class LoadSheddingQueue {

    public enum Kind { REPLACEABLE_REFRESH, CRITICAL }
    public enum Admission { ACCEPTED, SHED_EXPIRED, SHED_OVERLOAD, REJECTED_CRITICAL }
    public enum Execution { EXECUTED, EMPTY }

    public record Task(String id, Kind kind, Instant expiresAt, Runnable action) {
        public Task {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(action, "action");
            if (kind == Kind.REPLACEABLE_REFRESH && expiresAt == null) {
                throw new IllegalArgumentException("Refresh work needs an expiration deadline");
            }
        }
    }

    public record Metrics(int queuedCritical, int queuedRefresh,
                          long expired, long shedForCapacity,
                          long rejectedCritical, long executed) {}

    private final int maxQueued;
    private final Clock clock;
    private final Deque<Task> critical = new ArrayDeque<>();
    private final Deque<Task> refresh = new ArrayDeque<>();
    private long expired;
    private long shedForCapacity;
    private long rejectedCritical;
    private long executed;

    public LoadSheddingQueue(int maxQueued, Clock clock) {
        if (maxQueued < 1) {
            throw new IllegalArgumentException("maxQueued must be positive");
        }
        this.maxQueued = maxQueued;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Admission enqueue(Task task) {
        Objects.requireNonNull(task, "task");
        removeExpiredRefreshes();

        if (task.kind() == Kind.REPLACEABLE_REFRESH
                && !task.expiresAt().isAfter(clock.instant())) {
            expired++;
            return Admission.SHED_EXPIRED;
        }

        if (queued() >= maxQueued) {
            if (task.kind() == Kind.REPLACEABLE_REFRESH) {
                shedForCapacity++;
                return Admission.SHED_OVERLOAD;
            }
            // При критична нова задача освобождаваме само заменима работа.
            if (!refresh.isEmpty()) {
                refresh.removeFirst();
                shedForCapacity++;
            } else {
                rejectedCritical++;
                return Admission.REJECTED_CRITICAL;
            }
        }

        if (task.kind() == Kind.CRITICAL) {
            critical.addLast(task);
        } else {
            refresh.addLast(task);
        }
        return Admission.ACCEPTED;
    }

    /**
     * Взимаме task-а под lock, но изпълняваме user code извън lock,
     * за да не блокираме всички producers при бавен/счупен task.
     */
    public Execution runNext() {
        Task task;
        synchronized (this) {
            removeExpiredRefreshes();
            task = !critical.isEmpty() ? critical.removeFirst() : refresh.pollFirst();
        }
        if (task == null) {
            return Execution.EMPTY;
        }

        // Още една проверка непосредствено преди старта на операцията.
        if (task.kind() == Kind.REPLACEABLE_REFRESH
                && !task.expiresAt().isAfter(clock.instant())) {
            synchronized (this) {
                expired++;
            }
            return Execution.EMPTY;
        }

        // Ако action хвърли exception, реалният durable consumer не ACK-ва.
        task.action().run();
        synchronized (this) {
            executed++;
        }
        return Execution.EXECUTED;
    }

    public synchronized Metrics metrics() {
        return new Metrics(critical.size(), refresh.size(), expired,
                shedForCapacity, rejectedCritical, executed);
    }

    private int queued() {
        return critical.size() + refresh.size();
    }

    private void removeExpiredRefreshes() {
        Instant now = clock.instant();
        Iterator<Task> iterator = refresh.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().expiresAt().isAfter(now)) {
                iterator.remove();
                expired++;
            }
        }
    }
}
