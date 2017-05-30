package org.elasticsearch.index.analysis;

import org.elasticsearch.test.ESTestCase;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.equalTo;

public class KeystrokeTests extends ESTestCase {

    public void testSort() {
        Keystroke k1 = new Keystroke("a", 1);
        Keystroke k2 = new Keystroke("ab", 2);
        Keystroke k3 = new Keystroke("abc", 3);

        List<Keystroke> result = Stream.of(k1, k2, k3).sorted().collect(toList());
        assertThat(result, equalTo(Arrays.asList(k3, k2, k1)));
    }
}
