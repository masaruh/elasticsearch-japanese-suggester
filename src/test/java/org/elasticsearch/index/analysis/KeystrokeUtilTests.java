package org.elasticsearch.index.analysis;


import org.elasticsearch.test.ESTestCase;

import java.util.HashSet;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.hasSize;

public class KeystrokeUtilTests extends ESTestCase {

    public void testMappingHasNoDuplicates() {
        KeystrokeUtil.KEY_STROKE_MAP.entrySet().stream()
                .map(Map.Entry::getValue)
                .map(l -> l.stream().map(Keystroke::getKey).collect(toList()))
                .forEach(l -> {
                    assertThat("Duplicate in entry:" + l, l, hasSize(new HashSet<>(l).size()));
                });
    }
}
