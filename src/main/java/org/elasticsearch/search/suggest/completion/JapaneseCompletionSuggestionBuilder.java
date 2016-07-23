package org.elasticsearch.search.suggest.completion;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.suggest.SuggestionSearchContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JapaneseCompletionSuggestionBuilder extends CompletionSuggestionBuilder {
    public static final String SUGGESTION_NAME = "japanese_completion";

    public JapaneseCompletionSuggestionBuilder(String field) {
        super(field);
    }

    public JapaneseCompletionSuggestionBuilder(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public JapaneseCompletionSuggestionBuilder prefix(String prefix) {
        super.prefix(prefix);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder prefix(String prefix, Fuzziness fuzziness) {
        super.prefix(prefix, fuzziness);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder prefix(String prefix, FuzzyOptions fuzzyOptions) {
        super.prefix(prefix);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder regex(String regex) {
        super.regex(regex);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder regex(String regex, RegexOptions regexOptions) {
        this.regex(regex);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder payload(List<String> fields) {
        super.payload(fields);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder contexts(Map<String, List<? extends ToXContent>> queryContexts) {
        super.contexts(queryContexts);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder contexts(Contexts2x contexts2x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JapaneseCompletionSuggestionBuilder analyzer(String analyzer) {
        super.analyzer(analyzer);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder text(String text) {
        super.text(text);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder size(int size) {
        super.size(size);
        return this;
    }

    @Override
    public JapaneseCompletionSuggestionBuilder shardSize(Integer shardSize) {
        super.shardSize(shardSize);
        return this;
    }

    @Override
    public String getWriteableName() {
        return SUGGESTION_NAME;
    }

    @Override
    public SuggestionSearchContext.SuggestionContext build(QueryShardContext context) throws IOException {
        CompletionSuggestionContext suggestionContext = (CompletionSuggestionContext) super.build(context);
        return new JapaneseCompletionSuggestionContext(suggestionContext, context);
    }
}
