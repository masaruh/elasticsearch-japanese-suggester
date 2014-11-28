package org.elasticsearch.plugin;

import org.elasticsearch.analysis.KuromojiSuggestTokenizerFactory;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.mapper.JapaneseCompletionTypeModule;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.search.suggest.SuggestModule;
import org.elasticsearch.search.suggest.completion.JapaneseCompletionSuggester;

import java.util.Collection;

public class JapaneseSuggesterPlugin extends AbstractPlugin {
    @Override
    public String name() {
        return "japanese-suggester";
    }

    @Override
    public String description() {
        return "Suggester for Japanese query completion";
    }

    public void onModule(SuggestModule suggestModule) {
        suggestModule.registerSuggester(JapaneseCompletionSuggester.class);
    }

    @Override
    public Collection<Class<? extends Module>> indexModules() {
        Collection<Class<? extends Module>> modules = Lists.newArrayList();
        modules.add(JapaneseCompletionTypeModule.class);
        return modules;
    }

    public void onModule(AnalysisModule module) {
        module.addTokenizer("kuromoji_suggest", KuromojiSuggestTokenizerFactory.class);
    }

}
