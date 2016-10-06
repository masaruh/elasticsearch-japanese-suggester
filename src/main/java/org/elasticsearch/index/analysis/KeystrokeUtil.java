package org.elasticsearch.index.analysis;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A utility class that is used to generate key strokes from input string.
 */
public class KeystrokeUtil {
    private static final Map<String, List<Keystroke>> KEY_STROKE_MAP;

    static {
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

            KEY_STROKE_MAP = Collections.unmodifiableMap(expandMultiChar(ksTmp));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, List<Keystroke>> expandMultiChar(Map<String, List<Keystroke>> original) {
        Map<String, List<Keystroke>> expanded = new HashMap<>();

        for (Map.Entry<String, List<Keystroke>> entry : original.entrySet()) {
            String key = entry.getKey();
            List<Keystroke> value = entry.getValue();

            if (entry.getKey().length() == 1) {
                expanded.put(key, Collections.unmodifiableList(value));
                continue;
            }

            PriorityQueue<Keystroke> tmp = new PriorityQueue<>();
            for (int i = 0; i < key.length(); i++) {
                tmp = append(tmp, original.get(key.substring(i, i + 1)), 256, value.get(value.size() - 1).getWeight());
            }

            value.addAll(tmp);

            expanded.put(key, Collections.unmodifiableList(value));
        }
        return expanded;
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
        List<String> result = new ArrayList<>();
        PriorityQueue<Keystroke> strokes = buildKeystrokes(reading, maxExpansions);
        Keystroke stroke;
        while ((stroke = strokes.poll()) != null) {
            result.add(stroke.getKey());
        }

        Collections.reverse(result);
        return result;
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
                        keyStrokeFragments = new ArrayList<>();
                        keyStrokeFragments.add(new Keystroke(ch, 1));
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

                keyStrokeFragments = new ArrayList<>();
                keyStrokeFragments.add(new Keystroke(reading.substring(from, pos), 1));
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

    private static PriorityQueue<Keystroke> append(
            PriorityQueue<Keystroke> prefixes, List<Keystroke> suffixes, int maxExpansions, int baseWeight) {

        if (maxExpansions <= 0) {
            throw new IllegalArgumentException("maxExpansions must be > 0");
        }

        PriorityQueue<Keystroke> result = new PriorityQueue<>();

        if (prefixes.isEmpty()) {
            result.addAll(suffixes.subList(0, Math.min(suffixes.size(), maxExpansions)));
        } else {
            int count = 0;
            int maxWeight = Integer.MIN_VALUE;
            for (Keystroke prefix : prefixes) {
                for (Keystroke suffix : suffixes) {
                    Keystroke ks = Keystroke.concatenate(prefix, suffix, baseWeight);

                    if (count < maxExpansions) {
                        maxWeight = Math.max(maxWeight, ks.getWeight());
                        result.add(ks);
                        count++;
                    } else if (ks.getWeight() < maxWeight) {
                        maxWeight = Math.max(maxWeight, ks.getWeight());
                        result.poll();
                        result.add(ks);
                    }
                }
            }
        }

        return result;
    }

    private static class Keystroke implements Comparable<Keystroke> {
        private final String key;
        private final int weight;

        public Keystroke(String key, int weight) {
            this.key = key;
            this.weight = weight;
        }

        public String getKey() {
            return this.key;
        }

        public int getWeight() {
            return weight;
        }

        // Descending order.
        @Override
        public int compareTo(Keystroke other) {
            return other.weight - this.weight;
        }

        public static Keystroke concatenate(Keystroke k1, Keystroke k2, int extraWeight) {
            return new Keystroke(k1.getKey() + k2.getKey(), k1.getWeight() + k2.getWeight() + extraWeight);
        }
    }
}
