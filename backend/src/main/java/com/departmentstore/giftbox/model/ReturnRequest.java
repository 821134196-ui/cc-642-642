package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 退货单。每个订单行只允许一笔退货（order_item_id 唯一），
 * idempotency_key 唯一保证柜台重复提交/并发建单只产生一单。
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uq_return_order_item", columnNames = "order_item_id"),
        @UniqueConstraint(name = "uq_return_idem_key", columnNames = "idempotency_key")
})
public class ReturnRequest extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    public OrderItem orderItem;

    /** 客户端生成的幂等键 */
    @Column(name = "idempotency_key", nullable = false)
    public String idempotencyKey;

    /** 办理柜台 */
    @Column(nullable = false)
    public String counterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ReturnStatus status = ReturnStatus.INSPECTING;

    /** 检查是否合格（整盒复原的必要条件之一） */
    @Column(nullable = false)
    public boolean inspectionPassed = false;

    /** 售出组合价总额快照 = comboPriceCents * qty（分） */
    @Column(nullable = false)
    public long totalComboCents;

    /** 报价时算出的应退金额（分） */
    public long quotedAmountCents;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public LocalDateTime refundedAt;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    public List<ReturnLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    public List<DeductionLine> deductions = new ArrayList<>();

    @Version
    public long version;

    public static ReturnRequest findByIdempotencyKey(String key) {
        return find("idempotencyKey", key).firstResult();
    }

    public static ReturnRequest findByOrderItem(OrderItem item) {
        return find("orderItem", item).firstResult();
    }
}
