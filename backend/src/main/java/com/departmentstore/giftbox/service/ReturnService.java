package com.departmentstore.giftbox.service;

import com.departmentstore.giftbox.model.DeductionCategory;
import com.departmentstore.giftbox.model.DeductionLine;
import com.departmentstore.giftbox.model.GiftBoxComponent;
import com.departmentstore.giftbox.model.InventoryState;
import com.departmentstore.giftbox.model.OrderItem;
import com.departmentstore.giftbox.model.OrderItemStatus;
import com.departmentstore.giftbox.model.PaymentRefundTxn;
import com.departmentstore.giftbox.model.Product;
import com.departmentstore.giftbox.model.ProductKind;
import com.departmentstore.giftbox.model.Refund;
import com.departmentstore.giftbox.model.ReturnLine;
import com.departmentstore.giftbox.model.ReturnRequest;
import com.departmentstore.giftbox.model.ReturnStatus;
import com.departmentstore.giftbox.model.SkuKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 退货主流程：建单 → 逐件核对 → 报价（扣减明细）→ 顾客确认 → 退款 + 库存复原。
 *
 * 幂等与并发：
 *  - 建单：idempotency_key 唯一；同一订单行只允许一笔退货（order_item_id 唯一）
 *  - 确认：状态机 QUOTED → REFUNDED 由乐观锁保护；退款单/支付台账/库存流水
 *    均有唯一约束兜底；重复确认（含重复回执）直接返回已有退款，不再退也不再入库
 */
@ApplicationScoped
public class ReturnService {

    @Inject
    PaymentGateway paymentGateway;

    @Inject
    InventoryService inventoryService;

    // ---------- 建单 ----------

    @Transactional
    public ReturnRequest createReturn(long orderItemId, String counterId, String idempotencyKey) {
        ReturnRequest byKey = ReturnRequest.findByIdempotencyKey(idempotencyKey);
        if (byKey != null) {
            return byKey; // 同一客户端重复提交：返回已建单据
        }
        OrderItem item = OrderItem.findById(orderItemId);
        if (item == null) {
            throw new NotFoundException("订单行不存在: " + orderItemId);
        }
        ReturnRequest byItem = ReturnRequest.findByOrderItem(item);
        if (byItem != null) {
            throw conflict("该订单行已存在退货单 #" + byItem.id + "，不能重复建单");
        }

        ReturnRequest rr = new ReturnRequest();
        rr.orderItem = item;
        rr.counterId = counterId;
        rr.idempotencyKey = idempotencyKey;
        rr.status = ReturnStatus.INSPECTING;
        rr.createdAt = LocalDateTime.now();
        rr.totalComboCents = item.comboPriceCents * item.qty;

        // 按组合价分摊到每个组件行（权重 = 零售标价 × 应有数量）
        List<GiftBoxComponent> components = GiftBoxComponent.list("giftBox", item.giftBox);
        long[] weights = new long[components.size()];
        for (int i = 0; i < components.size(); i++) {
            GiftBoxComponent c = components.get(i);
            weights[i] = c.product.listPriceCents * (long) c.qty * item.qty;
        }
        long[] allocated = MoneyAllocation.allocate(rr.totalComboCents, weights);

        for (int i = 0; i < components.size(); i++) {
            GiftBoxComponent c = components.get(i);
            ReturnLine line = new ReturnLine();
            line.returnRequest = rr;
            line.product = c.product;
            line.expectedQty = c.qty * item.qty;
            // 默认全部完好退回，柜台按实际情况修改
            line.receivedQty = line.expectedQty;
            line.intactQty = line.expectedQty;
            line.damagedQty = 0;
            line.allocatedTotalCents = allocated[i];
            line.unitAllocCents = line.expectedQty > 0 ? allocated[i] / line.expectedQty : 0;
            rr.lines.add(line);
        }
        rr.persist();
        return rr;
    }

    // ---------- 逐件核对 ----------

    public record LineInput(long lineId, int receivedQty, int intactQty, int damagedQty) {
    }

