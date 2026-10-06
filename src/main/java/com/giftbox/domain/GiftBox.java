package com.giftbox.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

/** 礼盒模板：商品 + 配件 + 赠品的组合，售出时按组合价格成交。 */
@Entity
public class GiftBox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;

    /** 售出时的组合价格（分） */
    private long comboPriceCents;

    @OneToMany(mappedBy = "giftBox", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC, id ASC")
    private List<GiftBoxComponent> components = new ArrayList<>();

    public GiftBox() {
    }

    public GiftBox(String code, String name, long comboPriceCents) {
        this.code = code;
        this.name = name;
        this.comboPriceCents = comboPriceCents;
    }

    public GiftBoxComponent addComponent(ComponentType type, String name, long unitPriceCents,
                                         int quantity, int sortOrder) {
        GiftBoxComponent c = new GiftBoxComponent(this, type, name, unitPriceCents, quantity, sortOrder);
        components.add(c);
        return c;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getComboPriceCents() { return comboPriceCents; }
    public void setComboPriceCents(long comboPriceCents) { this.comboPriceCents = comboPriceCents; }
    public List<GiftBoxComponent> getComponents() { return components; }
}
