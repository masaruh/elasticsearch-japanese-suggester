package org.elasticsearch.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;
import org.elasticsearch.index.settings.IndexSettings;

public class KuromojiSuggestTokenizerFactory extends AbstractTokenizerFactory {
    private final boolean expand;
    private final int maxExpansions;
    private final boolean edgeNGram;

    @Inject
    public KuromojiSuggestTokenizerFactory(Index index, @IndexSettings Settings indexSettings, Environment env, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);

        this.expand = settings.getAsBoolean("expand", false);
        this.maxExpansions = settings.getAsInt("max_expansions", 512);
        this.edgeNGram = settings.getAsBoolean("edge_ngram", false);
    }

    @Override
    public Tokenizer create() {
        return new KuromojiSuggestTokenizer(this.expand, this.maxExpansions, this.edgeNGram);
    }
}
