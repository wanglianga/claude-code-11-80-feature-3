package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.CityBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityBlockRepo extends JpaRepository<CityBlock, Long> {
    List<CityBlock> findByNetworkIdOrderByNodeCodeAsc(Long networkId);
}
