package org.elasticsearch.analysis;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.settings.IndexSettingsService;

public class KuromojiSuggestAnalyzerProvider extends AbstractIndexAnalyzerProvider<KuromojiSuggestAnalyzer> {
    public static final String INDEX_ANALYZER = "kuromoji_suggest_index";
    public static final String SEARCH_ANALYZER = "kuromoji_suggest_search";

    private final KuromojiSuggestAnalyzer analyzer;
    @Inject
    public KuromojiSuggestAnalyzerProvider(Index index, IndexSettingsService indexSettingsService,
                                           @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettingsService.indexSettings(), name, settings);

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