package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.domain.SnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessImpactSnapshotRepo extends JpaRepository<AccessImpactSnapshot, Long> {
    List<AccessImpactSnapshot> findByNetworkIdOrderByVersionNoDesc(Long networkId);
    Optional<AccessImpactSnapshot> findByNetworkIdAndStatus(Long networkId, SnapshotStatus status);
    Optional<AccessImpactSnapshot> findTopByNetworkIdOrderByVersionNoDesc(Long networkId);
    long countByNetworkId(Long networkId);
}
