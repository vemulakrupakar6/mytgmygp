package com.tg.mytgmygp.Repository;

import com.tg.mytgmygp.Entity.mandals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MandalRepository extends JpaRepository<mandals, Integer> {

    List<mandals> findByDistrict_DistrictId(Integer districtId);
}