    @Transactional
    public ReturnRequest updateLines(long returnId, List<LineInput> inputs, boolean inspectionPassed) {
        ReturnRequest rr = requireReturn(returnId);
        if (rr.status == ReturnStatus.REFUNDED) {
            throw conflict("退货单已退款，不能再修改核对结果");
        }
        if (rr.status == ReturnStatus.QUOTED) {
            // 报价后退回修改：作废旧报价快照，需重新报价并由顾客再次确认
            rr.deductions.clear();
            rr.quotedAmountCents = 0;
            rr.status = ReturnStatus.INSPECTING;
        }
        Map<Long, ReturnLine> byId = new java.util.HashMap<>();
        for (ReturnLine l : rr.lines) {
            byId.put(l.id, l);
        }
        for (LineInput in : inputs) {
            ReturnLine line = byId.get(in.lineId());
            if (line == null) {
                throw new BadRequestException("核对行不属于该退货单: " + in.lineId());
            }
            if (in.receivedQty() < 0 || in.receivedQty() > line.expectedQty) {
                throw new BadRequestException("实收数量须在 0 与应有数量(" + line.expectedQty + ")之间: " + line.product.name);
            }
            if (in.intactQty() < 0 || in.damagedQty() < 0 || in.intactQty() + in.damagedQty() != in.receivedQty()) {
                throw new BadRequestException("完好+破损必须等于实收数量: " + line.product.name);
            }
            line.receivedQty = in.receivedQty();
            line.intactQty = in.intactQty();
            line.damagedQty = in.damagedQty();
        }
        rr.inspectionPassed = inspectionPassed;
        return rr;
    }

    // ---------- 报价 ----------

    @Transactional
    public ReturnRequest quote(long returnId) {
        ReturnRequest rr = requireReturn(returnId);
        if (rr.status == ReturnStatus.REFUNDED) {
            throw conflict("退货单已退款，不能重新报价");
        }
        // 重新生成扣减明细（快照保存，供顾客确认）
        rr.deductions.clear();
        long totalDeductions = 0;
        for (ReturnLine line : rr.lines) {
            int missing = line.missingQty();
            int damaged = line.damagedQty;
            if (missing == 0 && damaged == 0) {
                continue;
            }
            long damagedAmount = MoneyAllocation.deductionFor(
                    line.allocatedTotalCents, line.unitAllocCents, line.expectedQty, damaged);
            long missingAmount;
            if (missing + damaged == line.expectedQty && missing > 0 && damaged > 0) {
                // 整行无一件完好：扣减总额必须等于该行分摊总额，缺失部分吸收余数
                missingAmount = line.allocatedTotalCents - damagedAmount;
            } else {
                missingAmount = MoneyAllocation.deductionFor(
                        line.allocatedTotalCents, line.unitAllocCents, line.expectedQty, missing);
            }
            if (damaged > 0) {
                rr.deductions.add(buildDeduction(rr, line, damagedCategory(line.product.kind),
                        damaged, damagedAmount));
            }
            if (missing > 0) {
                rr.deductions.add(buildDeduction(rr, line, missingCategory(line.product.kind),
                        missing, missingAmount));
            }
            totalDeductions += damagedAmount + missingAmount;
        }
        rr.quotedAmountCents = rr.totalComboCents - totalDeductions;
        rr.status = ReturnStatus.QUOTED;
        return rr;
    }

    // ---------- 顾客确认 → 退款 + 库存复原 ----------

    @Transactional
    public Refund confirm(long returnId, String counterId) {
        ReturnRequest rr = requireReturn(returnId);
        if (rr.status == ReturnStatus.REFUNDED) {
            // 幂等重放：重复确认/重复回执直接返回已有退款，不会再次退款或入库
            return Refund.findByReturnRequest(rr);
        }
        if (rr.status != ReturnStatus.QUOTED) {
            throw conflict("退货单尚未生成报价，不能确认退款");
        }

        OrderItem item = rr.orderItem;

        // 1) 模拟支付网关退款（幂等键 = return-{id}，网关台账去重）
        PaymentRefundTxn pay = paymentGateway.refund(
                "return-" + rr.id, item.order.orderNo, rr.quotedAmountCents);

        // 2) 库存复原：只有完整且检查合格才恢复整盒；否则散件各按状态入库
        boolean complete = true;
        for (ReturnLine line : rr.lines) {
            if (line.receivedQty != line.expectedQty || line.damagedQty > 0) {
                complete = false;
                break;
            }
        }
        if (complete && rr.inspectionPassed) {
            inventoryService.adjust(SkuKind.BOX, item.giftBox.sku, InventoryState.SELLABLE,
                    item.qty, rr.id, "退货单#" + rr.id + " 整盒复原入库");
        } else {
            for (ReturnLine line : rr.lines) {
                if (line.intactQty > 0) {
                    inventoryService.adjust(SkuKind.COMPONENT, line.product.sku, InventoryState.SELLABLE,
                            line.intactQty, rr.id, "退货单#" + rr.id + " 散件完好入库");
                }
                if (line.damagedQty > 0) {
                    inventoryService.adjust(SkuKind.COMPONENT, line.product.sku, InventoryState.DAMAGED,
                            line.damagedQty, rr.id, "退货单#" + rr.id + " 破损件入库");
                }
            }
        }

        // 3) 退款单（return_request_id / order_item_id / receipt_no 唯一）
        Refund refund = new Refund();
        refund.returnRequest = rr;
        refund.orderItem = item;
        refund.amountCents = rr.quotedAmountCents;
        refund.paymentTxnId = pay.txnId;
        refund.counterId = counterId;
        refund.createdAt = LocalDateTime.now();
        refund.receiptNo = "PENDING";
        refund.persist();
        refund.receiptNo = "RCPT-" + String.format("%06d", refund.id);

        item.status = OrderItemStatus.RETURNED;
        rr.status = ReturnStatus.REFUNDED;
        rr.refundedAt = refund.createdAt;
        return refund;
    }

