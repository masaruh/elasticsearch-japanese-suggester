package org.elasticsearch.index.mapper;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.mapper.core.CompletionFieldMapper;

/**
 * Maps "japanese_completion" type to "completion" for convenience.
 */
public class RegisterJapaneseCompletionType extends AbstractIndexComponent {
    @Inject
    public RegisterJapaneseCompletionType(Index index, Settings indexSettings, MapperService mapperService) {
        super(index, indexSettings);

        mapperService.documentMapperParser().putTypeParser("japanese_completion", new CompletionFieldMapper.TypeParser());
    }
}
