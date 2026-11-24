package com.flood.statemigrate;

import com.flood.statemigrate.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
abstract class BaseIntegrationTest {

    @Autowired protected StreamDefinitionRepo streamRepo;
    @Autowired protected RulePackageRepo rulePackageRepo;
    @Autowired protected CheckpointManifestRepo checkpointRepo;
    @Autowired protected StreamEventRepo eventRepo;
    @Autowired protected StateProofRepo proofRepo;
    @Autowired protected MigrationPlanRepo planRepo;
    @Autowired protected DualTrackRunRepo runRepo;
    @Autowired protected DualTrackDiffRepo diffRepo;
    @Autowired protected SwitchGateRepo gateRepo;
    @Autowired protected RollbackRecordRepo rollbackRepo;
    @Autowired protected EvidenceEntryRepo evidenceRepo;
    @Autowired protected com.flood.statemigrate.repo.AccessImpactSnapshotRepo impactSnapshotRepo;
    @Autowired protected com.flood.statemigrate.repo.DepthObservationRepo depthObsRepo;
    @Autowired protected com.flood.statemigrate.repo.ClosureOverrideRepo closureOverrideRepo;
    @Autowired protected com.flood.statemigrate.repo.RoadLinkRepo roadLinkRepo;
    @Autowired protected com.flood.statemigrate.repo.CityBlockRepo cityBlockRepo;
    @Autowired protected com.flood.statemigrate.repo.ServiceFacilityRepo serviceFacilityRepo;
    @Autowired protected com.flood.statemigrate.repo.RoadNetworkRepo roadNetworkRepo;

    protected ScenarioBuilder scenario;

    @BeforeEach
    void cleanDb() {
        diffRepo.deleteAll();
        runRepo.deleteAll();
        evidenceRepo.deleteAll();
        planRepo.deleteAll();
        gateRepo.deleteAll();
        rollbackRepo.deleteAll();
        checkpointRepo.deleteAll();
        eventRepo.deleteAll();
        proofRepo.deleteAll();
        rulePackageRepo.deleteAll();
        streamRepo.deleteAll();
        impactSnapshotRepo.deleteAll();
        depthObsRepo.deleteAll();
        closureOverrideRepo.deleteAll();
        roadLinkRepo.deleteAll();
        cityBlockRepo.deleteAll();
        serviceFacilityRepo.deleteAll();
        roadNetworkRepo.deleteAll();
        scenario = new ScenarioBuilder(streamRepo, rulePackageRepo, eventRepo, checkpointRepo);
    }
}