    // ---------- 查询 ----------

    @Transactional
    public ReturnRequest requireReturn(long returnId) {
        ReturnRequest rr = ReturnRequest.findById(returnId);
        if (rr == null) {
            throw new NotFoundException("退货单不存在: " + returnId);
        }
        return rr;
    }

    @Transactional
    public Refund findRefundByReturnId(long returnId) {
        ReturnRequest rr = ReturnRequest.findById(returnId);
        return rr == null ? null : Refund.findByReturnRequest(rr);
    }

    @Transactional
    public ReturnRequest findByIdempotencyKey(String key) {
        return ReturnRequest.findByIdempotencyKey(key);
    }

    // ---------- 内部 ----------

    private DeductionLine buildDeduction(ReturnRequest rr, ReturnLine line,
                                         DeductionCategory category, int qty, long amount) {
        DeductionLine d = new DeductionLine();
        d.returnRequest = rr;
        d.product = line.product;
        d.category = category;
        d.qty = qty;
        d.unitPriceCents = line.unitAllocCents;
        d.amountCents = amount;
        d.basis = basisText(category, line.product, qty, line.unitAllocCents, amount);
        return d;
    }

    private static DeductionCategory missingCategory(ProductKind kind) {
        return switch (kind) {
            case MAIN -> DeductionCategory.PRODUCT_MISSING;
            case ACCESSORY -> DeductionCategory.ACCESSORY_MISSING;
            case GIFT -> DeductionCategory.GIFT_MISSING;
        };
    }

    private static DeductionCategory damagedCategory(ProductKind kind) {
        return switch (kind) {
            case MAIN -> DeductionCategory.PRODUCT_DAMAGED;
            case ACCESSORY -> DeductionCategory.ACCESSORY_DAMAGED;
            case GIFT -> DeductionCategory.GIFT_DAMAGED;
        };
    }

    private static String basisText(DeductionCategory category, Product product,
                                    int qty, long unitAllocCents, long amountCents) {
        String unit = Money.yuan(unitAllocCents);
        String list = Money.yuan(product.listPriceCents);
        String total = Money.yuan(amountCents);
        return switch (category) {
            case PRODUCT_MISSING -> "商品未退回。依据：按售出组合价分摊 " + unit + "/件 × " + qty
                    + " 件（零售标价 " + list + "/件），共扣 " + total + "。";
            case PRODUCT_DAMAGED -> "商品破损，无法二次销售。依据：按售出组合价分摊 " + unit + "/件 全额扣减 × "
                    + qty + " 件（零售标价 " + list + "/件），共扣 " + total + "。";
            case ACCESSORY_MISSING -> "配件未退回。依据：按组合价分摊 " + unit + "/件 × " + qty
                    + " 件（零售标价 " + list + "/件），共扣 " + total + "。";
            case ACCESSORY_DAMAGED -> "配件破损。依据：按组合价分摊 " + unit + "/件 × " + qty
                    + " 件（零售标价 " + list + "/件），共扣 " + total + "。";
            case GIFT_MISSING -> "赠品未随礼盒退回。依据：赠品按组合价分摊价值 " + unit + "/件 × " + qty
                    + " 件（赠品零售标价 " + list + "/件），共扣 " + total + "。";
            case GIFT_DAMAGED -> "赠品破损。依据：赠品按组合价分摊价值 " + unit + "/件 × " + qty
                    + " 件（赠品零售标价 " + list + "/件），共扣 " + total + "。";
        };
    }

    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(message, 409);
    }
}
