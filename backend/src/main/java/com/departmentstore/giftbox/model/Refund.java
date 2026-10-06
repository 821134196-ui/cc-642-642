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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 退款单。return_request_id / order_item_id / receipt_no 均唯一：
 * 同一退货单、同一订单行只可能有一笔有效退款，重复回执无法再次退款。
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uq_refund_return", columnNames = "return_request_id"),
        @UniqueConstraint(name = "uq_refund_order_item", columnNames = "order_item_id"),
        @UniqueConstraint(name = "uq_refund_receipt", columnNames = "receipt_no")
})
public class Refund extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    public ReturnRequest returnRequest;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    public OrderItem orderItem;

    @Column(name = "receipt_no", nullable = false)
    public String receiptNo;

    @Column(nullable = false)
    public long amountCents;

    @Column(nullable = false)
    public String paymentTxnId;

    @Column(nullable = false)
    public String counterId;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public static Refund findByReturnRequest(ReturnRequest rr) {
        return find("returnRequest", rr).firstResult();
    }
}
