package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.MandalResponse;
import com.tg.mytgmygp.Entity.mandals;
import com.tg.mytgmygp.Repository.MandalRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MandalService {

    private final MandalRepository mandalRepository;

    public MandalService(MandalRepository mandalRepository) {
        this.mandalRepository = mandalRepository;
    }

    public List<MandalResponse> getMandalsByDistrict(Integer districtId) {

        return mandalRepository
                .findByDistrict_DistrictId(districtId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private MandalResponse mapToResponse(mandals mandal) {

        return new MandalResponse(
                mandal.getMandalId(),
                mandal.getMandalName()
        );
    }
}