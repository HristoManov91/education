package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.algorithms.hashing.model.MapCopyLab;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Крос-JDK experiment за JDK 26 record hashing и JDK 27 HashMap bulk copy.
 * Числата зависят от source concrete class, call-site shape, CPU и warmup.
 */
public class MapCopyAndRecordHashBenchmark {

    @State(Scope.Thread)
    public static class Fixture {
        @Param({"5", "150", "2048"})
        public int mapSize;

        @Param({"hashmap", "unmodifiableHashMap", "linkedHashMap"})
        public String sourceKind;

        private Map<MapCopyLab.DefaultKey, Integer> source;
        private MapCopyLab.DefaultKey[] defaultKeys;
        private MapCopyLab.ManualHashKey[] manualKeys;

        @Setup(Level.Trial)
        public void setUp() {
            HashMap<MapCopyLab.DefaultKey, Integer> hashMap = MapCopyLab.source(mapSize);
            source = switch (sourceKind) {
                case "hashmap" -> hashMap;
                case "unmodifiableHashMap" -> Collections.unmodifiableMap(hashMap);
                case "linkedHashMap" -> new LinkedHashMap<>(hashMap);
                default -> throw new IllegalArgumentException("Unexpected source kind");
            };
            defaultKeys = new MapCopyLab.DefaultKey[mapSize];
            manualKeys = new MapCopyLab.ManualHashKey[mapSize];
            for (int index = 0; index < mapSize; index++) {
                defaultKeys[index] = new MapCopyLab.DefaultKey(index % 19, index);
                manualKeys[index] = new MapCopyLab.ManualHashKey(index % 19, index);
            }
        }
    }

    @Benchmark
    public Map<MapCopyLab.DefaultKey, Integer> mapConstructor(Fixture fixture) {
        return MapCopyLab.constructorCopy(fixture.source);
    }

    @Benchmark
    public Map<MapCopyLab.DefaultKey, Integer> mapPutAll(Fixture fixture) {
        return MapCopyLab.putAllCopy(fixture.source);
    }

    @Benchmark
    public int defaultRecordHash(Fixture fixture) {
        int checksum = 1;
        for (MapCopyLab.DefaultKey key : fixture.defaultKeys) {
            checksum = 31 * checksum + key.hashCode();
        }
        return checksum;
    }

    @Benchmark
    public int manualRecordHash(Fixture fixture) {
        int checksum = 1;
        for (MapCopyLab.ManualHashKey key : fixture.manualKeys) {
            checksum = 31 * checksum + key.hashCode();
        }
        return checksum;
    }
}
