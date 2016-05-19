package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.CharFilter;

import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;

public class UnicodeNormalizationCharFilter extends CharFilter {
    private final Normalizer.Form form;
    private final boolean lowerCase;

    private StringBuilder normalized = new StringBuilder();

    private final char[] buffer = new char[1024];

    private boolean read = false;
    private int position = 0;

    public UnicodeNormalizationCharFilter(Reader input, Normalizer.Form form, boolean lowerCase) {
        super(input);
        this.form = form;
        this.lowerCase = lowerCase;
    }

    @Override
    protected int correct(int i) {
        return 0;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (this.read && this.position == this.normalized.length()) {
            return -1;
        }

        if (!this.read) {
            readAllAndNormalize();
        }

        int readLength = Math.min(len, this.normalized.length() - this.position);

        this.normalized.getChars(this.position, this.position + readLength, cbuf, off);
        this.position += readLength;

        return readLength;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.position = 0;
        this.read = false;
        this.normalized = new StringBuilder();
    }

    private void readAllAndNormalize() throws IOException {
        int length;
        StringBuilder raw = new StringBuilder();
        while ((length = this.input.read(this.buffer)) != -1) {
            this.read = true;
            raw.append(this.buffer, 0, length);
        }
        String normalized = Normalizer.normalize(raw, this.form);

        this.normalized.append(this.lowerCase ? normalized.toLowerCase() : normalized);
    }
}
