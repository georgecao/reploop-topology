package org.reploop.topology.parser;

import org.reploop.topology.model.Processable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Component
public class DefaultProcessableTree implements ProcessableTree {

    /**
     * Handle entities on a single host, so every PID is unique.
     *
     * @param entities entities on one host
     * @param <E>      type of entity
     * @return pid and it's far parent's id
     */
    public <E extends Processable> Map<Integer, Integer> processPerHost(List<E> entities) {
        Map<Integer, Integer> pidMap = new HashMap<>();
        // A network file or a  process only has one parent id, no matter how many times it appears.
        Map<Integer, E> entityMap = entities.stream()
                .collect(toMap(Processable::getPid, p -> p, (t, t2) -> t));
        for (E entity : entities) {
            E parent = parent(entityMap, entity);
            Integer pid = entity.getPid();
            Integer ppid = parent.getPid();
            pidMap.put(pid, ppid);
        }
        return pidMap;
    }

    /**
     * Find the most far parent of the entity.
     *
     * @param entityMap all entities
     * @param entity    current entity
     * @param <E>       type of entity
     * @return the parent
     */
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
        groups.forEach((host, entitiesPerHost) -> {
            Map<Integer, Integer> pidMap = processPerHost(entitiesPerHost);
            hostPidMap.put(host, pidMap);
        });
        return hostPidMap;

    }

    private <E extends Processable> void process(List<E> entities,
                                                 Consumer<Simple> consumer) {
        entities.stream()
                .map(Simple::new)
                .forEach(consumer);
    }

    /**
     * Some sub process may miss from `ps -ef` 's result.
     */
    @Override
    public <E extends Processable, T extends Processable> Map<String, Map<Integer, Integer>> reduce(List<E> entities,
                                                                                                    List<T> entities2) {
        // Entities may not have all process info,
        // So the result may not the most far parent, maybe some parent in the middle.
        // So we merge them first to get the whole.
        List<Simple> merge = new ArrayList<>();
        process(entities, merge::add);
        process(entities2, merge::add);
        return reduce(merge);
    }

    public static class Simple implements Processable {
        private String host;
        private Integer pid;
        private Integer ppid;

        public Simple(Processable p) {
            this(p.getHost(), p.getPid(), p.getPpid());
        }

        public Simple(String host, Integer pid, Integer ppid) {
            this.host = host;
            this.pid = pid;
            this.ppid = ppid;
        }

        @Override
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        @Override
        public Integer getPid() {
            return pid;
        }

        public void setPid(Integer pid) {
            this.pid = pid;
        }

        @Override
        public Integer getPpid() {
            return ppid;
        }

        public void setPpid(Integer ppid) {
            this.ppid = ppid;
        }
    }
}
