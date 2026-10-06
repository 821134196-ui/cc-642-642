package com.giftbox.domain;

/** 退货单状态：登记中 / 顾客已确认明细 / 已完成退款入库 / 并发落败被拒（未退款未入库） */
public enum ReturnStatus {
    DRAFT,
    CONFIRMED,
    COMPLETED,
    REJECTED
}
