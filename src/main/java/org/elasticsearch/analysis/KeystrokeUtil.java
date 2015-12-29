package org.elasticsearch.analysis;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
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
            Map<String, List<String>> tmp = mapper.readValue(in, new TypeReference<Map<String, List<String>>>(){});

            for (String key : tmp.keySet()) {
                tmp.put(key, Collections.unmodifiableList(tmp.get(key)));
            }
            KEY_STROKE_MAP = Collections.unmodifiableMap(tmp);

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Convert reading to canonical key stroke.
     * See {@link #toKeyStrokes(String, int)}.
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
    public static List<String> toKeyStrokes(String reading, int maxExpansions) {
        KeystrokeBuilder builder = buildKeystrokes(reading);
        return builder.keyStrokes(maxExpansions);
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

                    // There are Katakana characters that aren't in KEY_STROKE_MAP.
                    if (keyStrokeFragments == null) {
                        keyStrokeFragments = Lists.newArrayList(ch);
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
        private RootNode root;

        public void append(List<String> keyStrokes) {
            if (this.root == null) {
                this.root = new RootNode(keyStrokes);
            } else {
                this.root.append(new Node(keyStrokes));
            }
        }

        public List<String> keyStrokes(int maxExpansions) {
            return this.root.keyStrokes(maxExpansions);
        }

        public String canonicalKeyStroke() {
            return this.root.keyStroke();
        }
    }

    private static class Node {
        protected final List<String> keyStrokes;
        protected Node child;

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

        public List<String> keyStrokes(int maxExpansions) {
            if (this.child == null) {
                return this.keyStrokes;
            }

            List<String> result = Lists.newArrayList();
            for (String tail : this.child.keyStrokes(maxExpansions)) {
                for (String stroke : this.keyStrokes) {
                    result.add(stroke + tail);
                    if (result.size() >= maxExpansions) {
                        return result;
                    }
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

    private static class RootNode extends Node {
        public RootNode(List<String> keyStrokes) {
            super(keyStrokes);
        }

        @Override
        public List<String> keyStrokes(int maxExpansions) {
            // If it doesn't have child, create new list (keyStrokes is original contents of KEY_STROKE_MAP).
            if (this.child == null) {
                List<String> result = Lists.newArrayList();
                result.addAll(this.keyStrokes);
                return result;
            }
            return super.keyStrokes(maxExpansions);
        }
    }
}
