package org.elasticsearch.indices.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.KuromojiSuggestAnalyzer;
import org.elasticsearch.index.analysis.KuromojiSuggestAnalyzerProvider;
import org.elasticsearch.index.analysis.KuromojiSuggestTokenizer;
import org.elasticsearch.index.analysis.PreBuiltAnalyzerProviderFactory;
import org.elasticsearch.index.analysis.PreBuiltCharFilterFactoryFactory;
import org.elasticsearch.index.analysis.PreBuiltTokenizerFactoryFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.index.analysis.UnicodeNormalizationCharFilter;

import java.io.Reader;
import java.text.Normalizer;

/**
 * Registers indices level analysis components so, if not explicitly configured,
 * will be shared among all indices.
 */
public class KuromojiSuggestIndicesAnalysis extends AbstractComponent {
    @Inject
    public KuromojiSuggestIndicesAnalysis(Settings settings, IndicesAnalysisService indicesAnalysisService) {
        super(settings);
        indicesAnalysisService.analyzerProviderFactories().put(KuromojiSuggestAnalyzerProvider.INDEX_ANALYZER,
                new PreBuiltAnalyzerProviderFactory(KuromojiSuggestAnalyzerProvider.INDEX_ANALYZER, AnalyzerScope.INDICES,
                        new KuromojiSuggestAnalyzer.IndexKuromojiSuggestAnalyzer()));

        indicesAnalysisService.analyzerProviderFactories().put(KuromojiSuggestAnalyzerProvider.SEARCH_ANALYZER,
                new PreBuiltAnalyzerProviderFactory(KuromojiSuggestAnalyzerProvider.SEARCH_ANALYZER, AnalyzerScope.INDICES,
                        new KuromojiSuggestAnalyzer.SearchKuromojiSuggestAnalyzer()));

        indicesAnalysisService.charFilterFactories().put("nfkc",
                new PreBuiltCharFilterFactoryFactory(new CharFilterFactory() {
                    @Override
                    public String name() {
                        return "nfkc";
                    }

                    @Override
                    public Reader create(Reader reader) {
                        return new UnicodeNormalizationCharFilter(reader, Normalizer.Form.NFKC, false);
                    }
                }));

        indicesAnalysisService.tokenizerFactories().put("kuromoji_suggest_index",
                new PreBuiltTokenizerFactoryFactory(new TokenizerFactory() {
                    @Override
                    public String name() {
                        return "kuromoji_suggest_index";
                    }

                    @Override
                    public Tokenizer create() {
                        return new KuromojiSuggestTokenizer(true, 512, false);
                    }
                }));

        indicesAnalysisService.tokenizerFactories().put("kuromoji_suggest_search",
                new PreBuiltTokenizerFactoryFactory(new TokenizerFactory() {
                    @Override
                    public String name() {
                        return "kuromoji_suggest_search";
                    }

                    @Override
                    public Tokenizer create() {
                        return new KuromojiSuggestTokenizer(false, 512, false);
                    }
                }));
    }
}
