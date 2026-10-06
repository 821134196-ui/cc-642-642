package com.giftbox.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * 退货逐件检查行，对应礼盒的一个应有组件。
 * 数量恒等式：intactQty + damagedQty + missingQty = expectedQty。
 * 完好件可重新入库（整盒复原或散件），破损件不可入库，缺失件无实物。
 */
@Entity
public class ReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReturnCase returnCase;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private GiftBoxComponent component;

    private int expectedQty;
    private int intactQty;
    private int damagedQty;
    private int missingQty;

    /** 柜台对该行的备注（如破损位置、缺失原因） */
    private String note;

    private int sortOrder;

    public ReturnLine() {
    }

    public ReturnLine(ReturnCase returnCase, GiftBoxComponent component, int sortOrder) {
        this.returnCase = returnCase;
        this.component = component;
        this.expectedQty = component.getQuantity();
        // 默认全部登记为完好，柜台逐件改判
        this.intactQty = component.getQuantity();
        this.damagedQty = 0;
        this.missingQty = 0;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public ReturnCase getReturnCase() { return returnCase; }
    public GiftBoxComponent getComponent() { return component; }
    public int getExpectedQty() { return expectedQty; }
    public int getIntactQty() { return intactQty; }
    public void setIntactQty(int intactQty) { this.intactQty = intactQty; }
    public int getDamagedQty() { return damagedQty; }
    public void setDamagedQty(int damagedQty) { this.damagedQty = damagedQty; }
    public int getMissingQty() { return missingQty; }
    public void setMissingQty(int missingQty) { this.missingQty = missingQty; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /** 不能重新入库、需要从退款扣减的件数（破损 + 缺失） */
    public int nonReturnableQty() {
        return damagedQty + missingQty;
    }
}
