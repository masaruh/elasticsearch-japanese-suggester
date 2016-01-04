package org.elasticsearch.plugin;

import org.elasticsearch.analysis.KuromojiSuggestTokenizerFactory;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.mapper.core.CompletionFieldMapper;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.suggest.completion.JapaneseCompletionSuggester;

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

    public void onModule(AnalysisModule module) {
        module.addTokenizer("kuromoji_suggest", KuromojiSuggestTokenizerFactory.class);
    }

    public void onModule(SearchModule module) {
        module.registerSuggester("japanese_completion", JapaneseCompletionSuggester.class);
    }

}
