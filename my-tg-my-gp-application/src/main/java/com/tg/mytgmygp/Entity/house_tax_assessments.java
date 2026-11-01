package com.tg.mytgmygp.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "house_tax_assessments")
@Getter
@Setter
@NoArgsConstructor
public class house_tax_assessments {

    @Id
    @Column(name = "assessment_no")
    private Long assessmentNo;

    @Column(name = "ptin", nullable = false, length = 50)
    private String ptin;

    @Column(name = "owner", nullable = false, length = 150)
    private String owner;

    @Column(name = "father_husband_name", length = 150)
    private String fatherHusbandName;

    @Column(name = "house_no", length = 50)
    private String houseNo;

    @Column(name = "building_usage", length = 50)
    private String buildingUsage;

    @Column(name = "exemption", length = 50)
    private String exemption;

    @Column(name = "dt_of_asmt")
    private LocalDate dtOfAsmt;

    @Column(name = "court_case", length = 10)
    private String courtCase;

    @Column(name = "building_classification", length = 150)
    private String buildingClassification;

    @Column(name = "district_id", nullable = false)
    private Integer districtId;

    @Column(name = "mandal_id", nullable = false)
    private Integer mandalId;

    @Column(name = "gram_panchayat_id", nullable = false)
    private Integer gramPanchayatId;
}