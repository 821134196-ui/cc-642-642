package com.giftbox.pricing;

import com.giftbox.domain.ComponentType;
import com.giftbox.domain.CustomerOrder;
import com.giftbox.domain.LineStatus;
import com.giftbox.domain.ReturnLine;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 退款定价：按售出时的组合价格，把组合价分摊到每一个应有组件单位上。
 *
 * 分摊权重取组件单件参考报价；用“最大余数法”分配取整差额，
 * 因此所有组件单位分摊额之和恒等于售出组合价，refund + deduction 恒等于组合价。
 *
 * 每行内按单位精确归集（缺失→破损→完好的顺序切分单位分摊额），
 * 所以赠品缺失、商品破损各扣减项相加严格等于该行扣减，所有行相加严格等于总扣减。
 */
@ApplicationScoped
public class PricingService {

    /** 一个组件单位的分摊输入 */
    private record Unit(int lineIndex, int unitIndex, long weight) {
    }

    public Quote quote(CustomerOrder order, List<ReturnLine> lines) {
        long combo = order.getComboPriceCents();

        // 1) 展开每个“应有件”为一个分摊单位，权重 = 组件单件参考报价
        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            ReturnLine line = lines.get(i);
            long w = Math.max(0, line.getComponent().getUnitPriceCents());
            for (int u = 0; u < line.getExpectedQty(); u++) {
                units.add(new Unit(i, u, w));
            }
        }

        if (!units.isEmpty()) {
            long totalWeight = units.stream().mapToLong(Unit::weight).sum();
            if (totalWeight <= 0) {
                // 全部零价时按件均摊
                List<Unit> flat = new ArrayList<>();
                for (Unit u : units) {
                    flat.add(new Unit(u.lineIndex(), u.unitIndex(), 1L));
                }
                units = flat;
            }
        }
        long totalWeight = units.stream().mapToLong(Unit::weight).sum();

        // 2) 最大余数法求每个单位的分摊额，Σ == combo
        long[] perUnit = largestRemainder(combo, units, totalWeight);

        // 3) 归集到行（保留单位明细，供缺失/破损/完好精确切分）
        List<List<Long>> byLine = new ArrayList<>();
        long[] lineAllocated = new long[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            byLine.add(new ArrayList<>());
        }
        for (int k = 0; k < units.size(); k++) {
            Unit u = units.get(k);
            byLine.get(u.lineIndex()).add(perUnit[k]);
            lineAllocated[u.lineIndex()] += perUnit[k];
        }

        List<LineQuote> lineQuotes = new ArrayList<>();
        long refund = 0;
        long deduction = 0;
        for (int i = 0; i < lines.size(); i++) {
            ReturnLine line = lines.get(i);
            List<Long> unitShares = byLine.get(i);

            // 单位切分顺序：先缺失、再破损、余下完好。各项相加 == 行分摊额。
            int cursor = 0;
            long missingAmount = sumSlice(unitShares, cursor, line.getMissingQty());
            cursor += line.getMissingQty();
            long damagedAmount = sumSlice(unitShares, cursor, line.getDamagedQty());
            cursor += line.getDamagedQty();
            long intactAmount = sumSlice(unitShares, cursor, line.getIntactQty());

            long lineDeduction = missingAmount + damagedAmount;
            long lineRefund = intactAmount;

            long unitAllocDisplay = line.getExpectedQty() == 0
                    ? 0
                    : Math.floorDiv(lineAllocated[i], line.getExpectedQty());

            ComponentType type = line.getComponent().getType();
            List<ChargeItem> charges = new ArrayList<>();
            if (line.getMissingQty() > 0) {
                charges.add(new ChargeItem(missingTitle(type), LineStatus.MISSING,
                        line.getMissingQty(), missingAmount,
                        line.getComponent().getUnitPriceCents(), missingBasis(type, line)));
            }
            if (line.getDamagedQty() > 0) {
                charges.add(new ChargeItem(damagedTitle(type), LineStatus.DAMAGED,
                        line.getDamagedQty(), damagedAmount,
                        line.getComponent().getUnitPriceCents(), damagedBasis(type, line)));
            }

            refund += lineRefund;
            deduction += lineDeduction;
            lineQuotes.add(new LineQuote(line, lineAllocated[i], unitAllocDisplay,
                    lineRefund, lineDeduction, charges));
        }

