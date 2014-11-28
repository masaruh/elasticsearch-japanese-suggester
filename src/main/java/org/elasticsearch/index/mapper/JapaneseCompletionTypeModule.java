package org.elasticsearch.index.mapper;

import org.elasticsearch.common.inject.AbstractModule;

public class JapaneseCompletionTypeModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(RegisterJapaneseCompletionType.class).asEagerSingleton();
    }
}
