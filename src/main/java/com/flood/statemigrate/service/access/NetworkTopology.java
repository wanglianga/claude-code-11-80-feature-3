package com.flood.statemigrate.service.access;

import com.flood.statemigrate.domain.DirectionType;
import com.flood.statemigrate.domain.RoadLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路网拓扑：把路段展开为有向边（双向路段生成正向/反向两条边）。
 * edgeId 规则：linkCode#F（from→to）、linkCode#R（反向）；
 * 通行状态按 linkCode 判定，同一物理路段的两个方向共享同一时变状态。
 */
public class NetworkTopology {

    public record DirectedEdge(String edgeId, RoadLink link, String from, String to, double lengthM) {
    }

    private final List<RoadLink> links;
    private final List<DirectedEdge> edges = new ArrayList<>();
    private final Map<String, List<DirectedEdge>> adjacency = new HashMap<>();
    private final Map<String, RoadLink> linkByCode = new HashMap<>();

    public NetworkTopology(List<RoadLink> links) {
        this.links = links;
        for (RoadLink link : links) {
            linkByCode.put(link.getLinkCode(), link);
            addEdge(new DirectedEdge(link.getLinkCode() + "#F", link,
                    link.getFromNode(), link.getToNode(), link.getLengthM()));
            if (link.getDirection() == DirectionType.BIDIRECTIONAL) {
                addEdge(new DirectedEdge(link.getLinkCode() + "#R", link,
                        link.getToNode(), link.getFromNode(), link.getLengthM()));
            }
        }
    }

    private void addEdge(DirectedEdge e) {
        edges.add(e);
        adjacency.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
    }

    public List<RoadLink> links() { return links; }
    public List<DirectedEdge> edges() { return edges; }
    public RoadLink link(String code) { return linkByCode.get(code); }
    public List<DirectedEdge> outOf(String node) {
        return adjacency.getOrDefault(node, List.of());
    }
}
