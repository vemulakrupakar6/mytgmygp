package com.tg.mytgmygp.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "house_tax_arrear_details")
@Getter
@Setter
@NoArgsConstructor
public class house_tax_arrear_details {

    @Id
    @Column(name = "assessment_no")
    private Long assessmentNo;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "house_tax", nullable = false)
    private BigDecimal houseTax = BigDecimal.ZERO;

    @Column(name = "library_cess", nullable = false)
    private BigDecimal libraryCess = BigDecimal.ZERO;

    @Column(name = "drainage_tax", nullable = false)
    private BigDecimal drainageTax = BigDecimal.ZERO;

    @Column(name = "water_tax", nullable = false)
    private BigDecimal waterTax = BigDecimal.ZERO;

    @Column(name = "lighting_tax", nullable = false)
    private BigDecimal lightingTax = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;
}