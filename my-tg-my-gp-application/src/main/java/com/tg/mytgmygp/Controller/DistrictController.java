package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Dto.DistrictResponse;
import com.tg.mytgmygp.Service.DistrictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/districts")
public class DistrictController {

    private final DistrictService districtService;

    public DistrictController(DistrictService districtService) {
        this.districtService = districtService;
    }

    @GetMapping
    public List<DistrictResponse> getAllDistricts() {
        return districtService.getAllDistricts();
    }
}