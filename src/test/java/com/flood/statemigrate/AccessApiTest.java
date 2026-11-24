package com.flood.statemigrate;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 路网可达性接口层：鉴权、路网登记、快照计算与版本账读取。 */
@AutoConfigureMockMvc
class AccessApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String T0 = "2026-11-24T00:00:00Z";
    private static final String T1 = "2026-11-24T01:00:00Z";
    private static final String T2 = "2026-11-24T02:00:00Z";

    private long createNetwork(String code) throws Exception {
        String body = """
                {"networkCode":"%s","name":"接口测试路网","horizonStart":"%s","horizonEnd":"%s",
                 "slotMinutes":60,"depthValidityMinutes":90}
                """.formatted(code, T0, T2);
        MvcResult r = mockMvc.perform(post("/api/access/networks").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.parse(r.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    @Test
    void accessApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/access/networks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullAccessFlowOverHttp() throws Exception {
        long nid = createNetwork("NET-API");

        // 桥（0.35m 封闭）与街区
        mockMvc.perform(post("/api/access/networks/%d/links".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"linkCode":"L-BRIDGE","name":"跨河桥","kind":"BRIDGE","direction":"BIDIRECTIONAL",
                                 "fromNode":"A","toNode":"B","lengthM":200,"depthCloseThresholdM":0.35}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("BRIDGE"));
        mockMvc.perform(post("/api/access/networks/%d/blocks".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nodeCode":"A","name":"河西","residentPopulation":1000}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/access/networks/%d/blocks".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nodeCode":"B","name":"河东","residentPopulation":500}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/access/networks/%d/facilities".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"facilityCode":"FIRE-1","name":"消防站","type":"FIRE_STATION","nodeCode":"A"}"""))
                .andExpect(status().isCreated());

        // 水深：T0 畅通、T1 超 0.35m 封桥
        mockMvc.perform(post("/api/access/networks/%d/depths".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkCode":"L-BRIDGE","observedAt":"%s","depthM":0.10}""".formatted(T0)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/access/networks/%d/depths".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkCode":"L-BRIDGE","observedAt":"%s","depthM":0.40}""".formatted(T1)))
                .andExpect(status().isCreated());

        // 首版快照
        mockMvc.perform(post("/api/access/networks/%d/snapshots/recompute".formatted(nid))
                        .with(httpBasic("operator", "operator123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.triggerType").value("INITIAL"));

        // 封路更正生成 v2，并标出受影响服务点
        mockMvc.perform(post("/api/access/networks/%d/closures".formatted(nid))
                        .with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"linkCode":"L-BRIDGE","closed":true,"effectiveFrom":"%s","effectiveTo":"%s",
                                 "reason":"权威封路"}""".formatted(T0, T2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.triggerType").value("CLOSURE_CORRECTION"));

        // 版本账与受影响服务点
        mockMvc.perform(get("/api/access/networks/%d/snapshots".formatted(nid))
                        .with(httpBasic("operator", "operator123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/access/snapshots/2/affected").with(httpBasic("operator", "operator123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerType").value("CLOSURE_CORRECTION"))
                .andExpect(jsonPath("$.affectedFacilityCodes[0]").value("FIRE-1"));
        mockMvc.perform(get("/api/access/snapshots/2/facility-impact")
                        .with(httpBasic("operator", "operator123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].facilityCode").value("FIRE-1"));
    }

    @Test
    void unknownLinkCodeIsRejected() throws Exception {
        long nid = createNetwork("NET-BAD");
        mockMvc.perform(post("/api/access/networks/%d/depths".formatted(nid))
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkCode":"NO-SUCH","observedAt":"%s","depthM":0.1}""".formatted(T0)))
                .andExpect(status().isBadRequest());
    }
}
