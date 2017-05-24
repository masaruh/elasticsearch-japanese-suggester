package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class KuromojiSuggestTokenizerTests extends ESTestCase {

    public void testMaxExpansions() throws IOException {
        testTokenizationCount(createTokenizer(true, 512, false), "上昇気流", 33); // without max_expansions
        testTokenizationCount(createTokenizer(true, 1, false), "上昇気流", 2); // 2 -> original + max_expansions=1

        testTokenizationCount(createTokenizer(true, 512, false), "小学校", 13); // without max_expansions
        testTokenizationCount(createTokenizer(true, 5, false), "小学校", 6); // 6 -> original + max_expansions=5
    }

    public void testWeight() throws IOException {
        testTokenizationWithWeight(createTokenizer(true, 1, false), "じょじょ",
                Stream.of(
                        strokeOf("jojo", 2),
                        strokeOf("じょじょ", 4)
                ).collect(Collectors.toSet()));
    }

    public void testEdgeNgram() throws IOException {
        testTokenizationWithWeight(createTokenizer(true, 256, true), "あいう",
                Stream.of(
                        strokeOf("a", 3),
                        strokeOf("ai", 3),
                        strokeOf("aiu", 3),
                        strokeOf("あ", 3),
                        strokeOf("あい", 3),
                        strokeOf("あいう", 3)
                ).collect(Collectors.toSet()));
    }

    private Tokenizer createTokenizer(boolean expand, int maxExpansions, boolean edgeNgram) {
        return new KuromojiSuggestTokenizer(expand, maxExpansions, edgeNgram);
    }

    private void testTokenizationCount(Tokenizer tokenizer, String input, int expected) throws IOException {
        tokenizer.setReader(new StringReader(input));
        List<Keystroke> result = readStream(tokenizer);
        assertThat(new HashSet<>(result), hasSize(expected));
        tokenizer.close();
    }

    private void testTokenizationWithWeight(Tokenizer tokenizer, String input, Set<Keystroke> expected) throws IOException {
        tokenizer.setReader(new StringReader(input));
        List<Keystroke> result = readStream(tokenizer);
        assertThat(new HashSet<>(result), equalTo(expected));
        tokenizer.close();
    }

    private List<Keystroke> readStream(TokenStream stream) throws IOException {
        stream.reset();

        List<Keystroke> result = new ArrayList<>();
        while (stream.incrementToken()) {
            result.add(strokeOf(
                    stream.getAttribute(CharTermAttribute.class).toString(),
                    stream.getAttribute(WeightAttribute.class).getWeight()));
        }

        return result;
    }

    private Keystroke strokeOf(String stroke, int weight) {
        return new Keystroke(stroke, weight);
    }

    private static class Keystroke {
        private String stroke;
        private int weight;
        private Keystroke(String stroke, int weight) {
            this.stroke = stroke;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return stroke + ":" + weight;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Keystroke keystroke = (Keystroke) o;

            return weight == keystroke.weight && stroke.equals(keystroke.stroke);
        }

        @Override
        public int hashCode() {
            int result = stroke.hashCode();
            result = 31 * result + weight;
            return result;
        }
    }
}
