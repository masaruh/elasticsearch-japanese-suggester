package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.CharFilter;

import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;

public class UnicodeNormalizationCharFilter extends CharFilter {
    private final Normalizer.Form form;
    private final boolean lowerCase;

    private final StringBuilder normalized = new StringBuilder();
    private final StringBuilder raw = new StringBuilder();

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
        if (off + len > this.normalized.length()) {
            readAllAndNormalize();
        }

        if (!this.read) {
            return -1;
        }

        int readLength = Math.min(len, this.normalized.length() - this.position);

        this.normalized.getChars(this.position, this.position + readLength, cbuf, off);
        this.position += readLength;

        // consumed all normalized buffer.
        if (this.position == this.normalized.length()) {
            clear();
        }

        return readLength;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        clear();
    }

    private void clear() {
        this.position = 0;
        this.normalized.delete(0, this.normalized.length());
        this.raw.delete(0, this.raw.length());
        this.read = false;
    }

    private void readAllAndNormalize() throws IOException {
        int length;
        while ((length = this.input.read(this.buffer)) != -1) {
            this.read = true;
            this.raw.append(this.buffer, 0, length);
        }
        String normalized = Normalizer.normalize(this.raw, this.form);

        this.normalized.append(this.lowerCase ? normalized.toLowerCase() : normalized);
    }
}
