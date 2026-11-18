package bg.hristomanov.education.performance.quarkus;

import java.util.List;
import java.util.Objects;

/**
 * Сравнение на три семантично еднакви dispatch стратегии.
 * Inspired by the Quarkus-generated tableswitch, но това НЕ е Quarkus source code.
 */
public final class HandlerDispatchLab {
    private HandlerDispatchLab() {
    }

    public interface Handler {
        int apply(int value);
    }

    public record Add(int amount) implements Handler {
        @Override
        public int apply(int value) {
            return value + amount;
        }
    }

    public record Multiply(int factor) implements Handler {
        @Override
        public int apply(int value) {
            return value * factor;
        }
    }

    public record Xor(int mask) implements Handler {
        @Override
        public int apply(int value) {
            return value ^ mask;
        }
    }

    /**
     * Classify each handler exactly once. Unknown handlers keep the safe
     * interface fallback, so extension types are not silently skipped.
     */
    public static final class Chain {
        private final List<Handler> handlers;
        private final byte[] kinds;

        public Chain(List<Handler> handlers) {
            this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
            this.kinds = new byte[this.handlers.size()];
            for (int i = 0; i < this.handlers.size(); i++) {
                kinds[i] = kindOf(this.handlers.get(i));
            }
        }

        public int viaVirtual(int value) {
            int result = value;
            for (Handler handler : handlers) {
                result = handler.apply(result);
            }
            return result;
        }

        public int viaInstanceof(int value) {
            int result = value;
            for (Handler handler : handlers) {
                if (handler instanceof Add add) {
                    result += add.amount();
                } else if (handler instanceof Multiply multiply) {
                    result *= multiply.factor();
                } else if (handler instanceof Xor xor) {
                    result ^= xor.mask();
                } else {
                    result = handler.apply(result);
                }
            }
            return result;
        }

        public int viaPreclassified(int value) {
            int result = value;
            for (int i = 0; i < handlers.size(); i++) {
                Handler handler = handlers.get(i);
                result = switch (kinds[i]) {
                    case 0 -> result + ((Add) handler).amount();
                    case 1 -> result * ((Multiply) handler).factor();
                    case 2 -> result ^ ((Xor) handler).mask();
                    default -> handler.apply(result);
                };
            }
            return result;
        }

        public int size() {
            return handlers.size();
        }

        private static byte kindOf(Handler handler) {
            if (handler instanceof Add) {
                return 0;
            }
            if (handler instanceof Multiply) {
                return 1;
            }
            if (handler instanceof Xor) {
                return 2;
            }
            return -1;
        }
    }
}
