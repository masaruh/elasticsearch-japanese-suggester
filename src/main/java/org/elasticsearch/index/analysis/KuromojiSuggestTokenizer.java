package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A tokenizer that generates key strokes from input by utilizing {@link JapaneseTokenizer}.
 */
public class KuromojiSuggestTokenizer extends Tokenizer {

    private static final Comparator<String> LENGTH_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
            int res = s1.length() - s2.length();

            if (res != 0) {
                return res;
            }

            return s1.compareTo(s2);
        }
    };

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLengthAtt = addAttribute(PositionLengthAttribute.class);
    private final WeightAttribute weightAtt = addAttribute(WeightAttribute.class);

    private final JapaneseTokenizer kuromoji;

    private final boolean expand;
    private final int maxExpansions;
    private final boolean edgeNGram;

    private Iterator<KeystrokeUtil.Keystroke> keystrokes;
    private boolean first = true; // First token or not.

    public KuromojiSuggestTokenizer(boolean expand, int maxExpansions, boolean edgeNGram) {
        this.expand = expand;
        this.maxExpansions = maxExpansions;
        this.edgeNGram = edgeNGram;

        this.kuromoji = new JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL);
    }

    @Override
    public final boolean incrementToken() throws IOException {
        if (!this.keystrokes.hasNext()) {
            return false;
        }

        clearAttributes();

        KeystrokeUtil.Keystroke keystroke = this.keystrokes.next();
        String stroke = keystroke.getKey();
        this.termAtt.append(stroke);
        this.offsetAtt.setOffset(0, stroke.length());
        this.weightAtt.setWeight(keystroke.getWeight());
        this.weightAtt.setWeights(keystroke.getWeightHistory());

        if (this.first) {
            this.posIncAtt.setPositionIncrement(1);
            this.first = false;
        } else {
            this.posIncAtt.setPositionIncrement(0);
        }

        this.posLengthAtt.setPositionLength(1);
        return true;
    }


    @Override
    public void close() throws IOException {
        super.close();
        this.kuromoji.close();
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.kuromoji.setReader(this.input);
        this.kuromoji.reset();

        StringBuilder readingBuilder = new StringBuilder();
        StringBuilder surfaceFormBuilder = new StringBuilder();
        while (this.kuromoji.incrementToken()) {
            String readingFragment = this.kuromoji.getAttribute(ReadingAttribute.class).getReading();
            String surfaceFormFragment = this.kuromoji.getAttribute(CharTermAttribute.class).toString();

            if (readingFragment == null) {
                // Use surface form if kuromoji can't produce reading.
                readingFragment = surfaceFormFragment;
            }
            readingBuilder.append(readingFragment);
            surfaceFormBuilder.append(surfaceFormFragment);
        }

        // It may contain Hiragana. Convert it to Katakana.
        hiraganaToKatakana(readingBuilder);

        List<KeystrokeUtil.Keystroke> keyStrokes;
        if (this.expand) {
            keyStrokes = KeystrokeUtil.toKeyStrokes(readingBuilder.toString(), this.maxExpansions);
        } else {
            keyStrokes = new ArrayList<>();
            keyStrokes.add(KeystrokeUtil.toCanonicalKeystroke(readingBuilder.toString()));
        }

        // Add original input as "keystroke"
        // Kuromoji doesn't always produce correct reading. So, we use original input for matching too.
        String surfaceFormString = surfaceFormBuilder.toString();
        KeystrokeUtil.Keystroke surfaceForm = new KeystrokeUtil.Keystroke(surfaceFormString, surfaceFormString.length());
        if (!keyStrokes.contains(surfaceForm)) {
            keyStrokes.add(surfaceForm);
        }

        this.keystrokes = this.edgeNGram ? toEdgeNGrams(keyStrokes) : keyStrokes.iterator();
        this.first = true;
    }

    private void hiraganaToKatakana(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c >= 'ぁ' && c <= 'ん') {
                sb.setCharAt(i, (char)(c - 'ぁ' + 'ァ'));
            }
        }
    }

    private Iterator<KeystrokeUtil.Keystroke> toEdgeNGrams(List<KeystrokeUtil.Keystroke> keyStrokes) {
//        Set<String> edgeNGrams = new TreeSet<>(LENGTH_COMPARATOR);
//        for (String keyStroke : keyStrokes) {
//            for (int i = 0; i < keyStroke.length(); i++) {
//                edgeNGrams.add(keyStroke.substring(0, i + 1));
//            }
//        }
//
//        return edgeNGrams.iterator();
        return null;
    }

    @Override
    public void end() throws IOException {
        super.end();
        this.kuromoji.end();
    }
}
