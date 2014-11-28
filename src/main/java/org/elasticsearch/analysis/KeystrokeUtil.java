package org.elasticsearch.analysis;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.common.base.Throwables;
import org.elasticsearch.common.collect.Lists;

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
     * Convert reading to key strokes.
     * Input reading is expected to be in Katakana produced by {@link org.apache.lucene.analysis.ja.JapaneseTokenizer}.
     * However, since there are words/characters that {@link org.apache.lucene.analysis.ja.JapaneseTokenizer} can't tokenize,
     * it doesn't have to be in Katakana. (In that case, it's not really "reading", though).
     *
     * @param reading reading "basically" in Katakana.
     */
    public static List<String> toKeyStrokes(String reading) {
        KeystrokeBuilder builder = new KeystrokeBuilder();

        int pos = 0;
        int len = reading.length();
        while (pos < len) {
            List<String> keyStrokeFragments = null;

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
                    keyStrokeFragments = Lists.newArrayList(reading.substring(pos, pos + 1));
                }

                pos++;
            }

            builder.append(keyStrokeFragments);
        }

        return builder.keyStrokes();
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
    }

}
