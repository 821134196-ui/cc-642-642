package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 库存流水。同一退货单对同一 (sku, 形态, 状态) 只允许一条流水（唯一约束），
 * 保证一次退货的库存调整最多生效一次。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uq_invtxn_return_sku",
        columnNames = {"return_request_id", "sku", "sku_kind", "state"}))
public class InventoryTxn extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "return_request_id")
    public Long returnRequestId;

    @Column(nullable = false)
    public String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "sku_kind", nullable = false)
    public SkuKind skuKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public InventoryState state;

    @Column(nullable = false)
    public long delta;

    @Column(length = 300)
    public String reason;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public static boolean existsForReturn(Long returnRequestId, String sku, SkuKind kind, InventoryState state) {
        return count("returnRequestId = ?1 and sku = ?2 and skuKind = ?3 and state = ?4",
                returnRequestId, sku, kind, state) > 0;
    }
}
