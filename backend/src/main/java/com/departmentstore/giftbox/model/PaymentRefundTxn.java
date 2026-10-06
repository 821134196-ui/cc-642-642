package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 模拟支付网关的退款台账。idempotency_key 唯一：
 * 网关层面同样保证同一笔退款请求不会执行两次。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_pay_idem", columnNames = "idempotency_key"))
public class PaymentRefundTxn extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "idempotency_key", nullable = false)
    public String idempotencyKey;

    @Column(nullable = false)
    public String orderNo;

    @Column(nullable = false)
    public long amountCents;

    @Column(nullable = false)
    public String txnId;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public static PaymentRefundTxn findByKey(String key) {
        return find("idempotencyKey", key).firstResult();
    }
}
