package com.giftbox.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/** 主管意见：保留在退货单下，随处理过程可回看。 */
@Entity
public class SupervisorNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReturnCase returnCase;

    private String supervisor;
    private boolean approved;
    @Lob
    private String opinion;
    private LocalDateTime createdAt;

    public SupervisorNote() {
    }

    public SupervisorNote(ReturnCase returnCase, String supervisor, boolean approved,
                          String opinion, LocalDateTime createdAt) {
        this.returnCase = returnCase;
        this.supervisor = supervisor;
        this.approved = approved;
        this.opinion = opinion;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public ReturnCase getReturnCase() { return returnCase; }
    public String getSupervisor() { return supervisor; }
    public boolean isApproved() { return approved; }
    public String getOpinion() { return opinion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
