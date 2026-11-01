package com.tg.mytgmygp.Controller;

import com.tg.mytgmygp.Dto.GramPanchayatResponse;
import com.tg.mytgmygp.Service.GramPanchayatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mandals")
public class GramPanchayatController {

    private final GramPanchayatService gramPanchayatService;

    public GramPanchayatController(
            GramPanchayatService gramPanchayatService) {

        this.gramPanchayatService = gramPanchayatService;
    }

    @GetMapping("/{mandalId}/gram-panchayats")
    public List<GramPanchayatResponse> getGramPanchayatsByMandal(
            @PathVariable Integer mandalId) {

        return gramPanchayatService
                .getGramPanchayatsByMandal(mandalId);
    }
}