package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.CharsRefBuilder;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.Suggester;
import org.elasticsearch.search.suggest.SuggestionBuilder;

import java.io.IOException;

public class JapaneseCompletionSuggester extends Suggester<JapaneseCompletionSuggestionContext> {
    public static final JapaneseCompletionSuggester INSTANCE = new JapaneseCompletionSuggester();

    private static final int SIZE_FACTOR = 10;

    public JapaneseCompletionSuggester() {
    }

    @Override
    protected Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> innerExecute(String name,
         JapaneseCompletionSuggestionContext suggestion, IndexSearcher searcher, CharsRefBuilder spare) throws IOException {

        CompletionSuggestionContext delegate = suggestion.getDelegate();

        // We need to filter options by prefix.
        // If query contains Kanji, results have to contain those Kanji.
        String input = delegate.getText().utf8ToString();
        int index = lastIndexOfKanji(input);

        if (index >= 0) {
            String prefix = input.substring(0, index + 1);

            // Increase size since we need to filter options by prefix.
            int originalSize = delegate.getSize();
            delegate.setSize(originalSize * SIZE_FACTOR);

            CompletionSuggestion inner = (CompletionSuggestion) CompletionSuggester.INSTANCE.execute(name, delegate, searcher, spare);

            delegate.setSize(originalSize);
            return filteredCompletionSuggestion(inner, prefix, originalSize);

        } else {
            return CompletionSuggester.INSTANCE.execute(name, delegate, searcher, spare);
        }
    }

    private int lastIndexOfKanji(String input) {
        for (int i = input.length() - 1; i >= 0; i--) {
            if (Character.UnicodeBlock.of(input.charAt(i)).equals(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)) {
                return i;
            }
        }

        return -1;
    }

    private CompletionSuggestion filteredCompletionSuggestion(CompletionSuggestion original, String prefix, int size) {
        CompletionSuggestion.Entry innerEntry = original.getEntries().get(0); // Always one entry.

        CompletionSuggestion completionSuggestion = new CompletionSuggestion(original.getName(), size);
        CompletionSuggestion.Entry completionSuggestEntry =
                new CompletionSuggestion.Entry(innerEntry.getText(), innerEntry.getOffset(), innerEntry.getLength());
        completionSuggestion.addTerm(completionSuggestEntry);

        innerEntry.getOptions()
                .stream()
                .filter(op -> op.getText().string().startsWith(prefix))
                .limit(size)
                .forEach(completionSuggestEntry::addOption);

        return completionSuggestion;
    }

    @Override
    public SuggestionBuilder<?> innerFromXContent(QueryParseContext context) throws IOException {
        return CompletionSuggester.INSTANCE.innerFromXContent(context);
    }

    @Override
    public SuggestionBuilder<?> read(StreamInput in) throws IOException {
        return new JapaneseCompletionSuggestionBuilder(in);
    }
}
