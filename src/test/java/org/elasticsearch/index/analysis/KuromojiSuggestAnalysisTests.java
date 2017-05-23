package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.plugin.JapaneseSuggesterPlugin;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;

public class KuromojiSuggestAnalysisTests extends ESTestCase {

    private static final String INPUT = "シュークリーム";
    // Original + key stroke variations.
    private static final List<String> KEY_STROKES = Arrays.asList(
            "シュークリーム", "syu-kuri-mu", "shu-kuri-mu", "sixyu-kuri-mu", "shixyu-kuri-mu");

    public void testSimpleSearchAnalyzer() throws IOException {

        testTokenization(getSearchAnalyzer(), INPUT, KEY_STROKES.subList(0, 2));
    }

    public void testDedupInput() throws IOException {
        testTokenization(getIndexAnalyzer(), "aa", Collections.singletonList("aa"));
    }

    public void testKanjiAndAlphaNumeric() throws IOException {
        testTokenization(getIndexAnalyzer(), "2015年", Arrays.asList("2015年", "2015nen"));
        testTokenization(getIndexAnalyzer(), "第138回", Arrays.asList("第138回", "dai138kai"));
        testTokenization(getIndexAnalyzer(), "A型", Arrays.asList("a型", "agata"));
    }

    public void testExpansionOrder() throws IOException {
        testTokenization(getIndexAnalyzer(),
                "ジョジョ",
                Arrays.asList("jojo", "jozyo", "zyojo", "jojixyo", "zyozyo", "jixyojo", "jozixyo", "zyojixyo",
                        "jixyozyo", "zixyojo", "zyozixyo", "jixyojixyo", "zixyozyo", "jixyozixyo", "zixyojixyo", "zixyozixyo", "ジョジョ"),
                true);

        testTokenization(getSearchAnalyzer(),
                "ジョジョ",
                Arrays.asList("jojo", "ジョジョ"),
                true);

        testTokenization(getSearchAnalyzer(),
                "あいう",
                Arrays.asList("aiu", "あいう"),
                true);

    }

    private void testTokenization(Analyzer analyzer, String input, List<String> expected) throws IOException {
        testTokenization(analyzer, input, expected, false);
    }

    private void testTokenization(Analyzer analyzer, String input, List<String> expected, boolean ordered) throws IOException {
        TokenStream stream = analyzer.tokenStream("dummy", input);
        List<String> result = readStream(stream);
        stream.close();
        if (ordered) {
            assertThat(result, equalTo(expected));
        } else {
            assertThat(new HashSet<>(result), equalTo(new HashSet<>(expected)));
        }
    }

    private List<String> readStream(TokenStream stream) throws IOException {
        stream.reset();

        List<String> result = new ArrayList<>();
        while (stream.incrementToken()) {
            result.add(stream.getAttribute(CharTermAttribute.class).toString());
        }

        return result;
    }

    private Analyzer getIndexAnalyzer() throws IOException {
        return getAnalysisService().indexAnalyzers.get(KuromojiSuggestAnalyzerProvider.INDEX_ANALYZER);
    }

    private Analyzer getSearchAnalyzer() throws IOException {
        return getAnalysisService().indexAnalyzers.get(KuromojiSuggestAnalyzerProvider.SEARCH_ANALYZER);
    }

    private TestAnalysis getAnalysisService() throws IOException {
        return createTestAnalysis(new Index("test", "_na_"), Settings.EMPTY, new JapaneseSuggesterPlugin());
    }
}
