package bg.hristomanov.education.algorithms.hashing.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * JDK 26 record hashing / JDK 27 HashMap bulk-copy teaching contract.
 * Копирането прави нова map структура, но НЕ клонира keys/values (shallow copy).
 */
public final class MapCopyLab {

    private MapCopyLab() {
    }

    public record DefaultKey(int tenant, int sku) {
    }

    public record ManualHashKey(int tenant, int sku) {
        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(tenant) + Integer.hashCode(sku);
        }
    }

    public static HashMap<DefaultKey, Integer> source(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        HashMap<DefaultKey, Integer> result = new HashMap<>();
        for (int index = 0; index < size; index++) {
            result.put(new DefaultKey(index % 19, index), index * 3);
        }
        return result;
    }

    public static HashMap<DefaultKey, Integer> constructorCopy(Map<DefaultKey, Integer> source) {
        return new HashMap<>(source);
    }

    public static HashMap<DefaultKey, Integer> putAllCopy(Map<DefaultKey, Integer> source) {
        HashMap<DefaultKey, Integer> result = new HashMap<>();
        result.putAll(source);
        return result;
    }

    public static Map<DefaultKey, Integer> unmodifiableView(HashMap<DefaultKey, Integer> source) {
        return Collections.unmodifiableMap(source);
    }
}
