package org.reploop.topology.parser;

import org.reploop.topology.model.Processable;

import java.util.List;
import java.util.Map;

public interface ProcessableTree {
    <E extends Processable> Map<String, Map<Integer, Integer>> reduce(List<E> entities);

    <E extends Processable, T extends Processable> Map<String, Map<Integer, Integer>> reduce(List<E> entities, List<T> entities2);

}
