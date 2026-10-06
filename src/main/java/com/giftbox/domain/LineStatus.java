package com.giftbox.domain;

/** 逐件检查结果：完好 / 破损 / 缺失 */
public enum LineStatus {
    INTACT("完好"),
    DAMAGED("破损"),
    MISSING("缺失");

    public final String label;

    LineStatus(String label) {
        this.label = label;
    }
}
