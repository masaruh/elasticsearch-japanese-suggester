package org.elasticsearch.search.suggest.completion;

import org.elasticsearch.action.suggest.SuggestResponse;
import org.elasticsearch.common.base.Function;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.JapaneseSuggesterPlugin;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ElasticsearchIntegrationTest.ClusterScope(scope= ElasticsearchIntegrationTest.Scope.TEST, numDataNodes =1)
public class JapaneseCompletionSuggesterTest extends ElasticsearchIntegrationTest {
    private static final String INDEX = "test_index";
    private static final String TYPE = "test_type";

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return ImmutableSettings.settingsBuilder().put(super.nodeSettings(nodeOrdinal)).put("plugin.types", JapaneseSuggesterPlugin.class.getName()).build();
    }

    @Test
    public void testJapaneseCompletion() throws Exception {
        createTestIndex();

        feedDocument("東京");
        feedDocument("豆腐");

        assertSuggestResult("とう", "東京", "豆腐");
        assertSuggestResult("tou", "東京", "豆腐");
        assertSuggestResult("とうf", "豆腐");
        assertSuggestResult("とうk", "東京");
        assertSuggestResult("東", "東京");
        assertSuggestResult("豆", "豆腐");
        assertSuggestResult("党", null);
    }

    public void createTestIndex() throws IOException {
        client().admin().indices().prepareCreate(INDEX)
                .setSettings(
                        jsonBuilder()
                            .startObject()
                                .startObject("index")
                                    .startObject("analysis")
                                        .startObject("analyzer")
                                            .startObject("kuromoji_suggest_index")
                                                .field("tokenizer", "kuromoji_suggest_index")
                                            .endObject()
                                            .startObject("kuromoji_suggest_search")
                                                .field("tokenizer", "kuromoji_suggest_search")
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
                                    .endObject()
                                .endObject()
                            .endObject())
                .execute().actionGet();

        client().admin().indices().preparePutMapping(INDEX).setType(TYPE)
                .setSource(
                        jsonBuilder()
                            .startObject()
                                .startObject("properties")
                                    .startObject("suggest")
                                        .field("type", "japanese_completion")
                                        .field("index_analyzer", "kuromoji_suggest_index")
                                        .field("search_analyzer", "kuromoji_suggest_search")
                                    .endObject()
                                .endObject()
                            .endObject())
                .execute().actionGet();

        ensureYellow();
    }

    private void feedDocument(String value) throws IOException {
        client().prepareIndex(INDEX, TYPE)
                .setSource(
                        jsonBuilder()
                                .startObject()
                                .field("suggest", value)
                                .endObject())
                .setRefresh(true).execute().actionGet();

    }

    private void assertSuggestResult(String input, String... expected) throws IOException {
        SuggestResponse response = client().prepareSuggest(INDEX)
                .setSuggestText(input).addSuggestion(new JapaneseCompletionSuggestionBuilder("suggestion").field("suggest"))
                .execute().actionGet();

//        System.out.println(response.getSuggest());

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
