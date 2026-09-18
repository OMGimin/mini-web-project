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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "verdicts")
public class VerdictEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trial_id", nullable = false, unique = true)
    private TrialEntity trial;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "winner_side", columnDefinition = "trial_side")
    private TrialSide winnerSide;
    @Column(name = "a_fault_ratio", nullable = false)
    private int aFaultRatio;
    @Column(name = "b_fault_ratio", nullable = false)
    private int bFaultRatio;
    @Column(nullable = false, columnDefinition = "text")
    private String summary;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String grounds;
    @Column(name = "a_recommendation", nullable = false, columnDefinition = "text")
    private String aRecommendation;
    @Column(name = "b_recommendation", nullable = false, columnDefinition = "text")
    private String bRecommendation;
    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VerdictEntity() {
    }

    public VerdictEntity(TrialEntity trial, TrialSide winnerSide, int aFaultRatio,
                         int bFaultRatio, String summary, String grounds,
                         String aRecommendation, String bRecommendation, String promptVersion) {
        this.trial = trial;
        this.winnerSide = winnerSide;
        this.aFaultRatio = aFaultRatio;
        this.bFaultRatio = bFaultRatio;
        this.summary = summary;
        this.grounds = grounds;
        this.aRecommendation = aRecommendation;
        this.bRecommendation = bRecommendation;
        this.promptVersion = promptVersion;
    }

    public Long getId() {
        return id;
    }

    public TrialSide getWinnerSide() {
        return winnerSide;
    }

    public int getAFaultRatio() {
        return aFaultRatio;
    }

    public int getBFaultRatio() {
        return bFaultRatio;
    }

    public String getSummary() {
        return summary;
    }

    public String getGrounds() {
        return grounds;
    }

    public String getARecommendation() {
        return aRecommendation;
    }

    public String getBRecommendation() {
        return bRecommendation;
    }
}
