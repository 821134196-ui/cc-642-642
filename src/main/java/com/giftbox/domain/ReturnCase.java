package com.giftbox.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 退货办理单。同一订单允许两个柜台各自建单（各自登记/拍照/主管意见），
 * 但 complete 时以订单上的 returnState 条件更新仲裁，只有一单能真正退款入库。
 */
@Entity
public class ReturnCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CustomerOrder order;

    /** 办理柜台，如 A 柜 / B 柜 */
    private String counter;

    @Enumerated(EnumType.STRING)
    private ReturnStatus status = ReturnStatus.DRAFT;

    private LocalDateTime createdAt;
    private LocalDateTime customerConfirmedAt;
    private LocalDateTime completedAt;

    /** 完成时落定的退款与扣减快照（分），与组合价/明细严格一致 */
    private Long refundCents;
    private Long deductionCents;
    private boolean boxRestored;

    @OneToMany(mappedBy = "returnCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ReturnLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "returnCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InspectionPhoto> photos = new ArrayList<>();

    @OneToMany(mappedBy = "returnCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SupervisorNote> supervisorNotes = new ArrayList<>();

    @OneToMany(mappedBy = "returnCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundRecord> refunds = new ArrayList<>();

    public ReturnCase() {
    }

    public ReturnCase(CustomerOrder order, String counter, LocalDateTime createdAt) {
        this.order = order;
        this.counter = counter;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public String getCounter() { return counter; }
    public void setCounter(String counter) { this.counter = counter; }
    public ReturnStatus getStatus() { return status; }
    public void setStatus(ReturnStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getCustomerConfirmedAt() { return customerConfirmedAt; }
    public void setCustomerConfirmedAt(LocalDateTime t) { this.customerConfirmedAt = t; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime t) { this.completedAt = t; }
    public Long getRefundCents() { return refundCents; }
    public void setRefundCents(Long refundCents) { this.refundCents = refundCents; }
    public Long getDeductionCents() { return deductionCents; }
    public void setDeductionCents(Long deductionCents) { this.deductionCents = deductionCents; }
    public boolean isBoxRestored() { return boxRestored; }
    public void setBoxRestored(boolean boxRestored) { this.boxRestored = boxRestored; }
    public List<ReturnLine> getLines() { return lines; }
    public List<InspectionPhoto> getPhotos() { return photos; }
    public List<SupervisorNote> getSupervisorNotes() { return supervisorNotes; }
    public List<RefundRecord> getRefunds() { return refunds; }
}
