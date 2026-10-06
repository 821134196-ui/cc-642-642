package com.departmentstore.giftbox.service;

import com.departmentstore.giftbox.model.PaymentRefundTxn;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 模拟支付网关。按幂等键去重：同一键的退款请求无论来多少次，
 * 只执行一次并返回同一笔交易（台账落库，唯一约束兜底）。
 */
@ApplicationScoped
public class PaymentGateway {

    @Transactional
    public PaymentRefundTxn refund(String idempotencyKey, String orderNo, long amountCents) {
        PaymentRefundTxn existing = PaymentRefundTxn.findByKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        PaymentRefundTxn txn = new PaymentRefundTxn();
        txn.idempotencyKey = idempotencyKey;
        txn.orderNo = orderNo;
        txn.amountCents = amountCents;
        txn.txnId = "MOCKPAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        txn.createdAt = LocalDateTime.now();
        txn.persist();
        return txn;
    }
}
