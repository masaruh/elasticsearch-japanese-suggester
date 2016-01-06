package org.elasticsearch.index.analysis;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.Version;
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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class KuromojiSuggestAnalysisTest {
    private static final String INPUT = "シュークリーム";
    // Original + key stroke variations.
    private static final List<String> KEY_STROKES = Lists.newArrayList(
            "シュークリーム", "syu-kuri-mu", "shu-kuri-mu", "sixyu-kuri-mu", "shixyu-kuri-mu");


    private static String settingTemplate;

    @BeforeClass
    public static void readTemplate() throws IOException {
        InputStream stream = KuromojiSuggestAnalysisTest.class.getClassLoader().getResourceAsStream("analyzer_configuration_template.json");
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));

        StringBuilder sb = new StringBuilder();

        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            line = br.readLine();
        }

        settingTemplate = sb.toString();
        stream.close();
    }

    @Test
    public void testNoExpandNoEdgeNGram() throws IOException {
        testTokenization(createAnalyzer(false, false), INPUT, KEY_STROKES.subList(0, 2));
    }

    @Test
    public void testExpandNoEdgeNgram() throws IOException {
        testTokenization(createAnalyzer(true, false), INPUT, KEY_STROKES);
    }

    @Test
    public void testNoExpandEdgeNgram() throws IOException {
        testTokenization(createAnalyzer(false, true), INPUT, edgeNgram(KEY_STROKES.subList(0, 2)));
    }

    @Test
    public void testExpandEdgeNgram() throws IOException {
        testTokenization(createAnalyzer(true, true), INPUT, edgeNgram(KEY_STROKES));
    }

    @Test
    public void testDedupInput() throws IOException {
        testTokenization(createAnalyzer(true, false), "aa", Lists.newArrayList("aa"));
    }

    @Test
    public void testKanjiAndAlphaNumeric() throws IOException {
        testTokenization(createAnalyzer(true, false), "2015年", Lists.newArrayList("2015年", "2015nen"));
        testTokenization(createAnalyzer(true, false), "第138回", Lists.newArrayList("第138回", "dai138kai"));
        testTokenization(createAnalyzer(true, false), "A型", Lists.newArrayList("a型", "agata"));
    }

    @Test
    public void testMaxExpansions() throws IOException {
        testTokenization(createAnalyzer(true, false), "上昇気流", 25); // without max_expansions
        testTokenization(createAnalyzer(true, 1, false), "上昇気流", 2); // 2 -> original + max_expansions=1

        testTokenization(createAnalyzer(true, false), "小学校", 13); // without max_expansions
        testTokenization(createAnalyzer(true, 5, false), "小学校", 6); // 6 -> original + max_expansions=5
    }

    @Test
    public void testExpansionOrder() throws IOException {
        testTokenization(createAnalyzer(true, false),
                "ジョジョ",
                Lists.newArrayList("jojo", "zyojo", "jozyo", "zyozyo", "zixyojo", "jozixyo", "zixyozyo", "zyozixyo", "zixyozixyo", "ジョジョ"),
                true);

        testTokenization(createAnalyzer(false, false),
                "ジョジョ",
                Lists.newArrayList("jojo", "ジョジョ"),
                true);
    }

    private List<String> edgeNgram(List<String> inputs) throws IOException {
        Set<String> result = Sets.newLinkedHashSet(); // Deduplicate.
        for (String input : inputs) {
            EdgeNGramTokenizer edgeNGramTokenizer = new EdgeNGramTokenizer(1, Integer.MAX_VALUE);
            edgeNGramTokenizer.setReader(new StringReader(input));
            result.addAll(readStream(edgeNGramTokenizer));
            edgeNGramTokenizer.close();
        }

        return Lists.newArrayList(result);
    }

    private void testTokenization(Analyzer analyzer, String input, List<String> expected) throws IOException {
        testTokenization(analyzer, input, expected, false);
    }

    private void testTokenization(Analyzer analyzer, String input, List<String> expected, boolean ordered) throws IOException {
        TokenStream stream = analyzer.tokenStream("dummy", input);
        List<String> result = readStream(stream);

        if (ordered) {
            Assert.assertThat(result, equalTo(expected));
        } else {
            Assert.assertThat(Sets.newHashSet(result), equalTo(Sets.newHashSet(expected)));
        }

        analyzer.close();
    }

    private void testTokenization(Analyzer analyzer, String input, int expected) throws IOException {
        TokenStream stream = analyzer.tokenStream("dummy", input);
        List<String> result = readStream(stream);
        Assert.assertThat(Sets.newHashSet(result), hasSize(expected));
        analyzer.close();
    }

    private List<String> readStream(TokenStream stream) throws IOException {
        stream.reset();

        List<String> result = Lists.newArrayList();
        while (stream.incrementToken()) {
            result.add(stream.getAttribute(CharTermAttribute.class).toString());
        }

        return result;
    }

    public Analyzer createAnalyzer(boolean expand, boolean edgeNGram) {
        return createAnalyzer(expand, 512, edgeNGram); // default is 512
    }

    public Analyzer createAnalyzer(boolean expand, int maxExpansions, boolean edgeNGram) {
        String analyzerSetting = settingTemplate
                .replace("%EXPAND%", "" + expand)
                .replace("%MAX_EXPANSIONS%", "" + maxExpansions)
                .replace("%EDGE_NGRAM%", "" + edgeNGram);

        Settings settings = Settings.settingsBuilder().loadFromSource(analyzerSetting)
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

        AnalysisService analysisService = injector.getInstance(AnalysisService.class);

        NamedAnalyzer analyzer = analysisService.analyzer("kuromoji_suggest");
        assertThat(analyzer.analyzer(), instanceOf(CustomAnalyzer.class));

        return analyzer;
    }
}
