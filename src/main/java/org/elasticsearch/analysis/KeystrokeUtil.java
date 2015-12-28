package org.elasticsearch.analysis;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * A utility class that is used to generate key strokes from input string.
 */
public class KeystrokeUtil {
    private static final Map<String, List<String>> KEY_STROKE_MAP;

    static {
        try (InputStream in = KeystrokeBuilder.class.getClassLoader().getResourceAsStream("KeyStrokeMapping.json")) {
            ObjectMapper mapper = new ObjectMapper();
            KEY_STROKE_MAP = mapper.readValue(in, new TypeReference<Map<String, List<String>>>(){});

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Convert reading to canonical key stroke.
     * See {@link #toKeyStrokes(String)}.
     *
     * @param reading reading "basically" in Katakana.
     */
    public static String toCanonicalKeystroke(String reading) {
        KeystrokeBuilder builder = buildKeystrokes(reading);
        return builder.canonicalKeyStroke();
    }

    /**
     * Convert reading to key strokes.
     * Input reading is expected to be in Katakana produced by {@link org.apache.lucene.analysis.ja.JapaneseTokenizer}.
     * However, since there are words/characters that {@link org.apache.lucene.analysis.ja.JapaneseTokenizer} can't tokenize,
     * it doesn't have to be in Katakana. (In that case, it's not really "reading", though).
     *
     * @param reading reading "basically" in Katakana.
     */
    public static List<String> toKeyStrokes(String reading) {
        KeystrokeBuilder builder = buildKeystrokes(reading);
        return builder.keyStrokes();
    }

    private static KeystrokeBuilder buildKeystrokes(String reading) {
        KeystrokeBuilder builder = new KeystrokeBuilder();

        int pos = 0;
        int len = reading.length();
        while (pos < len) {
            List<String> keyStrokeFragments = null;

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

                    // There are cases we don't find key strokes for it.
                    //  - Not Japanese
                    //  - kuromoji doesn't know the word.
                    // In that case we treat the character as key stroke.
                    if (keyStrokeFragments == null) {
                        keyStrokeFragments = Lists.newArrayList(ch);
                    }

                    pos++;
                }
            } else {
                // Consume consecutive non-Katakana input.
                int from = pos;
                while (pos < len && !isKatakana(reading.charAt(pos))) {
                    pos++;
                }

                keyStrokeFragments = Lists.newArrayList(reading.substring(from, pos));
            }

            builder.append(keyStrokeFragments);
        }

        return builder;
    }

    private static boolean isKatakana(char c) {
        return 0x30A0 <= c && c <= 0x30FF;
    }

    private static class KeystrokeBuilder {
        private Node root;

        public void append(List<String> keyStrokes) {
            Node node = new Node(keyStrokes);

            if (this.root == null) {
                this.root = node;
            } else {
                this.root.append(node);
            }
        }

        public List<String> keyStrokes() {
            return this.root.keyStrokes();
        }

        public String canonicalKeyStroke() {
            return this.root.keyStroke();
        }
    }

    private static class Node {
        private final List<String> keyStrokes;
        private Node child;

        public Node(List<String> keyStrokes) {
            this.keyStrokes = keyStrokes;
        }

        public void append(Node node) {
            if (this.child == null) {
                this.child = node;
            } else {
                this.child.append(node);
            }
        }

        public List<String> keyStrokes() {
            if (this.child == null) {
                return this.keyStrokes;
            }

            List<String> result = Lists.newArrayList();
            for (String tail : this.child.keyStrokes()) {
                for (String stroke : this.keyStrokes) {
                    result.add(stroke + tail);
                }
            }

            return result;
        }

        public String keyStroke() {
            if (this.child == null) {
                return this.keyStrokes.get(0);
            }

            return this.keyStrokes.get(0) + this.child.keyStroke();
        }
    }

}
