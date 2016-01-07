package org.elasticsearch.indices.analysis;

import org.elasticsearch.common.inject.AbstractModule;

public class KuromojiSuggestIndicesAnalysisModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(KuromojiSuggestIndicesAnalysis.class).asEagerSingleton();
    }
}
