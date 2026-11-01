package com.tg.mytgmygp.Repository;

import com.tg.mytgmygp.Entity.house_tax_assessments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HouseTaxAssessmentRepository
        extends JpaRepository<house_tax_assessments, Long> {

    Optional<house_tax_assessments>
    findByAssessmentNoAndDistrictIdAndMandalIdAndGramPanchayatId(
            Long assessmentNo,
            Integer districtId,
            Integer mandalId,
            Integer gramPanchayatId
    );

    @Query("""
        SELECT h
        FROM house_tax_assessments h
        WHERE h.districtId = :districtId
          AND h.mandalId = :mandalId
          AND h.gramPanchayatId = :gpId
          AND (
              LOWER(h.owner) LIKE LOWER(CONCAT('%', :value, '%'))
              OR LOWER(h.fatherHusbandName)
                   LIKE LOWER(CONCAT('%', :value, '%'))
          )
        ORDER BY h.assessmentNo
        """)
    List<house_tax_assessments> searchByOwnerOrFatherName(
            @Param("value") String value,
            @Param("districtId") Integer districtId,
            @Param("mandalId") Integer mandalId,
            @Param("gpId") Integer gpId
    );
}