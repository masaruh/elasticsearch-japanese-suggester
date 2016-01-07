package org.elasticsearch.plugin;

import org.elasticsearch.common.inject.Module;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.KuromojiSuggestAnalyzerProvider;
import org.elasticsearch.index.analysis.KuromojiSuggestTokenizerFactory;
import org.elasticsearch.index.analysis.UnicodeNormalizationCharFilterFactory;
import org.elasticsearch.index.mapper.core.CompletionFieldMapper;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.indices.analysis.KuromojiSuggestIndicesAnalysisModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.suggest.completion.JapaneseCompletionSuggester;

import java.util.Collection;
import java.util.Collections;

public class JapaneseSuggesterPlugin extends Plugin {
    @Override
    public String name() {
        return "japanese-suggester";
    }

    @Override
    public String description() {
        return "Suggester for Japanese query completion";
    }

    public void onModule(IndicesModule indicesModule) {
        indicesModule.registerMapper("japanese_completion", new CompletionFieldMapper.TypeParser());
    }

    @Override
    public Collection<Module> nodeModules() {
        return Collections.<Module>singletonList(new KuromojiSuggestIndicesAnalysisModule());
    }

    public void onModule(AnalysisModule module) {
        module.addAnalyzer(KuromojiSuggestAnalyzerProvider.INDEX_ANALYZER, KuromojiSuggestAnalyzerProvider.class);
        module.addAnalyzer(KuromojiSuggestAnalyzerProvider.SEARCH_ANALYZER, KuromojiSuggestAnalyzerProvider.class);
        module.addTokenizer("kuromoji_suggest", KuromojiSuggestTokenizerFactory.class);
        module.addCharFilter("unicode_normalize", UnicodeNormalizationCharFilterFactory.class);
    }

    public void onModule(SearchModule module) {
        module.registerSuggester("japanese_completion", JapaneseCompletionSuggester.class);
    }

}
