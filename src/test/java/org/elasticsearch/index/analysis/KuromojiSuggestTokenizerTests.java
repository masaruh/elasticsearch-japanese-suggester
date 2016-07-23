package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;

public class KuromojiSuggestTokenizerTests extends ESTestCase {

    public void testMaxExpansions() throws IOException {
        testTokenization(createTokenizer(true, 512, false), "上昇気流", 25); // without max_expansions
        testTokenization(createTokenizer(true, 1, false), "上昇気流", 2); // 2 -> original + max_expansions=1

        testTokenization(createTokenizer(true, 512, false), "小学校", 13); // without max_expansions
        testTokenization(createTokenizer(true, 5, false), "小学校", 6); // 6 -> original + max_expansions=5
    }

    private Tokenizer createTokenizer(boolean expand, int maxExpansions, boolean edgeNgram) {
        return new KuromojiSuggestTokenizer(expand, maxExpansions, edgeNgram);
    }

    private void testTokenization(Tokenizer tokenizer, String input, int expected) throws IOException {
        tokenizer.setReader(new StringReader(input));
        List<String> result = readStream(tokenizer);
        assertThat(new HashSet<>(result), hasSize(expected));
        tokenizer.close();
    }

    private List<String> readStream(TokenStream stream) throws IOException {
        stream.reset();

        List<String> result = new ArrayList<>();
        while (stream.incrementToken()) {
            result.add(stream.getAttribute(CharTermAttribute.class).toString());
        }

        return result;
    }

    private List<String> edgeNgram(List<String> inputs) throws IOException {
        Set<String> result = new LinkedHashSet<>(); // Deduplicate.
        for (String input : inputs) {
            EdgeNGramTokenizer edgeNGramTokenizer = new EdgeNGramTokenizer(1, Integer.MAX_VALUE);
            edgeNGramTokenizer.setReader(new StringReader(input));
            result.addAll(readStream(edgeNGramTokenizer));
            edgeNGramTokenizer.close();
        }

        return result.stream().collect(Collectors.toList());
    }

}
