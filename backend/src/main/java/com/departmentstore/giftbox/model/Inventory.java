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

/** 库存：按 (SKU, 形态[整盒/散件], 状态[可售/破损]) 记账 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_inventory_key", columnNames = {"sku", "sku_kind", "state"}))
public class Inventory extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "sku_kind", nullable = false)
    public SkuKind skuKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public InventoryState state;

    @Column(nullable = false)
    public long qty;

    public static Inventory findRow(String sku, SkuKind kind, InventoryState state) {
        return find("sku = ?1 and skuKind = ?2 and state = ?3", sku, kind, state).firstResult();
    }
}
