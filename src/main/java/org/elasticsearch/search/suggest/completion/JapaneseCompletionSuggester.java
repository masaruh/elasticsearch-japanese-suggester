package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.CollectionTerminatedException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.document.CompletionQuery;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.search.suggest.document.TopSuggestDocsCollector;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.PriorityQueue;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.mapper.CompletionFieldMapper;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.Suggester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JapaneseCompletionSuggester extends Suggester<JapaneseCompletionSuggestionContext> {
    public static final JapaneseCompletionSuggester INSTANCE = new JapaneseCompletionSuggester();

    public JapaneseCompletionSuggester() {
    }

    @Override
    protected Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> innerExecute(
            String name, JapaneseCompletionSuggestionContext japaneseCompletionSuggestionContext, IndexSearcher searcher,
            CharsRefBuilder spare) throws IOException {

        CompletionSuggestionContext suggestionContext = japaneseCompletionSuggestionContext.getDelegate();

        if (suggestionContext.getFieldType() == null) {
            return null;
        }

        // We need to filter options by prefix.
        // If query contains Kanji, results have to contain those Kanji.
        String input = suggestionContext.getText().utf8ToString();
        int index = lastIndexOfKanji(input);

        String prefix = null;
        if (index >= 0) {
            prefix = input.substring(0, index + 1);
        }

        final CompletionFieldMapper.CompletionFieldType fieldType = suggestionContext.getFieldType();
        CompletionSuggestion completionSuggestion = new CompletionSuggestion(name, suggestionContext.getSize());
        spare.copyUTF8Bytes(suggestionContext.getText());
        CompletionSuggestion.Entry completionSuggestEntry = new CompletionSuggestion.Entry(
                new Text(spare.toString()), 0, spare.length());
        completionSuggestion.addTerm(completionSuggestEntry);
        // It needs to collect more than requested since documents may be filtered.
        // Terminate collection when original size is met.
        TopSuggestDocsCollector collector =
                new FilteredTopDocumentsCollector(
                        Math.max(searcher.getIndexReader().numDocs(), suggestionContext.getSize()), suggestionContext.getSize(), prefix);
        suggest(searcher, suggestionContext.toQuery(), collector);
        int numResult = 0;
        for (TopSuggestDocs.SuggestScoreDoc suggestScoreDoc : collector.get().scoreLookupDocs()) {
            FilteredTopDocumentsCollector.SuggestDoc suggestDoc =
                    (FilteredTopDocumentsCollector.SuggestDoc) suggestScoreDoc;
            // collect contexts
            Map<String, Set<CharSequence>> contexts = Collections.emptyMap();
            if (fieldType.hasContextMappings() && suggestDoc.getContexts().isEmpty() == false) {
                contexts = fieldType.getContextMappings().getNamedContexts(suggestDoc.getContexts());
            }
            if (numResult++ < suggestionContext.getSize()) {
                CompletionSuggestion.Entry.Option option = new CompletionSuggestion.Entry.Option(suggestDoc.doc,
                        new Text(suggestDoc.key.toString()), suggestDoc.score, contexts);
                completionSuggestEntry.addOption(option);
            } else {
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

    private static void suggest(IndexSearcher searcher, CompletionQuery query, TopSuggestDocsCollector collector) throws IOException {
        query = (CompletionQuery) query.rewrite(searcher.getIndexReader());
        Weight weight = query.createWeight(searcher, collector.needsScores());
        for (LeafReaderContext context : searcher.getIndexReader().leaves()) {
            BulkScorer scorer = weight.bulkScorer(context);
            if (scorer != null) {
                try {
                    scorer.score(collector.getLeafCollector(context), context.reader().getLiveDocs());
                } catch (CollectionTerminatedException e) {
                    // collection was terminated prematurely
                    // continue with the following leaf
                }
            }
        }
    }

    /**
     * Copied from CompletionSuggester.TopDocumentsCollector.
     * TopDocumentsCollector that applies prefix filtering.
     */
    private static final class FilteredTopDocumentsCollector extends TopSuggestDocsCollector {

        /**
         * Holds a list of suggest meta data for a doc
         */
        private static final class SuggestDoc extends TopSuggestDocs.SuggestScoreDoc {

            private List<TopSuggestDocs.SuggestScoreDoc> suggestScoreDocs;

            SuggestDoc(int doc, CharSequence key, CharSequence context, float score) {
                super(doc, key, context, score);
            }

            void add(CharSequence key, CharSequence context, float score) {
                if (suggestScoreDocs == null) {
                    suggestScoreDocs = new ArrayList<>(1);
                }
                suggestScoreDocs.add(new TopSuggestDocs.SuggestScoreDoc(doc, key, context, score));
            }

            public List<CharSequence> getKeys() {
                if (suggestScoreDocs == null) {
                    return Collections.singletonList(key);
                } else {
                    List<CharSequence> keys = new ArrayList<>(suggestScoreDocs.size() + 1);
                    keys.add(key);
                    for (TopSuggestDocs.SuggestScoreDoc scoreDoc : suggestScoreDocs) {
                        keys.add(scoreDoc.key);
                    }
                    return keys;
                }
            }

            List<CharSequence> getContexts() {
                if (suggestScoreDocs == null) {
                    if (context != null) {
                        return Collections.singletonList(context);
                    } else {
                        return Collections.emptyList();
                    }
                } else {
                    List<CharSequence> contexts = new ArrayList<>(suggestScoreDocs.size() + 1);
                    contexts.add(context);
                    for (TopSuggestDocs.SuggestScoreDoc scoreDoc : suggestScoreDocs) {
                        contexts.add(scoreDoc.context);
                    }
                    return contexts;
                }
            }
        }

        private static final class SuggestDocPriorityQueue
                extends PriorityQueue<FilteredTopDocumentsCollector.SuggestDoc> {

            SuggestDocPriorityQueue(int maxSize) {
                super(maxSize);
            }

            @Override
            protected boolean lessThan(FilteredTopDocumentsCollector.SuggestDoc a,
                                       FilteredTopDocumentsCollector.SuggestDoc b) {
                if (a.score == b.score) {
                    int cmp = Lookup.CHARSEQUENCE_COMPARATOR.compare(a.key, b.key);
                    if (cmp == 0) {
                        // prefer smaller doc id, in case of a tie
                        return a.doc > b.doc;
                    } else {
                        return cmp > 0;
                    }
                }
                return a.score < b.score;
            }

            public FilteredTopDocumentsCollector.SuggestDoc[] getResults() {
                int size = size();
                FilteredTopDocumentsCollector.SuggestDoc[] res =
                        new FilteredTopDocumentsCollector.SuggestDoc[size];
                for (int i = size - 1; i >= 0; i--) {
                    res[i] = pop();
                }
                return res;
            }
        }

        private final int num;
        private final int terminate;
        private final FilteredTopDocumentsCollector.SuggestDocPriorityQueue pq;
        private final Map<Integer, FilteredTopDocumentsCollector.SuggestDoc> scoreDocMap;
        private String prefix;

        public FilteredTopDocumentsCollector(int num, int terminateCount, String prefix) {
            super(1); // TODO hack, we don't use the underlying pq, so we allocate a size of 1
            this.num = num;
            this.terminate = terminateCount;
            this.scoreDocMap = new LinkedHashMap<>(terminateCount);
            this.pq = new FilteredTopDocumentsCollector.SuggestDocPriorityQueue(num);
            this.prefix = prefix;
        }

        @Override
        public int getCountToCollect() {
            // This is only needed because we initialize
            // the base class with 1 instead of the actual num
            return num;
        }


        @Override
        protected void doSetNextReader(LeafReaderContext context) throws IOException {
            super.doSetNextReader(context);
            updateResults();
        }

        private void updateResults() {
            for (FilteredTopDocumentsCollector.SuggestDoc suggestDoc : scoreDocMap.values()) {
                if (pq.insertWithOverflow(suggestDoc) == suggestDoc) {
                    break;
                }
            }
            scoreDocMap.clear();
        }

        @Override
        public void collect(int docID, CharSequence key, CharSequence context, float score) throws IOException {
            if (scoreDocMap.containsKey(docID)) {
                FilteredTopDocumentsCollector.SuggestDoc suggestDoc = scoreDocMap.get(docID);
                suggestDoc.add(key, context, score);
            } else if (scoreDocMap.size() <= terminate) {
                if (accept(key)) {
                    scoreDocMap.put(docID,
                            new FilteredTopDocumentsCollector.SuggestDoc(
                                    docBase + docID, key, context, score));
                }
            } else {
                throw new CollectionTerminatedException();
            }
        }

        private boolean accept(CharSequence key) {
            if (prefix == null) {
                return true;
            }

            for (int i = 0; i < prefix.length(); i++) {
                if (prefix.charAt(i) != key.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public TopSuggestDocs get() throws IOException {
            updateResults(); // to empty the last set of collected suggest docs
            TopSuggestDocs.SuggestScoreDoc[] suggestScoreDocs = pq.getResults();
            if (suggestScoreDocs.length > 0) {
                return new TopSuggestDocs(suggestScoreDocs.length, suggestScoreDocs, suggestScoreDocs[0].score);
            } else {
                return TopSuggestDocs.EMPTY;
            }
        }
    }
}
