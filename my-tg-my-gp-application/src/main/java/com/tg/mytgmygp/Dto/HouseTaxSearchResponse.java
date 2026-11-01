package com.tg.mytgmygp.Dto;

import java.util.List;

public record HouseTaxSearchResponse(
        String status,
        String message,
        List<HouseTaxSearchItem> records,
        HouseTaxDetailsResponse details
) {}