package org.elasticsearch.index.analysis;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
import org.elasticsearch.test.rest.yaml.ESClientYamlSuiteTestCase;
import org.elasticsearch.test.rest.yaml.parser.ClientYamlTestParseException;

import java.io.IOException;

/**
 * Created by masaru on 7/23/16.
 */
public class KuromojiSuggestAnalysisRestIT extends ESClientYamlSuiteTestCase {

    public KuromojiSuggestAnalysisRestIT(@Name("yaml") ClientYamlTestCandidate testCandidate) {
        super(testCandidate);
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() throws IOException, ClientYamlTestParseException {
        return ESClientYamlSuiteTestCase.createParameters();
    }
}