package com.giftbox.domain;

/** 库存形态：整盒 / 拆散散件 */
public enum StockKind {
    BOX("整盒"),
    LOOSE("散件");

    public final String label;

    StockKind(String label) {
        this.label = label;
    }
}
