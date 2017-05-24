package org.elasticsearch.index.analysis;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

import java.util.List;

public class WeightAttributeImpl  extends AttributeImpl implements WeightAttribute, Cloneable {
    private int weight;
    private List<Integer> weights;

    @Override
    public void clear() {
        weight = 0;
        weights = null;
    }

    @Override
    public void reflectWith(AttributeReflector reflector) {
        reflector.reflect(WeightAttribute.class, "weight", weight);
        reflector.reflect(WeightAttribute.class, "weights", weights);
    }

    @Override
    public void copyTo(AttributeImpl target) {
        WeightAttribute t = (WeightAttribute) target;
        t.setWeight(weight);
        t.setWeights(weights);
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public List<Integer> getWeights() {
        return weights;
    }

    @Override
    public void setWeight(int weight) {
        this.weight = weight;
    }

    @Override
    public void setWeights(List<Integer> weights) {
        this.weights = weights;
    }
}