        return new Quote(combo, refund, deduction, lineQuotes);
    }

    private long sumSlice(List<Long> shares, int from, int qty) {
        long s = 0;
        for (int k = 0; k < qty && from + k < shares.size(); k++) {
            s += shares.get(from + k);
        }
        return s;
    }

    /**
     * 最大余数法：每单位先取 floor(combo*weight/totalWeight)，
     * 余下的分（R = combo - Σfloor）按余数从大到小各补 1 分，并列时按行/件顺序确定。
     */
    private long[] largestRemainder(long combo, List<Unit> units, long totalWeight) {
        long[] result = new long[units.size()];
        if (units.isEmpty()) {
            return result;
        }
        record Rem(int idx, long rem) {
        }
        List<Rem> rems = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < units.size(); i++) {
            long raw = combo * units.get(i).weight();
            long base = raw / totalWeight;
            result[i] = base;
            allocated += base;
            rems.add(new Rem(i, raw % totalWeight));
        }
        long left = combo - allocated;
        rems.sort(Comparator.comparingLong(Rem::rem).reversed()
                .thenComparingInt(r -> {
                    Unit u = units.get(r.idx());
                    return u.lineIndex() * 100000 + u.unitIndex();
                }));
        for (int k = 0; k < left && k < rems.size(); k++) {
            result[rems.get(k).idx()] += 1;
        }
        return result;
    }

    private String missingTitle(ComponentType type) {
        return switch (type) {
            case GIFT -> "赠品缺失";
            case ACCESSORY -> "配件缺失";
            case PRODUCT -> "商品缺失";
        };
    }

    private String damagedTitle(ComponentType type) {
        return switch (type) {
            case GIFT -> "赠品破损";
            case ACCESSORY -> "配件破损";
            case PRODUCT -> "商品破损";
        };
    }

    private String missingBasis(ComponentType type, ReturnLine line) {
        String quote = com.giftbox.support.Money.yuan(line.getComponent().getUnitPriceCents());
        return switch (type) {
            case GIFT -> "赠品未随盒退回，按售出组合价中该赠品的分摊额扣减；"
                    + "赠品单件参考报价 ¥" + quote + "，扣减以组合价分摊为准。";
            case ACCESSORY -> "配件缺失，无法按完整礼盒二次销售，按组合价分摊额扣减；"
                    + "配件单件参考报价 ¥" + quote + "。";
            case PRODUCT -> "商品本体缺失，按售出组合价分摊额扣减；"
                    + "商品单件参考报价 ¥" + quote + "。";
        };
    }

    private String damagedBasis(ComponentType type, ReturnLine line) {
        String quote = com.giftbox.support.Money.yuan(line.getComponent().getUnitPriceCents());
        return switch (type) {
            case GIFT -> "赠品破损不能再作赠品发出，按组合价分摊额扣减；"
                    + "赠品单件参考报价 ¥" + quote + "。";
            case ACCESSORY -> "配件破损无法配齐整盒，按组合价分摊额扣减；"
                    + "配件单件参考报价 ¥" + quote + "。";
            case PRODUCT -> "商品破损不能重新入库销售，按售出组合价分摊额扣减；"
                    + "商品单件参考报价 ¥" + quote + "。";
        };
    }

    // ---- 报价结果 DTO ----

    public record Quote(long comboPriceCents, long refundCents, long deductionCents,
                        List<LineQuote> lines) {
    }

    public record LineQuote(ReturnLine line, long allocatedCents, long unitAllocatedCents,
                            long refundCents, long deductionCents, List<ChargeItem> charges) {
    }

    public record ChargeItem(String title, LineStatus status, int quantity, long amountCents,
                             long unitQuoteCents, String basis) {
    }
}
