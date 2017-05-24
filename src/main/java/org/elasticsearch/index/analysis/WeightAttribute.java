package org.elasticsearch.index.analysis;

import org.apache.lucene.util.Attribute;

import java.util.List;

public interface WeightAttribute extends Attribute {
    int getWeight();
    List<Integer> getWeights();

    void setWeight(int weight);
    void setWeights(List<Integer> weights);
}
