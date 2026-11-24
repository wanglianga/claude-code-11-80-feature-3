package com.flood.statemigrate.service.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 逐时精确最短路引擎（Dijkstra，权重为路段长度米）。
 *
 * 每个时间片用该时间片的通行状态独立重算一次：状态非 OPEN（CLOSED/UNKNOWN）的有向边一律阻断。
 * 因此缺测支路不会出现在任何可达路径里——缺测不是畅通，不能作为绕行路径的证据。
 * 同一套引擎在无阻断（全 OPEN）的基线上再算一次，得到正常条件下的最短路与距离，用于绕行距离变化对比。
 */
@Component
public class ShortestPathEngine {

    private record Head(String node, double dist) implements Comparable<Head> {
        @Override
        public int compareTo(Head o) {
            return Double.compare(dist, o.dist);
        }
    }

    public record PathResult(double distanceM, List<String> nodePath, List<String> edgePath) {
        static PathResult unreachable() {
            return new PathResult(Double.POSITIVE_INFINITY, List.of(), List.of());
        }

        public boolean reachable() {
            return Double.isFinite(distanceM);
        }
    }

    /** 从 source 出发的单源最短树：blockedEdgeIds 中的边不可通行。 */
    public ShortestTree shortestTree(NetworkTopology topology, String source, Set<String> blockedEdgeIds) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, NetworkTopology.DirectedEdge> predecessor = new HashMap<>();
        dist.put(source, 0d);
        PriorityQueue<Head> pq = new PriorityQueue<>();
        pq.add(new Head(source, 0d));

        while (!pq.isEmpty()) {
            Head head = pq.poll();
            if (head.dist() > dist.getOrDefault(head.node(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            for (NetworkTopology.DirectedEdge e : topology.outOf(head.node())) {
                if (blockedEdgeIds.contains(e.edgeId())) {
                    continue;
                }
                double nd = head.dist() + e.lengthM();
                if (nd < dist.getOrDefault(e.to(), Double.POSITIVE_INFINITY)) {
                    dist.put(e.to(), nd);
                    predecessor.put(e.to(), e);
                    pq.add(new Head(e.to(), nd));
                }
            }
        }
        return new ShortestTree(source, dist, predecessor);
    }

    /** 基线路径：全部路段畅通时的最短路 */
    public ShortestTree baselineTree(NetworkTopology topology, String source) {
        return shortestTree(topology, source, Set.of());
    }

    /** 单源最短树：查询到任意目标的距离与路径，并给出可达节点集合。 */
    public static class ShortestTree {
        private final String source;
        private final Map<String, Double> dist;
        private final Map<String, NetworkTopology.DirectedEdge> predecessor;

        ShortestTree(String source, Map<String, Double> dist,
                     Map<String, NetworkTopology.DirectedEdge> predecessor) {
            this.source = source;
            this.dist = dist;
            this.predecessor = predecessor;
        }

        public String source() { return source; }
        public Set<String> reachableNodes() { return Collections.unmodifiableSet(dist.keySet()); }

        public Double distanceTo(String node) {
            Double d = dist.get(node);
            return d == null ? null : Math.round(d * 100.0) / 100.0;
        }

        public PathResult pathTo(String node) {
            if (!dist.containsKey(node)) {
                return PathResult.unreachable();
            }
            List<String> edges = new ArrayList<>();
            List<String> nodes = new ArrayList<>();
            String cur = node;
            nodes.add(cur);
            while (!cur.equals(source)) {
                NetworkTopology.DirectedEdge e = predecessor.get(cur);
                if (e == null) {
                    return PathResult.unreachable();
                }
                edges.add(e.edgeId());
                cur = e.from();
                nodes.add(cur);
            }
            Collections.reverse(edges);
            Collections.reverse(nodes);
            return new PathResult(Math.round(dist.get(node) * 100.0) / 100.0,
                    new ArrayList<>(new LinkedHashSet<>(nodes)), edges);
        }
    }
}
