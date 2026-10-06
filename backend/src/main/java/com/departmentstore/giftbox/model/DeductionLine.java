package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** 扣减明细：类别 + 数量 + 单价 + 金额 + 依据说明 */
@Entity
public class DeductionLine extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    public ReturnRequest returnRequest;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    public Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public DeductionCategory category;

    @Column(nullable = false)
    public int qty;

    /** 扣减单价（分）= 组合价分摊单价 */
    @Column(nullable = false)
    public long unitPriceCents;

    /** 扣减金额（分） */
    @Column(nullable = false)
    public long amountCents;

    /** 扣减依据（展示给顾客） */
    @Column(length = 500)
    public String basis;
}
