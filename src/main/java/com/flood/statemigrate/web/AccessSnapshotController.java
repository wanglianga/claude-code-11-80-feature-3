package com.flood.statemigrate.web;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.service.access.AccessNetworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 影响快照读取：版本详情与“受影响服务点”便捷查询。 */
@Tag(name = "路网可达性影响-快照")
@RestController
@RequestMapping("/api/access/snapshots")
public class AccessSnapshotController {

    private final AccessNetworkService service;
    private final ObjectMapper om;

    public AccessSnapshotController(AccessNetworkService service, ObjectMapper om) {
        this.service = service;
        this.om = om;
    }

    @Operation(summary = "快照版本详情（含时变通行图与分服务区影响 JSON）")
    @GetMapping("/{snapshotId}")
    public AccessImpactSnapshot get(@PathVariable Long snapshotId) {
        return service.getSnapshot(snapshotId);
    }

    @Operation(summary = "受影响服务点：本版本相对上一版本发生可达性变化的服务点编码与缺测路段")
    @GetMapping("/{snapshotId}/affected")
    public Map<String, Object> affected(@PathVariable Long snapshotId) {
        AccessImpactSnapshot s = service.getSnapshot(snapshotId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("snapshotId", s.getId());
        body.put("versionNo", s.getVersionNo());
        body.put("status", s.getStatus().name());
        body.put("triggerType", s.getTriggerType().name());
        body.put("triggerRef", s.getTriggerRef());
        body.put("affectedFacilityCodes", read(s.getAffectedFacilityCodesJson()));
        body.put("unknownLinkCodes", read(s.getUnknownLinkCodesJson()));
        return body;
    }

    @Operation(summary = "快照内分服务区影响摘要（类型、受影响时间片、被孤立人口下界）")
    @GetMapping("/{snapshotId}/facility-impact")
    public List<Map<String, Object>> facilityImpact(@PathVariable Long snapshotId) {
        AccessImpactSnapshot s = service.getSnapshot(snapshotId);
        JsonNode root = read(s.getServiceImpactJson());
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (JsonNode f : root.path("facilities")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("facilityCode", f.path("facilityCode").asText());
            m.put("name", f.path("name").asText());
            m.put("type", f.path("type").asText());
            m.put("affectedSlotCount", f.path("affectedSlotCount").asInt());
            m.put("worstIsolatedPopulationLowerBound",
                    f.path("worstIsolatedPopulationLowerBound").asInt());
            m.put("everIsolatedBlocks", f.path("everIsolatedBlocks"));
            out.add(m);
        }
        return out;
    }

    private JsonNode read(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("解析快照 JSON 失败: " + e.getMessage(), e);
        }
    }
}
