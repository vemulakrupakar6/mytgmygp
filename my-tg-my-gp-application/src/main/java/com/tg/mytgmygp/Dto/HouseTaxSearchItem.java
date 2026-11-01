package com.tg.mytgmygp.Dto;

public record HouseTaxSearchItem(
        Long assessmentNo,
        String ptin,
        String owner,
        String fatherHusbandName,
        String houseNo,
        String buildingUsage
) {}