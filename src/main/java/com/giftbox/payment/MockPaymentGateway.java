package com.giftbox.payment;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模拟支付渠道：只做记账式退款，不接入真实支付。
 * 真实系统中这一步应幂等（携带商户幂等键）；这里同样支持幂等键去重。
 */
@ApplicationScoped
public class MockPaymentGateway {

    private static final Logger LOG = Logger.getLogger(MockPaymentGateway.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AtomicLong seq = new AtomicLong(1);

    public RefundResponse refund(String orderCode, long amountCents, String idempotencyKey) {
        if (amountCents < 0) {
            throw new IllegalArgumentException("退款金额不能为负");
        }
        String receipt = "RF-" + LocalDateTime.now().format(FMT) + "-" + seq.getAndIncrement();
        LOG.infof("[模拟支付] 订单 %s 退款 %d 分 (幂等键 %s) -> 回执 %s",
                orderCode, amountCents, idempotencyKey, receipt);
        return new RefundResponse(receipt, amountCents, idempotencyKey, LocalDateTime.now());
    }

    public record RefundResponse(String receiptNo, long amountCents, String idempotencyKey,
                                 LocalDateTime at) {
    }
}
