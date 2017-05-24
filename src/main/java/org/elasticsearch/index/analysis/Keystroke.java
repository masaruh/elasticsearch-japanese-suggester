package org.elasticsearch.index.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

class Keystroke implements Comparable<Keystroke> {
    private final String key;
    private final int weight;
    private final List<Integer> weightHistory;

    Keystroke(String key, int weight) {
        this.key = key;
        this.weight = weight;
        this.weightHistory = Collections.singletonList(weight);
    }

    private Keystroke(String key, int weight, List<Integer> history) {
        this.key = key;
        this.weight = weight;
        List<Integer> tmp = new ArrayList<>(history);
        tmp.add(weight);
        this.weightHistory = Collections.unmodifiableList(tmp);
    }

    String getKey() {
        return this.key;
    }

    int getWeight() {
        return weight;
    }

    List<Integer> getWeightHistory() {
        return weightHistory;
    }

    // Order by:
    // 1. weight descending
    // 2. prefix (weight excluding the last keystroke) weight descending
    // 3. key descending
    @Override
    public int compareTo(Keystroke other) {
        int result = other.weight - this.weight;
        if (result != 0) {
            return result;
        }

        for (int i = 0; i < Math.min(this.weightHistory.size(), other.weightHistory.size()); i++) {
            result = other.weightHistory.get(i) - this.weightHistory.get(i);
            if (result != 0) {
                return result;
            }
        }
        return other.key.compareTo(this.key);
    }

    static Keystroke concatenate(Keystroke k1, Keystroke k2, int extraWeight) {
        return new Keystroke(k1.getKey() + k2.getKey(), k1.getWeight() + k2.getWeight() + extraWeight, k1.weightHistory);
    }

    @Override
    public String toString() {
        return key + "(" + weight + ":" + weightHistory.stream().map(Object::toString).collect(joining(",")) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Keystroke keystroke = (Keystroke) o;
        return weight == keystroke.weight &&
                Objects.equals(key, keystroke.key) &&
                Objects.equals(weightHistory, keystroke.weightHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, weight, weightHistory);
    }
}
