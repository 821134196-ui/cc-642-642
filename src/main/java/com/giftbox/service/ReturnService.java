package com.giftbox.service;

import com.giftbox.domain.CustomerOrder;
import com.giftbox.domain.InspectionPhoto;
import com.giftbox.domain.OrderReturnState;
import com.giftbox.domain.RefundRecord;
import com.giftbox.domain.RefundResult;
import com.giftbox.domain.ReturnCase;
import com.giftbox.domain.ReturnLine;
import com.giftbox.domain.ReturnStatus;
import com.giftbox.domain.StockEntry;
import com.giftbox.domain.SupervisorNote;
import com.giftbox.payment.MockPaymentGateway;
import com.giftbox.pricing.PricingService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退货办理主服务。
 *
 * 完成办理（complete）在单个事务内完成四件事：
 *   1) 条件更新认领订单（OPEN→REFUNDED），并发只有一单认领成功；
 *   2) 按当前登记明细重新计算退款/扣减（权威结果），校验 refund+deduction==组合价；
 *   3) 调用模拟支付退款，落唯一一条 SUCCESS 回执；
 *   4) 按复原规则写一次库存（全部完好→整盒；否则仅完好散件）。
 * 任一步失败整体回滚。重复 complete 或旧回执重放都不会二次退款/二次入库。
 */
@ApplicationScoped
public class ReturnService {

    @Inject
    EntityManager em;

    @Inject
    PricingService pricing;

    @Inject
    MockPaymentGateway gateway;

    @Transactional
    public ReturnCase createReturn(Long orderId, String counter) {
        CustomerOrder order = em.find(CustomerOrder.class, orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在: " + orderId);
        }
        ReturnCase rc = new ReturnCase(order, counter, LocalDateTime.now());
        em.persist(rc);
        int order2 = 0;
        for (var c : order.getGiftBox().getComponents()) {
            rc.getLines().add(new ReturnLine(rc, c, order2++));
        }
        em.flush();
        return rc;
    }

