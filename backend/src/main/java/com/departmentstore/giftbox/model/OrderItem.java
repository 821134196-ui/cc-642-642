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
import jakarta.persistence.Version;

/** 订单行：一次礼盒购买。comboPriceCents 为售出时的组合价快照，退款分摊以它为准 */
@Entity
public class OrderItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public CustomerOrder order;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "gift_box_id")
    public GiftBox giftBox;

    @Column(nullable = false)
    public int qty;

    /** 售出时组合价快照（分） */
    @Column(nullable = false)
    public long comboPriceCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrderItemStatus status = OrderItemStatus.NORMAL;

    @Version
    public long version;
}
