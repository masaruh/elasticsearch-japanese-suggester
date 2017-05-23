package org.elasticsearch.index.analysis;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility class that is used to generate key strokes from input string.
 */
public class KeystrokeUtil {
    private static final Map<String, List<Keystroke>> KEY_STROKE_MAP;

    static {
        Map<String, List<Keystroke>> parsed = parseMapping();

        KEY_STROKE_MAP = parsed.entrySet().stream()
                .map(expandMultiChar(parsed))
                .collect(Collectors.collectingAndThen(
                                Collectors.toMap(Map.Entry::getKey, entry -> Collections.unmodifiableList(entry.getValue())),
                                Collections::unmodifiableMap));
    }

    private static Map<String, List<Keystroke>> parseMapping() {
        Map<String, List<Keystroke>> ksTmp = new HashMap<>();
        try (InputStream in = KeystrokeUtil.class.getClassLoader().getResourceAsStream("KeyStrokeMapping.json")) {
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(in);

            parser.nextToken(); // Skip JsonToken.START_OBJECT

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String key = parser.getCurrentName();
                parser.nextToken(); // Skip JsonToken.START_ARRAY

                // read array of keystrokes
                List<Keystroke> keyStrokes = new ArrayList<>();
                int weight = 0;

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    keyStrokes.add(new Keystroke(parser.getText(), weight + 1));
                    weight++;
                }
                ksTmp.put(key, keyStrokes);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ksTmp;
    }

    private static Function<Map.Entry<String, List<Keystroke>>, Map.Entry<String, List<Keystroke>>> expandMultiChar(
            Map<String, List<Keystroke>> mapping) {

        return (Map.Entry<String, List<Keystroke>> entry) -> {
            String key = entry.getKey();
            List<Keystroke> value = entry.getValue();

            if (entry.getKey().length() > 1) {
                PriorityQueue<Keystroke> tmp = new PriorityQueue<>();
                for (int i = 0; i < key.length(); i++) {
                    tmp = append(tmp, mapping.get(key.substring(i, i + 1)), 256, value.get(value.size() - 1).getWeight() - 1);
                }

                entry.setValue(
                        Stream.concat(value.stream(), tmp.stream())
                                .sorted(Comparator.reverseOrder())
                                .map(ks -> new Keystroke(ks.key, ks.weight)) // "squash" history
                                .collect(Collectors.toList()));
            }

            return entry;
        };
    }

    /**
     * Convert reading to canonical key stroke.
     * See {@link #toKeyStrokes(String, int)}.
     *
     * @param reading reading "basically" in Katakana.
     * @return keystroke.
     */
    public static String toCanonicalKeystroke(String reading) {
        return buildKeystrokes(reading, 1).poll().getKey();
    }

    /**
     * Convert reading to key strokes.
     * Input reading is expected to be in Katakana produced by JapaneseTokenizer.
     * However, since there are words/characters that JapaneseTokenizer can't tokenize,
     * it doesn't have to be in Katakana. (In that case, it's not really "reading", though).
     *
     * @param reading reading "basically" in Katakana.
     * @param maxExpansions maximum number of expansions.
     * @return keystrokes
     */
    public static List<String> toKeyStrokes(String reading, int maxExpansions) {
        return buildKeystrokes(reading, maxExpansions).stream()
                .sorted(Comparator.reverseOrder())
                .map(Keystroke::getKey).collect(Collectors.toList());
    }

    private static PriorityQueue<Keystroke> buildKeystrokes(String reading, int maxExpansions) {
        PriorityQueue<Keystroke> keyStrokes = new PriorityQueue<>();

        int pos = 0;
        int len = reading.length();
        while (pos < len) {
            List<Keystroke> keyStrokeFragments = null;

            if (isKatakana(reading.charAt(pos))) {
                // Try two characters lookup.
                // ("キャ", "キュ"..etc)
                if (pos + 2 <= len) {
                    keyStrokeFragments = KEY_STROKE_MAP.get(reading.substring(pos, pos + 2));
                    if (keyStrokeFragments != null) {
                        pos += 2;
                    }
                }

                // If not found, single character lookup.
                if (keyStrokeFragments == null) {
                    String ch = reading.substring(pos, pos + 1);
                    keyStrokeFragments = KEY_STROKE_MAP.get(ch);

                    // There are Katakana characters that aren't in KEY_STROKE_MAP.
                    if (keyStrokeFragments == null) {
                        keyStrokeFragments = Collections.singletonList(new Keystroke(ch, 1));
                    }

                    pos++;
                }
            } else {
                // There are cases the chacter isn't Katakana.
                //  - Not Japanese
                //  - kuromoji doesn't know the word.
                // In that case we treat the character as key stroke.

                // Consume consecutive non-Katakana input.
                int from = pos;
                pos++; // Already checked pos < len and isKatakana(reading.charAt(pos))
                while (pos < len && !isKatakana(reading.charAt(pos))) {
                    pos++;
                }

                keyStrokeFragments = Collections.singletonList(new Keystroke(reading.substring(from, pos), 1));
            }

            keyStrokes = append(keyStrokes, keyStrokeFragments, maxExpansions);
        }

        return keyStrokes;
    }

