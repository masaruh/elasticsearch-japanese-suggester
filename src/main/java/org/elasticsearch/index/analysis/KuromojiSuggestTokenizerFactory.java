package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;

public class KuromojiSuggestTokenizerFactory extends AbstractTokenizerFactory {
    private final boolean expand;
    private final int maxExpansions;
    private final boolean edgeNGram;

    public KuromojiSuggestTokenizerFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        this.expand = settings.getAsBoolean("expand", false);
        this.maxExpansions = settings.getAsInt("max_expansions", 512);
        this.edgeNGram = settings.getAsBoolean("edge_ngram", false);
    }

    @Override
    public Tokenizer create() {
        return new KuromojiSuggestTokenizer(this.expand, this.maxExpansions, this.edgeNGram);
    }
}
