package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.RoadLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadLinkRepo extends JpaRepository<RoadLink, Long> {
    List<RoadLink> findByNetworkIdOrderByLinkCodeAsc(Long networkId);
    Optional<RoadLink> findByNetworkIdAndLinkCode(Long networkId, String linkCode);
}