    private static boolean isKatakana(char c) {
        return 0x30A0 <= c && c <= 0x30FF;
    }

    private static PriorityQueue<Keystroke> append(PriorityQueue<Keystroke> prefixes, List<Keystroke> suffixes, int maxExpansions) {
        return append(prefixes, suffixes, maxExpansions, 0);
    }

    /**
     * Concatenate prefixes and suffixes.
     * @param prefixes prefixes.
     * @param suffixes list of suffixes. Expected to be sorted in ascending order.
     * @param maxExpansions max expansions.
     * @param baseWeight baseWeight.
     * @return priority queue that has combination of prefix and suffix.
     */
    private static PriorityQueue<Keystroke> append(
            PriorityQueue<Keystroke> prefixes, List<Keystroke> suffixes, int maxExpansions, int baseWeight) {

        if (maxExpansions <= 0) {
            throw new IllegalArgumentException("maxExpansions must be > 0");
        }

        if (prefixes.isEmpty()) {
            return suffixes.stream()
                    .limit(maxExpansions)
                    .collect(Collectors.toCollection(PriorityQueue::new));
        }

        return prefixes.stream()
                .flatMap(pfx -> suffixes.stream()
                        .map(sfx -> Keystroke.concatenate(pfx, sfx, baseWeight)))
                .collect(new KeystrokeCollector(maxExpansions));

    }

    private static class Keystroke implements Comparable<Keystroke> {
        private final String key;
        private final int weight;
        private final List<Integer> weightHistory;

        private Keystroke(String key, int weight) {
            this.key = key;
            this.weight = weight;
            this.weightHistory = Collections.singletonList(weight);
        }

        private Keystroke(String key, int weight, List<Integer> history) {
            this.key = key;
            this.weight = weight;
            List<Integer> tmp = new ArrayList<>(history);
            tmp.add(weight);
            this.weightHistory = Collections.unmodifiableList(tmp);
        }

        private String getKey() {
            return this.key;
        }

        private int getWeight() {
            return weight;
        }

        // Order by:
        // 1. weight descending
        // 2. prefix (weight excluding the last keystroke) weight descending
        // 3. key descending
        @Override
        public int compareTo(Keystroke other) {
            int result = other.weight - this.weight;
            if (result != 0) {
                return result;
            }

            for (int i = 0; i < other.weightHistory.size(); i++) {
                result = other.weightHistory.get(i) - this.weightHistory.get(i);
                if (result != 0) {
                    return result;
                }
            }
            return other.key.compareTo(this.key);
        }

        private static Keystroke concatenate(Keystroke k1, Keystroke k2, int extraWeight) {
            return new Keystroke(k1.getKey() + k2.getKey(), k1.getWeight() + k2.getWeight() + extraWeight, k1.weightHistory);
        }

        @Override
        public String toString() {
            return key + " (" + weightHistory.stream().map(Object::toString).collect(Collectors.joining(",")) + " - " + weight + ")";
        }
    }

    private static class KeystrokeCollector implements Collector<Keystroke, PriorityQueue<Keystroke>, PriorityQueue<Keystroke>> {
        private final int maxExpansions;

        KeystrokeCollector(int maxExpansions) {
            this.maxExpansions = maxExpansions;
        }

        @Override
        public Supplier<PriorityQueue<Keystroke>> supplier() {
            return PriorityQueue::new;
        }

        @Override
        public BiConsumer<PriorityQueue<Keystroke>, Keystroke> accumulator() {
            return this::add;
        }

        @Override
        public BinaryOperator<PriorityQueue<Keystroke>> combiner() {
            return (q1, q2) -> {
                q2.forEach(ks -> add(q1, ks));
                return q1;
            };
        }

        @Override
        public Function<PriorityQueue<Keystroke>, PriorityQueue<Keystroke>> finisher() {
            return Function.identity();
        }

        // Small hack to skip adding and removing.
        private void add(PriorityQueue<Keystroke> q, Keystroke ks) {
            if (q.size() < maxExpansions) {
                q.add(ks);
            } else if (ks.compareTo(q.peek()) > 0) {
                q.poll();
                q.add(ks);
            }
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
        }
    }
}
