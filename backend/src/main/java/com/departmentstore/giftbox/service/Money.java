package com.departmentstore.giftbox.service;

/** 金额工具：内部一律用分，展示时格式化为元 */
public final class Money {

    private Money() {
    }

    public static String yuan(long cents) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + "¥" + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
