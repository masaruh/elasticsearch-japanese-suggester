package org.elasticsearch.index.analysis;


import org.elasticsearch.test.ESTestCase;

import java.util.HashSet;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.hasSize;

public class KeystrokeUtilTests extends ESTestCase {

    public void testMappingHasNoDuplicates() {
        KeystrokeUtil.KEY_STROKE_MAP.values().stream()
                .map(l -> l.stream().map(Keystroke::getKey).collect(toList()))
                .forEach(l -> {
                    assertThat("Duplicate in entry:" + l, l, hasSize(new HashSet<>(l).size()));
                });
    }

    public void testMappingHasSingleHistory() {
        KeystrokeUtil.KEY_STROKE_MAP.values().forEach(l -> l.forEach(ks -> {
            assertThat("weight history > 1" + l, ks.getWeightHistory(), hasSize(1));
                }));
    }
}
