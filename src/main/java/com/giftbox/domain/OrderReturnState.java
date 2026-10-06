package com.giftbox.domain;

/** 原订单退货状态：未退货 / 已退款（并发仲裁依据） */
public enum OrderReturnState {
    OPEN,
    REFUNDED
}
