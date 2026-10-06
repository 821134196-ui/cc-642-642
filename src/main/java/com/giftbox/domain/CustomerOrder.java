package com.giftbox.domain;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 原订单：售出时锁定组合价格快照；returnState 是“是否已退款”的并发仲裁位。 */
@Entity
@jakarta.persistence.Table(name = "sale_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String customerName;
    private String counter;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private GiftBox giftBox;

    /** 售出时组合价格快照（分），退款分摊始终以此为准 */
    private long comboPriceCents;

    private LocalDateTime soldAt;

    @Enumerated(EnumType.STRING)
    private OrderReturnState returnState = OrderReturnState.OPEN;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnCase> returnCases = new ArrayList<>();

    public CustomerOrder() {
    }

    public CustomerOrder(String code, String customerName, String counter,
                         GiftBox giftBox, long comboPriceCents, LocalDateTime soldAt) {
        this.code = code;
        this.customerName = customerName;
        this.counter = counter;
        this.giftBox = giftBox;
        this.comboPriceCents = comboPriceCents;
        this.soldAt = soldAt;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getCounter() { return counter; }
    public void setCounter(String counter) { this.counter = counter; }
    public GiftBox getGiftBox() { return giftBox; }
    public void setGiftBox(GiftBox giftBox) { this.giftBox = giftBox; }
    public long getComboPriceCents() { return comboPriceCents; }
    public void setComboPriceCents(long v) { this.comboPriceCents = v; }
    public LocalDateTime getSoldAt() { return soldAt; }
    public void setSoldAt(LocalDateTime soldAt) { this.soldAt = soldAt; }
    public OrderReturnState getReturnState() { return returnState; }
    public void setReturnState(OrderReturnState returnState) { this.returnState = returnState; }
    public List<ReturnCase> getReturnCases() { return returnCases; }
}
