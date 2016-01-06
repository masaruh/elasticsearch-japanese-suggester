package org.elasticsearch.index.analysis;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.Version;
import org.elasticsearch.analysis.KuromojiSuggestAnalyzerProvider;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.elasticsearch.plugin.JapaneseSuggesterPlugin;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class KuromojiSuggestAnalysisTest {
    private static AnalysisService analysisService;

    private static final String INPUT = "シュークリーム";
    // Original + key stroke variations.
    private static final List<String> KEY_STROKES = Lists.newArrayList(
            "シュークリーム", "syu-kuri-mu", "shu-kuri-mu", "sixyu-kuri-mu", "shixyu-kuri-mu");

    @BeforeClass
    public static void createAnalysisService() {
        Settings settings = Settings.settingsBuilder()
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .put("path.home", Files.createTempDir())
                .build();


        Index index = new Index("test");
        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings),
                new EnvironmentModule(new Environment(settings)))
                .createInjector();

        AnalysisModule analysisModule = new AnalysisModule(settings, parentInjector.getInstance(IndicesAnalysisService.class));
        new JapaneseSuggesterPlugin().onModule(analysisModule);
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(index, settings),
                new IndexNameModule(index),
                analysisModule)
                .createChildInjector(parentInjector);

        analysisService = injector.getInstance(AnalysisService.class);
    }

    @Test
    public void testSimpleSearchAnalyzer() throws IOException {
        testTokenization(getSearchAnalyzer(), INPUT, KEY_STROKES.subList(0, 2));
    }

    @Test
    public void testDedupInput() throws IOException {
        testTokenization(getIndexAnalyzer(), "aa", Lists.newArrayList("aa"));
    }

    @Test
    public void testKanjiAndAlphaNumeric() throws IOException {
        testTokenization(getIndexAnalyzer(), "2015年", Lists.newArrayList("2015年", "2015nen"));
        testTokenization(getIndexAnalyzer(), "第138回", Lists.newArrayList("第138回", "dai138kai"));
        testTokenization(getIndexAnalyzer(), "A型", Lists.newArrayList("a型", "agata"));
    }

    @Test
    public void testExpansionOrder() throws IOException {
        testTokenization(getIndexAnalyzer(),
                "ジョジョ",
                Lists.newArrayList("jojo", "zyojo", "jozyo", "zyozyo", "zixyojo", "jozixyo", "zixyozyo", "zyozixyo", "zixyozixyo", "ジョジョ"),
                true);

        testTokenization(getSearchAnalyzer(),
                "ジョジョ",
                Lists.newArrayList("jojo", "ジョジョ"),
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
            assertThat(Sets.newHashSet(result), equalTo(Sets.newHashSet(expected)));
        }
    }

    private List<String> readStream(TokenStream stream) throws IOException {
        stream.reset();

        List<String> result = Lists.newArrayList();
        while (stream.incrementToken()) {
            result.add(stream.getAttribute(CharTermAttribute.class).toString());
        }

        return result;
    }

    private Analyzer getIndexAnalyzer() {
        return analysisService.analyzer(KuromojiSuggestAnalyzerProvider.INDEX_ANALYZER);
    }

    private Analyzer getSearchAnalyzer() {
        return analysisService.analyzer(KuromojiSuggestAnalyzerProvider.SEARCH_ANALYZER);
    }
}
