package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.DistrictResponse;
import com.tg.mytgmygp.Entity.districts;
import com.tg.mytgmygp.Repository.DistrictRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DistrictService {

    private final DistrictRepository districtRepository;

    public DistrictService(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    public List<DistrictResponse> getAllDistricts() {

        return districtRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private DistrictResponse mapToResponse(districts district) {

        return new DistrictResponse(
                district.getDistrictId(),
                district.getDistrictName()
        );
    }
}