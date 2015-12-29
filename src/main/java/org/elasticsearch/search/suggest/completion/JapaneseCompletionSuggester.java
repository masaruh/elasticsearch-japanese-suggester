package org.elasticsearch.search.suggest.completion;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.CharsRefBuilder;
import org.elasticsearch.search.suggest.Suggest;

import java.io.IOException;
import java.util.List;

/**
 * Modified version of {@link org.elasticsearch.search.suggest.completion.CompletionSuggester}.
 */
public class JapaneseCompletionSuggester extends CompletionSuggester {
    private static final int SIZE_FACTOR = 10;

    /**
     * See {@link org.elasticsearch.search.suggest.completion.CompletionSuggester#innerExecute}.
     * Only difference is that found options are filtered based on input prefix when input contains Kanji.
     */
    @Override
    protected Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> innerExecute(String name,
             CompletionSuggestionContext suggestionContext, IndexSearcher searcher, CharsRefBuilder spare) throws IOException {

        // We need to filter options by prefix.
        // If query contains Kanji, results have to contain those Kanji.
        String input = suggestionContext.getText().utf8ToString();
        int index = lastIndexOfKanji(input);

        if (index >= 0) {
            // We need to filter options.
            int originalSize = suggestionContext.getSize();
            String prefix = input.substring(0, index + 1);

            // Increase size since we need to filter options by prefix.
            suggestionContext.setSize(originalSize * SIZE_FACTOR);

            CompletionSuggestion inner = (CompletionSuggestion) super.innerExecute(name, suggestionContext, searcher, spare);

            suggestionContext.setSize(originalSize);
            return filteredCompletionSuggestion(inner, prefix, originalSize);

        } else {
            return super.innerExecute(name, suggestionContext, searcher, spare);
        }
    }

    private CompletionSuggestion filteredCompletionSuggestion(CompletionSuggestion original, String prefix, int size) {
        CompletionSuggestion.Entry innerEntry = original.getEntries().get(0); // Always one entry.

        CompletionSuggestion completionSuggestion = new CompletionSuggestion(original.getName(), size);
        CompletionSuggestion.Entry completionSuggestEntry = new CompletionSuggestion.Entry(innerEntry.getText(), innerEntry.getOffset(), innerEntry.getLength());
        completionSuggestion.addTerm(completionSuggestEntry);

        List<CompletionSuggestion.Entry.Option> options = innerEntry.getOptions();

        for (CompletionSuggestion.Entry.Option option : filterOptionByPrefix(prefix, options)) {
            if (option.getText().string().startsWith(prefix)) {
                completionSuggestEntry.addOption(option);
            }

            if (completionSuggestEntry.getOptions().size() >= size) {
                break;
            }
        }

        return completionSuggestion;
    }

    private int lastIndexOfKanji(String input) {
        for (int i = input.length() - 1; i >= 0; i--) {
            if (Character.UnicodeBlock.of(input.charAt(i)).equals(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)) {
                return i;
            }
        }

        return -1;
    }

    private Iterable<CompletionSuggestion.Entry.Option> filterOptionByPrefix(final String prefix, List<CompletionSuggestion.Entry.Option> original) {
        return Iterables.filter(original, new Predicate<CompletionSuggestion.Entry.Option>() {
            @Override
            public boolean apply(CompletionSuggestion.Entry.Option option) {
                return option.getText().toString().startsWith(prefix);
            }
        });
    }
}
