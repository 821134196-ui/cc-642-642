package com.giftbox.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** 礼盒中的应有组件（商品 / 配件 / 赠品），unitPriceCents 为单件参考报价。 */
@Entity
public class GiftBoxComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private GiftBox giftBox;

    @Enumerated(EnumType.STRING)
    private ComponentType type;

    private String name;

    /** 单件参考报价（分）：赠品缺失/商品破损时展示的“报价”，同时是组合价分摊权重 */
    private long unitPriceCents;

    /** 该组件在整盒中的应有数量 */
    private int quantity;

    private int sortOrder;

    public GiftBoxComponent() {
    }

    public GiftBoxComponent(GiftBox giftBox, ComponentType type, String name,
                            long unitPriceCents, int quantity, int sortOrder) {
        this.giftBox = giftBox;
        this.type = type;
        this.name = name;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public GiftBox getGiftBox() { return giftBox; }
    public ComponentType getType() { return type; }
    public void setType(ComponentType type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(long v) { this.unitPriceCents = v; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
