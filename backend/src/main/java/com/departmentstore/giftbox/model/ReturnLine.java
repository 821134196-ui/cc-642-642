package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 组件核对行：应有 vs 实收，逐件登记完好 / 破损，缺失 = 应有 - 实收。
 * allocatedTotalCents 为该组件按组合价分摊到的总金额（分）。
 */
@Entity
public class ReturnLine extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    public ReturnRequest returnRequest;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    public Product product;

    /** 应有数量 */
    @Column(nullable = false)
    public int expectedQty;

    /** 实收数量 */
    @Column(nullable = false)
    public int receivedQty;

    /** 完好数量 */
    @Column(nullable = false)
    public int intactQty;

    /** 破损数量 */
    @Column(nullable = false)
    public int damagedQty;

    /** 该组件分摊到的组合价总额（分） */
    @Column(nullable = false)
    public long allocatedTotalCents;

    /** 每件分摊价 = floor(allocatedTotal / expectedQty)，最后一件吸收余数 */
    @Column(nullable = false)
    public long unitAllocCents;

    public int missingQty() {
        return expectedQty - receivedQty;
    }
}
