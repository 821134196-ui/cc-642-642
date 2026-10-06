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
 * 退款记录（支付回执）。SUCCESS 每订单至多一条；
 * 拿已退订单/旧回执再次请求只产生 DUPLICATE_REJECTED 审计记录，不发生第二次退款。
 */
@Entity
public class RefundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReturnCase returnCase;

    private Long orderId;
    private String orderCode;
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    private RefundResult result;

    /** 成功退款的支付回执号（模拟支付渠道返回） */
    private String receiptNo;
    private String paymentRef;
    private String detail;
    private LocalDateTime createdAt;

    public RefundRecord() {
    }

    public RefundRecord(ReturnCase returnCase, Long orderId, String orderCode, Long amountCents,
                        RefundResult result, String receiptNo, String paymentRef,
                        String detail, LocalDateTime createdAt) {
        this.returnCase = returnCase;
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.amountCents = amountCents;
        this.result = result;
        this.receiptNo = receiptNo;
        this.paymentRef = paymentRef;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public ReturnCase getReturnCase() { return returnCase; }
    public Long getOrderId() { return orderId; }
    public String getOrderCode() { return orderCode; }
    public Long getAmountCents() { return amountCents; }
    public RefundResult getResult() { return result; }
    public String getReceiptNo() { return receiptNo; }
    public String getPaymentRef() { return paymentRef; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
