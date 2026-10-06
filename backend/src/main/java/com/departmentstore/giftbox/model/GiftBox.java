package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

/** 礼盒规格：组合售价 + 组件清单 */
@Entity
public class GiftBox extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String sku;

    @Column(nullable = false)
    public String name;

    /** 组合售价（分） */
    @Column(nullable = false)
    public long comboPriceCents;

    @OneToMany(mappedBy = "giftBox", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    public List<GiftBoxComponent> components = new ArrayList<>();

    public static GiftBox findBySku(String sku) {
        return find("sku", sku).firstResult();
    }
}
