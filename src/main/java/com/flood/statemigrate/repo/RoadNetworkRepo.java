package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.RoadNetwork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoadNetworkRepo extends JpaRepository<RoadNetwork, Long> {
    Optional<RoadNetwork> findByNetworkCode(String networkCode);
}
