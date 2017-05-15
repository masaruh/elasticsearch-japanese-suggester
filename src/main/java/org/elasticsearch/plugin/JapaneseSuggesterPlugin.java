package org.elasticsearch.plugin;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.KuromojiSuggestAnalyzerProvider;
import org.elasticsearch.index.analysis.KuromojiSuggestTokenizerFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.index.analysis.UnicodeNormalizationCharFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.search.suggest.completion.JapaneseCompletionSuggestionBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class JapaneseSuggesterPlugin extends Plugin implements AnalysisPlugin, SearchPlugin {
    @Override
    public Map<String, AnalysisModule.AnalysisProvider<CharFilterFactory>> getCharFilters() {
        return singletonMap("unicode_normalize", UnicodeNormalizationCharFilterFactory::new);
    }

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
        return singletonMap("kuromoji_suggest", KuromojiSuggestTokenizerFactory::new);
    }

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
        Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> analyzers = new HashMap<>();
        analyzers.put("kuromoji_suggest_index", KuromojiSuggestAnalyzerProvider::new);
        analyzers.put("kuromoji_suggest_search", KuromojiSuggestAnalyzerProvider::new);
        return analyzers;
    }

    @Override
    public List<SuggesterSpec<?>> getSuggesters() {
        return singletonList(new SuggesterSpec<>(JapaneseCompletionSuggestionBuilder.SUGGESTION_NAME,
                JapaneseCompletionSuggestionBuilder::new, JapaneseCompletionSuggestionBuilder::fromXContent));
    }
}
