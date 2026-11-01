package com.tg.mytgmygp.Dto;

public record HouseTaxSearchRequest(
        Integer districtId,
        Integer mandalId,
        Integer gramPanchayatId,
        String serviceCode,
        String searchMethod,
        String searchValue
) {}