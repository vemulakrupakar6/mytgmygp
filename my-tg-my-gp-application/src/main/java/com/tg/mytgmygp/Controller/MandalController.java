package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Dto.MandalResponse;
import com.tg.mytgmygp.Service.MandalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/districts")
public class MandalController {

    private final MandalService mandalService;

    public MandalController(MandalService mandalService) {
        this.mandalService = mandalService;
    }

    @GetMapping("/{districtId}/mandals")
    public List<MandalResponse> getMandalsByDistrict(
            @PathVariable Integer districtId) {

        return mandalService.getMandalsByDistrict(districtId);
    }
}