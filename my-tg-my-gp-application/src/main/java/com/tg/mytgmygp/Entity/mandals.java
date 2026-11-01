package com.tg.mytgmygp.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "mandals")
@Getter
@Setter
@NoArgsConstructor
public class mandals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mandal_id")
    private Integer mandalId;

    @Column(name = "mandal_name", nullable = false)
    private String mandalName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = false)
    private districts district;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}