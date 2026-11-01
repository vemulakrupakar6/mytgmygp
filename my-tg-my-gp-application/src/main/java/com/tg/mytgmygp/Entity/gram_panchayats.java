package com.tg.mytgmygp.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gram_panchayats")
@Getter
@Setter
@NoArgsConstructor
public class gram_panchayats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gram_panchayat_id")
    private Integer gramPanchayatId;

    @Column(name = "gram_panchayat_name", nullable = false)
    private String gramPanchayatName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mandal_id", nullable = false)
    private mandals mandal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}