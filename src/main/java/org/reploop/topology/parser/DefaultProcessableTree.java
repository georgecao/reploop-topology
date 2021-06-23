package org.reploop.topology.parser;

import org.reploop.topology.model.Processable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Component
public class DefaultProcessableTree implements ProcessableTree {

    public <E extends Processable> Map<Integer, Integer> processPerHost(List<E> entities) {
        Map<Integer, Integer> pidMap = new HashMap<>();
        // A network file or a  process only has one parent id, no matter how many times it appears.
        Map<Integer, E> entityMap = entities.stream()
                .collect(toMap(Processable::getPid, p -> p, (t, t2) -> t));
        for (E entity : entities) {
            E parent = parent(entityMap, entity);
            Integer pid = entity.getPid();
            pidMap.put(pid, parent.getPid());
        }
        return pidMap;
    }

    private <E extends Processable> E parent(Map<Integer, E> entityMap, E entity) {
        Integer ppid = entity.getPpid();
        E parent;
        if (null == ppid || 0 == ppid || 1 == ppid || null == (parent = entityMap.get(ppid))) {
            return entity;
        } else {
            return parent(entityMap, parent);
        }
    }

    @Override
    public <E extends Processable> Map<String, Map<Integer, Integer>> reduce(List<E> entities) {
        Map<String, Map<Integer, Integer>> hostPidMap = new HashMap<>();
        Map<String, List<E>> groups = entities.stream()
                .collect(Collectors.groupingBy(Processable::getHost, Collectors.toList()));
        for (Map.Entry<String, List<E>> entry : groups.entrySet()) {
            Map<Integer, Integer> pidMap = processPerHost(entry.getValue());
            hostPidMap.put(entry.getKey(), pidMap);
        }
        return hostPidMap;

    }

    /**
     * Some sub process may miss from `ps -ef` 's result.
     */
    @Override
    public <E extends Processable, T extends Processable> Map<String, Map<Integer, Integer>> reduce(List<E> entities,
                                                                                                    List<T> entities2) {
        Map<String, Map<Integer, Integer>> master = reduce(entities);
        Map<String, Map<Integer, Integer>> slave = reduce(entities2);
        slave.forEach((host, ppidMap) -> {
            Map<Integer, Integer> pidMap = master.computeIfAbsent(host, s -> new HashMap<>());
            ppidMap.forEach((pid, ppid) -> {
                Integer mid = pidMap.getOrDefault(ppid, ppid);
                pidMap.put(pid, mid);
            });
        });
        return master;
    }

}
