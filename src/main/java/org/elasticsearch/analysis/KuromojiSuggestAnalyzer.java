package org.elasticsearch.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;

import java.io.Reader;
import java.text.Normalizer;

public abstract class KuromojiSuggestAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = createTokenizer();
        TokenStream tokenStream = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, tokenStream);
    }

    @Override
    protected Reader initReader(String fieldName, Reader reader) {
        return new UnicodeNormalizationCharFilter(reader, Normalizer.Form.NFKC, false);
    }

    protected abstract Tokenizer createTokenizer();

    public static class IndexKuromojiSuggestAnalyzer extends KuromojiSuggestAnalyzer {
        @Override
        protected Tokenizer createTokenizer() {
            return new KuromojiSuggestTokenizer(true, 512, false);
        }
    }

    public static class SearchKuromojiSuggestAnalyzer extends KuromojiSuggestAnalyzer {
        @Override
        protected Tokenizer createTokenizer() {
            return new KuromojiSuggestTokenizer(false, 512, false);
        }
    }
}
