package com.skala.team6.webmini.database.entity;

import com.skala.team6.webmini.common.model.TrialSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "trial_parties", uniqueConstraints = {
        @UniqueConstraint(name = "uk_trial_parties_trial_side", columnNames = {"trial_id", "side"})
})
public class TrialPartyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trial_id", nullable = false)
    private TrialEntity trial;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "trial_side")
    private TrialSide side;
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;
    @Column(nullable = false)
    private boolean ready;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TrialPartyEntity() {
    }

    public TrialPartyEntity(TrialEntity trial, TrialSide side, String displayName) {
        this.trial = trial;
        this.side = side;
        this.displayName = displayName;
        this.ready = false;
    }

    public Long getId() {
        return id;
    }

    public TrialEntity getTrial() {
        return trial;
    }

    public TrialSide getSide() {
        return side;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isReady() {
        return ready;
    }

    public void markReady() {
        this.ready = true;
    }

    public void markNotReady() {
        this.ready = false;
    }
}
