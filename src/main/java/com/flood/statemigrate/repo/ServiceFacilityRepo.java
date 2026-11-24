package com.flood.statemigrate.repo;

import com.flood.statemigrate.domain.ServiceFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceFacilityRepo extends JpaRepository<ServiceFacility, Long> {
    List<ServiceFacility> findByNetworkIdOrderByFacilityCodeAsc(Long networkId);
    List<ServiceFacility> findByNetworkIdAndTypeOrderByFacilityCodeAsc(
            Long networkId, com.flood.statemigrate.domain.FacilityType type);
    Optional<ServiceFacility> findByNetworkIdAndFacilityCode(Long networkId, String facilityCode);
}
