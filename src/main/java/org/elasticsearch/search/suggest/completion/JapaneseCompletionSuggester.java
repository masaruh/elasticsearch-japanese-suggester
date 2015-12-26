package org.elasticsearch.search.suggest.completion;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.CollectionUtil;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.text.StringText;
import org.elasticsearch.search.suggest.Suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modified version of {@link org.elasticsearch.search.suggest.completion.CompletionSuggester}.
 */
public class JapaneseCompletionSuggester extends CompletionSuggester {
    private static final ScoreComparator scoreComparator = new ScoreComparator();


    /**
     * See {@link org.elasticsearch.search.suggest.completion.CompletionSuggester#innerExecute}.
     * Only difference is that found options are filtered based on input prefix when input contains Kanji.
     */
    @Override
    protected Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> innerExecute(String name,
             CompletionSuggestionContext suggestionContext, IndexSearcher searcher, CharsRefBuilder spare) throws IOException {
        if (suggestionContext.fieldType() == null) {
            throw new ElasticsearchException("Field [" + suggestionContext.getField() + "] is not a completion suggest field");
        }
        final IndexReader indexReader = searcher.getIndexReader();
        CompletionSuggestion completionSuggestion = new CompletionSuggestion(name, suggestionContext.getSize());
        spare.copyUTF8Bytes(suggestionContext.getText());

        CompletionSuggestion.Entry completionSuggestEntry = new CompletionSuggestion.Entry(new StringText(spare.toString()), 0, spare.length());
        completionSuggestion.addTerm(completionSuggestEntry);

        String fieldName = suggestionContext.getField();
        Map<String, CompletionSuggestion.Entry.Option> results = Maps.newHashMapWithExpectedSize(indexReader.leaves().size() * suggestionContext.getSize());
        for (LeafReaderContext atomicReaderContext : indexReader.leaves()) {
            LeafReader atomicReader = atomicReaderContext.reader();
            Terms terms = atomicReader.fields().terms(fieldName);
            if (terms instanceof Completion090PostingsFormat.CompletionTerms) {
                final Completion090PostingsFormat.CompletionTerms lookupTerms = (Completion090PostingsFormat.CompletionTerms) terms;
                final Lookup lookup = lookupTerms.getLookup(suggestionContext.fieldType(), suggestionContext);
                if (lookup == null) {
                    // we don't have a lookup for this segment.. this might be possible if a merge dropped all
                    // docs from the segment that had a value in this segment.
                    continue;
                }
                List<Lookup.LookupResult> lookupResults = lookup.lookup(spare.get(), false, suggestionContext.getSize());
                for (Lookup.LookupResult res : lookupResults) {

                    final String key = res.key.toString();
                    final float score = res.value;
                    final CompletionSuggestion.Entry.Option value = results.get(key);
                    if (value == null) {
                        final CompletionSuggestion.Entry.Option option = new CompletionSuggestion.Entry.Option(new StringText(key), score, res.payload == null ? null
                                : new BytesArray(res.payload));
                        results.put(key, option);
                    } else if (value.getScore() < score) {
                        value.setScore(score);
                        value.setPayload(res.payload == null ? null : new BytesArray(res.payload));
                    }
                }
            }
        }

        // Filter options by prefix.
        // If query contains Kanji, results have to contain those Kanji.
        String input = spare.toString();
        int index = lastIndexOfKanji(input);
        if (index >= 0) {
            results = filterOptionsByPrefix(input.substring(0, index + 1), results);
        }

        final List<CompletionSuggestion.Entry.Option> options = new ArrayList<>(results.values());
        CollectionUtil.introSort(options, scoreComparator);

        int optionCount = Math.min(suggestionContext.getSize(), options.size());
        for (int i = 0 ; i < optionCount ; i++) {
            completionSuggestEntry.addOption(options.get(i));
        }

        return completionSuggestion;
    }

    private int lastIndexOfKanji(String input) {
        for (int i = input.length() - 1; i >= 0; i--) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(input.charAt(i));
            if (Character.UnicodeBlock.of(input.charAt(i)).equals(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)) {
                return i;
            }
        }

        return -1;
    }

    private Map<String, CompletionSuggestion.Entry.Option> filterOptionsByPrefix(final String prefix, Map<String, CompletionSuggestion.Entry.Option> original) {
        return Maps.filterKeys(original, new Predicate<String>() {
            @Override
            public boolean apply(String key) {
                return key.startsWith(prefix);
            }
        });
    }
}
