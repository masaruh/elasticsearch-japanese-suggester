package org.elasticsearch.plugin;

import org.elasticsearch.analysis.KuromojiSuggestTokenizerFactory;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.mapper.JapaneseCompletionTypeModule;
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

    @Override
    public Collection<Module> indexModules(Settings indexSettings) {
        return Collections.<Module>singletonList(new JapaneseCompletionTypeModule());
    }

    public void onModule(AnalysisModule module) {
        module.addTokenizer("kuromoji_suggest", KuromojiSuggestTokenizerFactory.class);
    }

    public void onModule(SearchModule module) {
        module.registerSuggester("japanese_completion", JapaneseCompletionSuggester.class);
    }

}
