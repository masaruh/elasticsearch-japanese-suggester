package org.elasticsearch.index.analysis;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;

public class KuromojiSuggestAnalyzerProvider extends AbstractIndexAnalyzerProvider<KuromojiSuggestAnalyzer> {
    public static final String INDEX_ANALYZER = "kuromoji_suggest_index";
    public static final String SEARCH_ANALYZER = "kuromoji_suggest_search";

    private final KuromojiSuggestAnalyzer analyzer;

    public KuromojiSuggestAnalyzerProvider(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        switch (name) {
            case INDEX_ANALYZER:
                this.analyzer = new KuromojiSuggestAnalyzer.IndexKuromojiSuggestAnalyzer();
                break;
            case SEARCH_ANALYZER:
                this.analyzer = new KuromojiSuggestAnalyzer.SearchKuromojiSuggestAnalyzer();
                break;
            default:
                throw new IllegalArgumentException("Invalid name [" + name + "]");
        }
    }

    @Override
    public KuromojiSuggestAnalyzer get() {
        return this.analyzer;
    }
}