    @Transactional
    public ReturnLine updateLine(Long returnId, Long lineId, Integer intact, Integer damaged,
                                 Integer missing, String note) {
        ReturnCase rc = mustGetReturn(returnId);
        ensureEditable(rc);
        ReturnLine line = rc.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("明细行不存在: " + lineId));
        int i = intact == null ? line.getIntactQty() : intact;
        int d = damaged == null ? line.getDamagedQty() : damaged;
        int m = missing == null ? line.getMissingQty() : missing;
        if (i < 0 || d < 0 || m < 0) {
            throw new BusinessException("数量不能为负");
        }
        if (i + d + m != line.getExpectedQty()) {
            throw new BusinessException("完好 %d + 破损 %d + 缺失 %d 必须等于应有数量 %d"
                    .formatted(i, d, m, line.getExpectedQty()));
        }
        line.setIntactQty(i);
        line.setDamagedQty(d);
        line.setMissingQty(m);
        if (note != null) {
            line.setNote(note);
        }
        em.flush();
        return line;
    }

    @Transactional
    public SupervisorNote addSupervisorNote(Long returnId, String supervisor, boolean approved,
                                            String opinion) {
        ReturnCase rc = mustGetReturn(returnId);
        SupervisorNote n = new SupervisorNote(rc, supervisor, approved, opinion, LocalDateTime.now());
        rc.getSupervisorNotes().add(n);
        em.persist(n);
        em.flush();
        return n;
    }

    @Transactional
    public InspectionPhoto attachPhoto(Long returnId, String fileName, String originalName,
                                       String contentType, long size, String uploadedBy) {
        ReturnCase rc = mustGetReturn(returnId);
        InspectionPhoto p = new InspectionPhoto(rc, fileName, originalName, contentType, size,
                uploadedBy, LocalDateTime.now());
        rc.getPhotos().add(p);
        em.persist(p);
        em.flush();
        return p;
    }

    /** 顾客确认退款明细：登记内容冻结，等待办理。 */
    @Transactional
    public ReturnCase confirm(Long returnId) {
        ReturnCase rc = mustGetReturn(returnId);
        if (rc.getStatus() != ReturnStatus.DRAFT) {
            throw new BusinessException("只有登记中的退货单可以确认，当前状态: " + rc.getStatus());
        }
        validateQuantities(rc);
        rc.setStatus(ReturnStatus.CONFIRMED);
        rc.setCustomerConfirmedAt(LocalDateTime.now());
        return rc;
    }

    /**
     * 办理退款 + 入库。幂等：同一退货单重复调用，直接返回既有结果；
     * 订单已被别的退货单办理时，本单记为 REJECTED 并留一条拒绝回执，不退不入库。
     */
    @Transactional
    public Completion complete(Long returnId) {
        ReturnCase rc = mustGetReturn(returnId);
        CustomerOrder order = rc.getOrder();

        // 幂等：本单已完成 —— 直接返回原回执，绝不二次退款/入库
        if (rc.getStatus() == ReturnStatus.COMPLETED) {
            RefundRecord existing = latestSuccess(rc);
            return new Completion(rc, existing, true, false);
        }
        // 已在并发中落败并留痕
        if (rc.getStatus() == ReturnStatus.REJECTED) {
            return new Completion(rc, latestRecord(rc), false, true);
        }

        // 登记中也允许直接办理（柜台现场确认），但先冻结校验一次
        validateQuantities(rc);

        // 1) 原子条件认领：只有 OPEN 的订单能更新为 REFUNDED
        int claimed = em.createQuery(
                        "update CustomerOrder o set o.returnState = :refunded "
                                + "where o.id = :id and o.returnState = :open")
                .setParameter("refunded", OrderReturnState.REFUNDED)
                .setParameter("open", OrderReturnState.OPEN)
                .setParameter("id", order.getId())
                .executeUpdate();

        if (claimed == 0) {
            // 另一柜台已办理：本单拒绝，仅留审计回执，不退款、不入库
            em.refresh(order);
            rc.setStatus(ReturnStatus.REJECTED);
            RefundRecord reject = new RefundRecord(rc, order.getId(), order.getCode(), null,
                    RefundResult.DUPLICATE_REJECTED, null, null,
                    "订单已由另一笔退货办理完成，本柜台请求被幂等拒绝，未退款、未入库",
                    LocalDateTime.now());
            em.persist(reject);
            return new Completion(rc, reject, false, true);
        }
        em.refresh(order);

        // 2) 权威报价（以已落库的登记明细为准）
        PricingService.Quote quote = pricing.quote(order, rc.getLines());
        if (quote.refundCents() + quote.deductionCents() != order.getComboPriceCents()) {
            throw new BusinessException("退款与扣减之和不等于售出组合价，禁止办理");
        }

        // 3) 模拟支付退款（成功才继续）
        String idemKey = "order-" + order.getId() + "-return-" + rc.getId();
        MockPaymentGateway.RefundResponse pay =
                gateway.refund(order.getCode(), quote.refundCents(), idemKey);
        RefundRecord refund = new RefundRecord(rc, order.getId(), order.getCode(),
                quote.refundCents(), RefundResult.SUCCESS, pay.receiptNo(), idemKey,
                "按组合价分摊计算，扣减 ¥" + com.giftbox.support.Money.yuan(quote.deductionCents())
                        + "，退款 ¥" + com.giftbox.support.Money.yuan(quote.refundCents()),
                LocalDateTime.now());
        em.persist(refund);

        // 4) 库存复原：全部完好才恢复整盒；否则只有完好散件各自入库
        boolean fullyIntact = rc.getLines().stream()
                .allMatch(l -> l.getMissingQty() == 0 && l.getDamagedQty() == 0);
        if (fullyIntact) {
            em.persist(StockEntry.box(order.getGiftBox(), 1, order.getCode(), rc.getId(),
                    LocalDateTime.now()));
        } else {
            for (ReturnLine l : rc.getLines()) {
                if (l.getIntactQty() > 0) {
                    em.persist(StockEntry.loose(l.getComponent(), l.getIntactQty(),
                            order.getCode(), rc.getId(), LocalDateTime.now()));
                }
            }
        }

        rc.setStatus(ReturnStatus.COMPLETED);
        rc.setCompletedAt(LocalDateTime.now());
        rc.setRefundCents(quote.refundCents());
        rc.setDeductionCents(quote.deductionCents());
        rc.setBoxRestored(fullyIntact);

        return new Completion(rc, refund, false, false);
    }

    /** 旧退款回执重放：只回显原记录，绝不再退、不再增加库存。 */
    @Transactional
    public RefundRecord replayReceipt(String receiptNo) {
        List<RefundRecord> hits = em.createQuery(
                        "select r from RefundRecord r where r.receiptNo = :no", RefundRecord.class)
                .setParameter("no", receiptNo)
                .getResultList();
        if (hits.isEmpty()) {
            throw new NotFoundException("回执不存在: " + receiptNo);
        }
        // 找到原成功回执即返回（幂等回显）；不写新记录、不调支付、不动库存
        return hits.get(0);
    }

    private void validateQuantities(ReturnCase rc) {
        for (ReturnLine l : rc.getLines()) {
            if (l.getIntactQty() < 0 || l.getDamagedQty() < 0 || l.getMissingQty() < 0
                    || l.getIntactQty() + l.getDamagedQty() + l.getMissingQty() != l.getExpectedQty()) {
                throw new BusinessException("组件「%s」登记数量与应有数量不一致".formatted(
                        l.getComponent().getName()));
            }
        }
    }

    private void ensureEditable(ReturnCase rc) {
        if (rc.getStatus() == ReturnStatus.COMPLETED) {
            throw new BusinessException("退货单已完成，不能修改登记结果");
        }
        if (rc.getStatus() == ReturnStatus.REJECTED) {
            throw new BusinessException("退货单已因并发办理被拒绝，不能修改");
        }
    }

    public ReturnCase mustGetReturn(Long id) {
        ReturnCase rc = em.find(ReturnCase.class, id);
        if (rc == null) {
            throw new NotFoundException("退货单不存在: " + id);
        }
        return rc;
    }

    private RefundRecord latestSuccess(ReturnCase rc) {
        return rc.getRefunds().stream()
                .filter(r -> r.getResult() == RefundResult.SUCCESS)
                .findFirst()
                .orElseGet(() -> latestRecord(rc));
    }

    private RefundRecord latestRecord(ReturnCase rc) {
        return rc.getRefunds().stream().findFirst().orElse(null);
    }

    /** 办理结果：alreadyDone=本单幂等重放；rejected=并发落败。 */
    public record Completion(ReturnCase returnCase, RefundRecord refundRecord,
                             boolean alreadyDone, boolean rejected) {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) {
            super(m);
        }
    }

    public static class BusinessException extends RuntimeException {
        public BusinessException(String m) {
            super(m);
        }
    }
}
