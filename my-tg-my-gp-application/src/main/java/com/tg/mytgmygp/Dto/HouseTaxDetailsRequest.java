package com.tg.mytgmygp.Dto;

public record HouseTaxDetailsRequest(
        Integer districtId,
        Integer mandalId,
        Integer gramPanchayatId,
        String serviceCode,
        Long assessmentNo,
        String searchMethod,
        String searchValue
) {
}