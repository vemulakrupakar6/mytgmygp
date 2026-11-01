package com.tg.mytgmygp.Repository;

import com.tg.mytgmygp.Entity.service_types;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository
        extends JpaRepository<service_types, Integer> {

    List<service_types> findByActiveTrueOrderByServiceIdAsc();

    // Add this method
    Optional<service_types> findByServiceCodeAndActiveTrue(
            String serviceCode
    );
}