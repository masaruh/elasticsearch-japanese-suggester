package org.elasticsearch.analysis;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractCharFilterFactory;
import org.elasticsearch.index.settings.IndexSettingsService;

import java.io.Reader;
import java.text.Normalizer;

public class UnicodeNormalizationCharFilterFactory extends AbstractCharFilterFactory {
    private final Normalizer.Form form;
    private final boolean lowerCase;
    @Inject
    public UnicodeNormalizationCharFilterFactory(Index index, IndexSettingsService indexSettingsService,
                                                 @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettingsService.getSettings(), name);
        this.form = Normalizer.Form.valueOf(settings.get("form", "NFKC").toUpperCase());
        this.lowerCase = settings.getAsBoolean("lower_case", true);
    }

    @Override
    public Reader create(Reader tokenStream) {
        return new UnicodeNormalizationCharFilter(tokenStream, this.form, this.lowerCase);
    }
}
