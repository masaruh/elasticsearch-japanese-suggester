package org.elasticsearch.search.suggest.completion;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;

import java.io.IOException;

public class JapaneseCompletionSuggestionBuilder extends SuggestBuilder.SuggestionBuilder<JapaneseCompletionSuggestionBuilder> {

    public JapaneseCompletionSuggestionBuilder(String name) {
        super(name, "japanese_completion");
    }

    @Override
    protected XContentBuilder innerToXContent(XContentBuilder builder, Params params) throws IOException {
        return builder;
    }
}
