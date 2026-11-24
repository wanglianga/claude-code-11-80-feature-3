package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.ClosureOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClosureOverrideRepo extends JpaRepository<ClosureOverride, Long> {
    List<ClosureOverride> findByNetworkIdOrderByIdAsc(Long networkId);
    List<ClosureOverride> findByNetworkIdAndLinkCodeOrderByIdAsc(Long networkId, String linkCode);
}
