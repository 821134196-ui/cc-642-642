package com.departmentstore.giftbox.service;

import java.util.Arrays;

/**
 * 金额分摊：把组合价按各组件（零售标价 × 数量）为权重分摊，
 * 使用最大余数法保证分摊结果之和严格等于组合价（分）。
 */
public final class MoneyAllocation {

    private MoneyAllocation() {
    }

    /**
     * @param totalCents 待分摊总额（分）
     * @param weights    每行的权重（必须 >= 0，且总和 > 0）
     * @return 每行分摊到的金额，sum(result) == totalCents
     */
    public static long[] allocate(long totalCents, long[] weights) {
        if (totalCents < 0) {
            throw new IllegalArgumentException("totalCents must be >= 0");
        }
        long totalWeight = 0;
        for (long w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("weight must be >= 0");
            }
            totalWeight += w;
        }
        if (weights.length == 0 || totalWeight <= 0) {
            throw new IllegalArgumentException("weights must not be empty and total weight must be > 0");
        }

        long[] floors = new long[weights.length];
        long[] remainders = new long[weights.length]; // 分子余数，分母统一为 totalWeight
        long allocated = 0;
        for (int i = 0; i < weights.length; i++) {
            long numerator = totalCents * weights[i];
            floors[i] = numerator / totalWeight;
            remainders[i] = numerator % totalWeight;
            allocated += floors[i];
        }

        long leftover = totalCents - allocated; // 0 <= leftover < weights.length
        // 按余数从大到小补 1 分；余数相同按行号顺序，保证确定性
        Integer[] order = new Integer[weights.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int cmp = Long.compare(remainders[b], remainders[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        for (int k = 0; k < leftover; k++) {
            floors[order[k]] += 1;
        }
        return floors;
    }

    /**
     * 某行扣减 k 件时的金额：全部扣减时等于该行分摊总额（吸收余数），
     * 部分扣减时按分摊单价 × 数量。
     */
    public static long deductionFor(long allocatedTotalCents, long unitAllocCents, int expectedQty, int deductQty) {
        if (deductQty < 0 || deductQty > expectedQty) {
            throw new IllegalArgumentException("deductQty out of range");
        }
        if (deductQty == expectedQty) {
            return allocatedTotalCents;
        }
        return unitAllocCents * deductQty;
    }
}
