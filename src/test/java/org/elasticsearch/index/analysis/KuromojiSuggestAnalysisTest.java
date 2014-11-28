package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.elasticsearch.plugin.JapaneseSuggesterPlugin;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.*;
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
        testTokenization(createAnalyzer(false, false), KEY_STROKES.subList(0, 2));
    }

    @Test
    public void testExpandNoEdgeNgram() throws IOException {
        testTokenization(createAnalyzer(true, false), KEY_STROKES);
    }

    @Test
    public void testNoExpandEdgeNgram() throws IOException {
        testTokenization(createAnalyzer(false, true), edgeNgram(KEY_STROKES.subList(0, 2)));
    }

    @Test
    public void testExpandEdgeNgram() throws IOException {
        testTokenization(createAnalyzer(true, true), edgeNgram(KEY_STROKES));
    }

    private List<String> edgeNgram(List<String> inputs) throws IOException {
        Set<String> result = Sets.newLinkedHashSet(); // Deduplicate.
        for (String input : inputs) {
            EdgeNGramTokenizer edgeNGramTokenizer = new EdgeNGramTokenizer(new StringReader(input), 1, Integer.MAX_VALUE);
            result.addAll(readStream(edgeNGramTokenizer));
            edgeNGramTokenizer.close();
        }

        return Lists.newArrayList(result);
    }

    private void testTokenization(Analyzer analyzer, List<String> expected) throws IOException {
        TokenStream stream = analyzer.tokenStream("dummy", INPUT);
        List<String> result = readStream(stream);

        // Compare two lists in order insensitive manner.
        Assert.assertThat(result, hasSize(expected.size()));
        Assert.assertThat(Sets.newHashSet(result), hasSize(result.size())); // No duplicates.
        Assert.assertThat(Sets.newHashSet(result), equalTo(Sets.newHashSet(expected)));

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

    private Analyzer createAnalyzer(boolean expand, boolean edgeNGram) {
        String analyzerSetting = settingTemplate.replace("%EXPAND%", "" + expand).replace("%EDGE_NGRAM%", "" + edgeNGram);

        Settings settings = ImmutableSettings.settingsBuilder().loadFromSource(analyzerSetting)
                .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();

        Index index = new Index("test");

        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings),
                new EnvironmentModule(new Environment(settings)),
                new IndicesAnalysisModule())
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
