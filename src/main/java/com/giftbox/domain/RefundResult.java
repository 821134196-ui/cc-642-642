package com.giftbox.domain;

/** 退款处理结果：成功，或因订单已退而被幂等拒绝。 */
public enum RefundResult {
    SUCCESS,
    DUPLICATE_REJECTED
}
