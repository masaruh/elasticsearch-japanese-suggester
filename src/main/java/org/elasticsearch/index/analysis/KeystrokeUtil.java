package org.elasticsearch.index.analysis;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * A utility class that is used to generate key strokes from input string.
 */
public class KeystrokeUtil {
    // Package private for test
    static final Map<String, List<Keystroke>> KEY_STROKE_MAP;

    static {
        Map<String, List<Keystroke>> parsed = parseMapping();

        // Expand 2 char entries and then 3 char entries
        for (int i = 2; i <= 3; i++) {
            int len = i;
            parsed.putAll(
                    parsed.entrySet().stream()
                            .filter(entry -> entry.getKey().length() == len)
                            .map(expandEntry(parsed))
                            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        KEY_STROKE_MAP = Collections.unmodifiableMap(parsed);
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

    private static Function<Map.Entry<String, List<Keystroke>>, Map.Entry<String, List<Keystroke>>> expandEntry(
            Map<String, List<Keystroke>> lookup) {

        return entry -> {
            String key = entry.getKey();
            List<Keystroke> original = entry.getValue();
            List<Keystroke> keystrokes = new ArrayList<>(original);
            keystrokes.addAll(expand(key, lookup, key.length() - 1, original.get(original.size() -1).getWeight()));
            return new AbstractMap.SimpleEntry<>(key, Collections.unmodifiableList(keystrokes));
        };
    }

    private static List<Keystroke> expand(String key, Map<String, List<Keystroke>> reference, int maxCharLength, int baseWeight) {
        if (key.length() <= maxCharLength) {
            return reference.get(key);
        }

        PriorityQueue<Keystroke> expanded = new PriorityQueue<>();
        for (int i = 1; i <= maxCharLength; i++) {
            List<Keystroke> left = expand(key.substring(0, i), reference, maxCharLength, 0);
            List<Keystroke> right = expand(key.substring(i), reference, maxCharLength, 0);
            if (left == null || right == null) {
                continue;
            }
            expanded.addAll(append(new PriorityQueue<>(left), right, 256, baseWeight));
        }

        // There may be duplicates, same keystroke but different weights because of different path.
        // Deduplicate them by keeping smallest ones.
        Map<String, Keystroke> deduped = expanded.stream()
                .collect(toMap(
                        Keystroke::getKey,
                        Function.identity(),
                        BinaryOperator.minBy(Comparator.<Keystroke>reverseOrder())));

        return deduped.values().stream()
                .sorted(reverseOrder())
                .map(ks -> new Keystroke(ks.getKey(), ks.getWeight())) // "squash" history
                .collect(toList());
    }

    /**
     * Convert reading to canonical key stroke.
     * See {@link #toKeyStrokes(String, int)}.
     *
     * @param reading reading "basically" in Katakana.
     * @return keystroke.
     */
    public static Keystroke toCanonicalKeystroke(String reading) {
        return buildKeystrokes(reading, 1).poll();
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
    public static List<Keystroke> toKeyStrokes(String reading, int maxExpansions) {
        return buildKeystrokes(reading, maxExpansions).stream()
                .sorted(reverseOrder())
                .collect(toList());
    }

    public static List<Keystroke> toEdgeNGrams(List<Keystroke> keyStrokes) {
        // There's duplicates since each generated keystrokes can have different weight even if stroke is the same
        // So, collect to map deduplicating
        Map<String, Keystroke> edgeNGrams = keyStrokes.stream()
                .flatMap(KeystrokeUtil::edgeNgrams)
                .collect(toMap(
                        Keystroke::getKey,
                        Function.identity(),
                        BinaryOperator.minBy(Comparator.<Keystroke>reverseOrder())));

        return edgeNGrams.values().stream()
                .sorted(Comparator.<Keystroke>reverseOrder().thenComparingInt(ks -> ks.getKey().length()))
                .collect(toList());
    }

    private static Stream<Keystroke> edgeNgrams(Keystroke keystroke) {
        return IntStream.rangeClosed(1, keystroke.getKey().length())
                .boxed()
                .map(i -> new Keystroke(keystroke.getKey().substring(0, i), keystroke.getWeight()))
                .collect(toList()).stream();
    }

    private static PriorityQueue<Keystroke> buildKeystrokes(String reading, int maxExpansions) {
        PriorityQueue<Keystroke> keyStrokes = new PriorityQueue<>();

        int pos = 0;
        int len = reading.length();
        while (pos < len) {
            List<Keystroke> keyStrokeFragments = null;

            if (isKatakana(reading.charAt(pos))) {
                // Try multi characters lookup.
                // ("キャ", "キュ"..etc)

                for (int i = 3; i > 0; i--) {
                    keyStrokeFragments = lookup(reading, pos, i);
                    if (keyStrokeFragments != null) {
                        pos += i;
                        break;
                    }
                }
                // There are Katakana characters that aren't in KEY_STROKE_MAP.
                if (keyStrokeFragments == null) {
                    keyStrokeFragments = Collections.singletonList(new Keystroke(reading.substring(pos, pos + 1), 1));
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

                keyStrokeFragments = Collections.singletonList(new Keystroke(reading.substring(from, pos), pos - from));
            }

            keyStrokes = append(keyStrokes, keyStrokeFragments, maxExpansions);
        }

        return keyStrokes;
    }

    private static List<Keystroke> lookup(String reading, int pos, int len) {
        if (pos + len > reading.length()) {
            return null;
        }
        return KEY_STROKE_MAP.get(reading.substring(pos, pos + len));
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
                    .collect(toCollection(PriorityQueue::new));
        }

        return prefixes.stream()
                .flatMap(pfx -> suffixes.stream()
                        .map(sfx -> Keystroke.concatenate(pfx, sfx, baseWeight)))
                .collect(new KeystrokeCollector(maxExpansions));

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
