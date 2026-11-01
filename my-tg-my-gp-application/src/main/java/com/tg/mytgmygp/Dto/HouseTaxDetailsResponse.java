package com.tg.mytgmygp.Dto;

import com.tg.mytgmygp.Entity.house_tax_assessments;
import com.tg.mytgmygp.Entity.house_tax_current_demand;
import com.tg.mytgmygp.Entity.house_tax_arrear_details;

import java.math.BigDecimal;

public record HouseTaxDetailsResponse(
        String searchMethod,
        String searchValue,
        String serviceCode,
        String serviceName,
        String districtName,
        String mandalName,
        String gramPanchayatName,
        house_tax_assessments assessment,
        house_tax_current_demand currentDemand,
        house_tax_arrear_details arrearDetails,
        BigDecimal totalPayable
) {}