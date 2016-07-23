package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.plugin.JapaneseSuggesterPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Assert;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@LuceneTestCase.SuppressCodecs("*") // requires custom completion format
public class JapaneseCompletionSuggesterTests extends ESIntegTestCase {

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
        assertSuggestResult(index, field, "党", (String[]) null);
    }

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
        assertSuggestResult(index, field, "小", 1, "小学校");

        // Add another document.
        i++;
        feedDocument(index, type, field, "省エネ" + i, i + 1);
        // This time it shouldn't return result (JapaneseCompletionSuggester.SIZE_FACTOR = 10).
        assertSuggestResult(index, field, "小", 1, (String[]) null);
    }

    public void testNormlization() throws IOException {
        String index = "normalization_test";
        String type = "type";
        String field = "completion_field";

        createTestIndex(index, type, field);

        feedDocument(index, type, field, "ｶﾞｷﾞｸﾞｹﾞｺﾞ");
        feedDocument(index, type, field, "ABCDE");

        assertSuggestResult(index, field, "ガ", 1, "ｶﾞｷﾞｸﾞｹﾞｺﾞ");
        assertSuggestResult(index, field, "a", 1, "ABCDE");
    }

    public void createTestIndex(String index, String type, String completionField) throws IOException {
        client().admin().indices().prepareCreate(index)
                .addMapping(type, jsonBuilder()
                            .startObject()
                                .startObject("properties")
                                    .startObject(completionField)
                                        .field("type", "completion")
                                        .field("analyzer", "kuromoji_suggest_index")
                                        .field("search_analyzer", "kuromoji_suggest_search")
                                    .endObject()
                                .endObject()
                            .endObject())
                .execute().actionGet();
    }

    private void feedDocument(String index, String type, String completionField, String value) throws IOException {
        feedDocument(index, type, completionField, value, 1);
    }

    private void feedDocument(String index, String type, String completionField, String value, int weight) throws IOException {
        IndexResponse a = client().prepareIndex(index, type)
                .setSource(
                        jsonBuilder()
                                .startObject()
                                    .startObject(completionField)
                                        .field("input", value)
                                        .field("weight", weight)
                                    .endObject()
                                .endObject())
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).execute().actionGet();
    }

    private void assertSuggestResult(String index, String completionField, String input, String... expected) throws IOException {
        assertSuggestResult(index, completionField, input, 10, expected);
    }

    private void assertSuggestResult(String index, String completionField, String input, int size, String... expected) throws IOException {
        JapaneseCompletionSuggestionBuilder prefix = new JapaneseCompletionSuggestionBuilder(completionField).prefix(input).size(size);
        SearchResponse response = client().prepareSearch(index)
                .suggest(new SuggestBuilder().addSuggestion("suggestion", prefix))
                .execute().actionGet();

        Assert.assertThat(response.getSuggest().size(), is(1));
        Suggest suggest = response.getSuggest();

        Suggest.Suggestion<Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option>> suggestion = suggest.getSuggestion("suggestion");
        Assert.assertThat(suggestion.getEntries().size(), is(1));

        Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option> entry = suggestion.getEntries().get(0);
        expected = expected == null ? new String[0] : expected;
        Assert.assertThat(extractText(entry), equalTo(Arrays.asList(expected)));
    }

    private List<String> extractText(Suggest.Suggestion.Entry<Suggest.Suggestion.Entry.Option> entry) {
        return entry.getOptions().stream().map(option -> option.getText().toString()).collect(Collectors.toList());
    }

}
