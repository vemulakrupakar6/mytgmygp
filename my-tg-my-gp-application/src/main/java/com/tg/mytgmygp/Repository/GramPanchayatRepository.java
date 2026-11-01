package com.tg.mytgmygp.Repository;

import com.tg.mytgmygp.Entity.gram_panchayats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GramPanchayatRepository
        extends JpaRepository<gram_panchayats, Integer> {

    List<gram_panchayats> findByMandal_MandalId(Integer mandalId);
}