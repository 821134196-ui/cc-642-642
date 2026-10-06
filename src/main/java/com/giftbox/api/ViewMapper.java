package com.giftbox.api;

import com.giftbox.domain.ComponentType;
import com.giftbox.domain.CustomerOrder;
import com.giftbox.domain.GiftBoxComponent;
import com.giftbox.domain.InspectionPhoto;
import com.giftbox.domain.RefundRecord;
import com.giftbox.domain.ReturnCase;
import com.giftbox.domain.ReturnLine;
import com.giftbox.domain.StockEntry;
import com.giftbox.domain.SupervisorNote;
import com.giftbox.pricing.PricingService;
import com.giftbox.support.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 实体 -> 前端 JSON 视图；金额统一转“元”字符串，分摊/扣减在此实时计算。 */
@ApplicationScoped
public class ViewMapper {

    @Inject
    PricingService pricing;

    public Map<String, Object> order(CustomerOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("code", o.getCode());
        m.put("customerName", o.getCustomerName());
        m.put("counter", o.getCounter());
        m.put("boxId", o.getGiftBox().getId());
        m.put("boxCode", o.getGiftBox().getCode());
        m.put("boxName", o.getGiftBox().getName());
        m.put("comboPriceYuan", Money.yuan(o.getComboPriceCents()));
        m.put("soldAt", o.getSoldAt() == null ? null : o.getSoldAt().toString());
        m.put("returnState", o.getReturnState().name());
        m.put("components", o.getGiftBox().getComponents().stream().map(this::component).toList());
        return m;
    }

    public Map<String, Object> component(GiftBoxComponent c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("type", c.getType().name());
        m.put("typeLabel", c.getType().label);
        m.put("name", c.getName());
        m.put("unitQuoteYuan", Money.yuan(c.getUnitPriceCents()));
        m.put("quantity", c.getQuantity());
        return m;
    }

    /** 退货单视图：alwaysQuote=true 时对登记中的单子也实时算一份报价用于展示。 */
    public Map<String, Object> returnCase(ReturnCase rc, boolean withQuote) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rc.getId());
        m.put("orderId", rc.getOrder().getId());
        m.put("orderCode", rc.getOrder().getCode());
        m.put("counter", rc.getCounter());
        m.put("status", rc.getStatus().name());
        m.put("createdAt", str(rc.getCreatedAt()));
        m.put("customerConfirmedAt", str(rc.getCustomerConfirmedAt()));
        m.put("completedAt", str(rc.getCompletedAt()));
        m.put("refundYuan", rc.getRefundCents() == null ? null : Money.yuan(rc.getRefundCents()));
        m.put("deductionYuan", rc.getDeductionCents() == null ? null : Money.yuan(rc.getDeductionCents()));
        m.put("boxRestored", rc.isBoxRestored());
        m.put("photos", rc.getPhotos().stream().map(p -> photo(rc.getId(), p)).toList());
        m.put("supervisorNotes", rc.getSupervisorNotes().stream().map(this::note).toList());
        m.put("refunds", rc.getRefunds().stream().map(this::refund).toList());

        PricingService.Quote q = withQuote ? pricing.quote(rc.getOrder(), rc.getLines()) : null;
        if (q != null) {
            m.put("quote", quote(q));
        }
        if (q != null) {
            Map<Long, PricingService.LineQuote> byLine = new HashMap<>();
            for (var lq : q.lines()) {
                byLine.put(lq.line().getId(), lq);
            }
            m.put("lines", rc.getLines().stream()
                    .map(l -> line(l, byLine.get(l.getId()))).toList());
        } else {
            m.put("lines", rc.getLines().stream().map(l -> line(l, null)).toList());
        }
        return m;
    }

    private Map<String, Object> line(ReturnLine l, PricingService.LineQuote lq) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("component", component(l.getComponent()));
        m.put("expectedQty", l.getExpectedQty());
        m.put("intactQty", l.getIntactQty());
        m.put("damagedQty", l.getDamagedQty());
        m.put("missingQty", l.getMissingQty());
        m.put("note", l.getNote());
        if (lq != null) {
            m.put("allocatedYuan", Money.yuan(lq.allocatedCents()));
            m.put("unitAllocatedYuan", Money.yuan(lq.unitAllocatedCents()));
            m.put("refundYuan", Money.yuan(lq.refundCents()));
            m.put("deductionYuan", Money.yuan(lq.deductionCents()));
            m.put("charges", lq.charges().stream().map(c -> {
                Map<String, Object> cm = new HashMap<>();
                cm.put("title", c.title());
                cm.put("status", c.status().name());
                cm.put("quantity", c.quantity());
                cm.put("amountYuan", Money.yuan(c.amountCents()));
                cm.put("unitQuoteYuan", Money.yuan(c.unitQuoteCents()));
                cm.put("basis", c.basis());
                return cm;
            }).toList());
        }
        return m;
    }

    public Map<String, Object> quote(PricingService.Quote q) {
        Map<String, Object> m = new HashMap<>();
        m.put("comboPriceYuan", Money.yuan(q.comboPriceCents()));
        m.put("refundYuan", Money.yuan(q.refundCents()));
        m.put("deductionYuan", Money.yuan(q.deductionCents()));
        m.put("balanced", q.refundCents() + q.deductionCents() == q.comboPriceCents());
        return m;
    }

    private Map<String, Object> photo(Long returnId, InspectionPhoto p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("fileName", p.getFileName());
        m.put("originalName", p.getOriginalName());
        m.put("contentType", p.getContentType());
        m.put("sizeBytes", p.getSizeBytes());
        m.put("uploadedBy", p.getUploadedBy());
        m.put("uploadedAt", str(p.getUploadedAt()));
        m.put("url", "/api/returns/" + returnId + "/photos/" + p.getId() + "/raw");
        return m;
    }

    private Map<String, Object> note(SupervisorNote n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("supervisor", n.getSupervisor());
        m.put("approved", n.isApproved());
        m.put("opinion", n.getOpinion());
        m.put("createdAt", str(n.getCreatedAt()));
        return m;
    }

    public Map<String, Object> refund(RefundRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("orderCode", r.getOrderCode());
        m.put("amountYuan", r.getAmountCents() == null ? null : Money.yuan(r.getAmountCents()));
        m.put("result", r.getResult().name());
        m.put("receiptNo", r.getReceiptNo());
        m.put("paymentRef", r.getPaymentRef());
        m.put("detail", r.getDetail());
        m.put("createdAt", str(r.getCreatedAt()));
        return m;
    }

    public Map<String, Object> stock(StockEntry e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("kind", e.getKind().name());
        m.put("kindLabel", e.getKind().label);
        if (e.getGiftBox() != null) {
            m.put("boxCode", e.getGiftBox().getCode());
            m.put("name", e.getGiftBox().getName());
        }
        if (e.getComponent() != null) {
            m.put("componentId", e.getComponent().getId());
            m.put("name", e.getComponent().getName());
            m.put("type", e.getComponent().getType().name());
            m.put("typeLabel", e.getComponent().getType().label);
        }
        m.put("quantity", e.getQuantity());
        m.put("sourceOrderCode", e.getSourceOrderCode());
        m.put("sourceReturnId", e.getSourceReturnId());
        m.put("note", e.getNote());
        m.put("createdAt", str(e.getCreatedAt()));
        return m;
    }

    private String str(LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}
