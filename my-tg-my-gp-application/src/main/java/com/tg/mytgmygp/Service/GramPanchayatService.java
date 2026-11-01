package com.tg.mytgmygp.Service;

import com.tg.mytgmygp.Dto.GramPanchayatResponse;
import com.tg.mytgmygp.Entity.gram_panchayats ;
import com.tg.mytgmygp.Repository.GramPanchayatRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GramPanchayatService {

    private final GramPanchayatRepository gramPanchayatRepository;

    public GramPanchayatService(
            GramPanchayatRepository gramPanchayatRepository) {

        this.gramPanchayatRepository = gramPanchayatRepository;
    }

    public List<GramPanchayatResponse> getGramPanchayatsByMandal(
            Integer mandalId) {

        return gramPanchayatRepository
                .findByMandal_MandalId(mandalId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private GramPanchayatResponse mapToResponse(
            gram_panchayats gramPanchayat) {

        return new GramPanchayatResponse(
                gramPanchayat.getGramPanchayatId(),
                gramPanchayat.getGramPanchayatName()
        );
    }
}