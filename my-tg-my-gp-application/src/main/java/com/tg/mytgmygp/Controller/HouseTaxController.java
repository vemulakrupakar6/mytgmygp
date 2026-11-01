package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Dto.HouseTaxDetailsRequest;
import com.tg.mytgmygp.Dto.HouseTaxDetailsResponse;
import com.tg.mytgmygp.Dto.HouseTaxSearchRequest;
import com.tg.mytgmygp.Dto.HouseTaxSearchResponse;
import com.tg.mytgmygp.Service.HouseTaxService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/house-tax")
public class HouseTaxController {

    private final HouseTaxService houseTaxService;

    public HouseTaxController(HouseTaxService houseTaxService) {
        this.houseTaxService = houseTaxService;
    }

    @PostMapping("/search")
    public ResponseEntity<HouseTaxSearchResponse> search(
            @RequestBody HouseTaxSearchRequest request) {

        return ResponseEntity.ok(houseTaxService.search(request));
    }

    @PostMapping("/details")
    public ResponseEntity<HouseTaxDetailsResponse> getDetails(
            @RequestBody HouseTaxDetailsRequest request) {

        return ResponseEntity.ok(houseTaxService.getDetails(request));
    }
}