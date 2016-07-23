package org.elasticsearch.index.analysis;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;

import java.io.Reader;
import java.text.Normalizer;
import java.util.Locale;

public class UnicodeNormalizationCharFilterFactory extends AbstractCharFilterFactory {
    private final Normalizer.Form form;
    private final boolean lowerCase;

    public UnicodeNormalizationCharFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name);
        this.form = Normalizer.Form.valueOf(settings.get("form", "NFKC").toUpperCase(Locale.getDefault()));
        this.lowerCase = settings.getAsBoolean("lower_case", true);
    }

    @Override
    public Reader create(Reader tokenStream) {
        return new UnicodeNormalizationCharFilter(tokenStream, this.form, this.lowerCase);
    }
}
