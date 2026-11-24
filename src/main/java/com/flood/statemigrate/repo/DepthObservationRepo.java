package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.DepthObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DepthObservationRepo extends JpaRepository<DepthObservation, Long> {
    List<DepthObservation> findByNetworkIdOrderByObservedAtAsc(Long networkId);
    List<DepthObservation> findByNetworkIdAndLinkCodeOrderByObservedAtAsc(Long networkId, String linkCode);
    List<DepthObservation> findByNetworkIdAndObservedAtBetweenOrderByObservedAtAsc(
            Long networkId, Instant from, Instant to);
    long countByNetworkId(Long networkId);
}
