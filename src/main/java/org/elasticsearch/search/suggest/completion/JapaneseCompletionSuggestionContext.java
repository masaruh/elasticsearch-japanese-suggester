package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.suggest.SuggestionSearchContext;

public class JapaneseCompletionSuggestionContext extends SuggestionSearchContext.SuggestionContext {
    private CompletionSuggestionContext delegate;

    protected JapaneseCompletionSuggestionContext(CompletionSuggestionContext completionSuggestionContext, QueryShardContext shardContext) {
        super(JapaneseCompletionSuggester.INSTANCE, shardContext);
        this.delegate = completionSuggestionContext;
    }

    @Override
    public BytesRef getText() {
        return delegate.getText();
    }

    public CompletionSuggestionContext getDelegate() {
        return delegate;
    }
}
