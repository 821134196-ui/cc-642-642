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

import java.time.LocalDateTime;

/**
 * 入库批次：退货完成时一次性写入。
 * 整盒复原（所有组件完好）才写一条 BOX；否则只按完好组件写 LOOSE 批次，
 * 破损/缺失件绝不入库，因此库存中不可能出现未真正复原的整盒。
 */
@Entity
public class StockEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private StockKind kind;

    /** kind=BOX 时有值 */
    @ManyToOne(fetch = FetchType.EAGER)
    private GiftBox giftBox;

    /** kind=LOOSE 时有值 */
    @ManyToOne(fetch = FetchType.EAGER)
    private GiftBoxComponent component;

    private int quantity;

    private String sourceOrderCode;
    private Long sourceReturnId;
    private String note;
    private LocalDateTime createdAt;

    public StockEntry() {
    }

    public static StockEntry box(GiftBox box, int qty, String orderCode, Long returnId, LocalDateTime at) {
        StockEntry e = new StockEntry();
        e.kind = StockKind.BOX;
        e.giftBox = box;
        e.component = null;
        e.quantity = qty;
        e.sourceOrderCode = orderCode;
        e.sourceReturnId = returnId;
        e.note = "退回礼盒检验全部完好，整盒复原入库";
        e.createdAt = at;
        return e;
    }

    public static StockEntry loose(GiftBoxComponent component, int qty, String orderCode,
                                   Long returnId, LocalDateTime at) {
        StockEntry e = new StockEntry();
        e.kind = StockKind.LOOSE;
        e.giftBox = null;
        e.component = component;
        e.quantity = qty;
        e.sourceOrderCode = orderCode;
        e.sourceReturnId = returnId;
        e.note = "退回礼盒拆散，完好散件入库";
        e.createdAt = at;
        return e;
    }

    public Long getId() { return id; }
    public StockKind getKind() { return kind; }
    public GiftBox getGiftBox() { return giftBox; }
    public GiftBoxComponent getComponent() { return component; }
    public int getQuantity() { return quantity; }
    public String getSourceOrderCode() { return sourceOrderCode; }
    public Long getSourceReturnId() { return sourceReturnId; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
