package org.elasticsearch.search.suggest.completion;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.action.suggest.SuggestResponse;
import org.elasticsearch.plugin.JapaneseSuggesterPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@LuceneTestCase.SuppressCodecs("*")
@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE)
public class JapaneseCompletionSuggesterTest extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return pluginList(JapaneseSuggesterPlugin.class);
    }

    @Override
    protected int numberOfShards() {
        return 1;
    }

    @Override
    protected int numberOfReplicas() {
        return 0;
    }

    @Test
    public void testJapaneseCompletion() throws Exception {
        String index = "simple_test";
        String type = "type";
        String field = "suggest";

        createTestIndex(index, type, field);

        feedDocument(index, type, field, "東京");
        feedDocument(index, type, field, "豆腐");
        forceMerge();

        assertSuggestResult(index, field, "とう", "東京", "豆腐");
        assertSuggestResult(index, field, "tou", "東京", "豆腐");
        assertSuggestResult(index, field, "とうf", "豆腐");
        assertSuggestResult(index, field, "とうk", "東京");
        assertSuggestResult(index, field, "東", "東京");
        assertSuggestResult(index, field, "豆", "豆腐");
        assertSuggestResult(index, field, "党", null);
    }

    @Test
    public void testPrefixFiltering() throws IOException {
        String index = "prefix_test";
        String type = "type";
        String field = "suggest";

        createTestIndex(index, type, field);

        feedDocument(index, type, field, "小学校", 1);

        int i = 1;
        for (; i < 10; i++) {
            feedDocument(index, type, field, "省エネ" + i, i + 1);
        }
        forceMerge();
        assertSuggestResult(index, field, "小", 1, "小学校");

        // Add another document.
        i++;
        feedDocument(index, type, field, "省エネ" + i, i + 1);
        // This time it shouldn't return result (JapaneseCompletionSuggester.SIZE_FACTOR = 10).
        assertSuggestResult(index, field, "小", 1, null);
    }

    public void testNormlization() throws IOException {
        String index = "normalization_test";
        String type = "type";
        String field = "suggest";

        createTestIndex(index, type, field);

        feedDocument(index, type, field, "ｶﾞｷﾞｸﾞｹﾞｺﾞ");
        feedDocument(index, type, field, "ABCDE");

        assertSuggestResult(index, field, "ガ", 1, "ｶﾞｷﾞｸﾞｹﾞｺﾞ");
        assertSuggestResult(index, field, "a", 1, "ABCDE");
    }

    public void createTestIndex(String index, String type, String completionField) throws IOException {
        client().admin().indices().prepareCreate(index)
                .setSettings(
                        jsonBuilder()
                            .startObject()
                                .startObject("index")
                                    .startObject("analysis")
                                        .startObject("analyzer")
                                            .startObject("kuromoji_suggest_index")
                                                .field("tokenizer", "kuromoji_suggest_index")
                                                .array("char_filter", "nfkc_lc")
                                            .endObject()
                                            .startObject("kuromoji_suggest_search")
                                                .field("tokenizer", "kuromoji_suggest_search")
                                                .array("char_filter", "nfkc_lc")
                                            .endObject()
                                        .endObject()
                                        .startObject("tokenizer")
                                            .startObject("kuromoji_suggest_index")
                                                .field("type", "kuromoji_suggest")
                                                .field("expand", true)
                                            .endObject()
                                            .startObject("kuromoji_suggest_search")
                                                .field("type", "kuromoji_suggest")
                                                .field("expand", false)
                                            .endObject()
                                        .endObject()
                                        .startObject("char_filter")
                                            .startObject("nfkc_lc")
                                                .field("type", "unicode_normalize")
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject())
                .execute().actionGet();

        client().admin().indices().preparePutMapping(index).setType(type)
                .setSource(
                        jsonBuilder()
                            .startObject()
                                .startObject("properties")
                                    .startObject(completionField)
                                        .field("type", "japanese_completion")
                                        .field("analyzer", "kuromoji_suggest_index")
                                        .field("search_analyzer", "kuromoji_suggest_search")
                                    .endObject()
                                .endObject()
                            .endObject())
                .execute().actionGet();

        ensureYellow();
    }

    private void feedDocument(String index, String type, String completionField, String value) throws IOException {
        feedDocument(index, type, completionField, value, 1);
    }

    private void feedDocument(String index, String type, String completionField, String value, int weight) throws IOException {
        client().prepareIndex(index, type)
                .setSource(
                        jsonBuilder()
                                .startObject()
                                    .startObject(completionField)
                                        .field("input", value)
                                        .field("output", value)
                                        .field("weight", weight)
                                    .endObject()
                                .endObject())
                .setRefresh(true).execute().actionGet();

    }

    private void assertSuggestResult(String index, String completionField, String input, String... expected) throws IOException {
        assertSuggestResult(index, completionField, input, 10, expected);
    }

    private void assertSuggestResult(String index, String completionField, String input, int size, String... expected) throws IOException {
        SuggestResponse response = client().prepareSuggest(index)
                .addSuggestion(new JapaneseCompletionSuggestionBuilder("suggestion").field(completionField).text(input).size(size))
                .execute().actionGet();

        Assert.assertThat(response.getSuggest().size(), is(1));
        Suggest suggest = response.getSuggest();

        Suggest.Suggestion<Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option>> suggestion = suggest.getSuggestion("suggestion");
        Assert.assertThat(suggestion.getEntries().size(), is(1));

        Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option> entry = suggestion.getEntries().get(0);
        expected = expected == null ? new String[0] : expected;
        Assert.assertThat(extractText(entry), equalTo((List<String>) Lists.newArrayList(expected)));
    }

    private List<String> extractText(Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option> entry) {
        return Lists.newArrayList(Iterables.transform(entry,
                new Function<Suggest.Suggestion.Entry.Option, String>() {
                    @Override
                    public String apply(Suggest.Suggestion.Entry.Option op) {
                        return op.getText().toString();
                    }
                }));
    }
}
