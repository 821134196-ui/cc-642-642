package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.api.dto.Dtos.DeductionDto;
import com.departmentstore.giftbox.api.dto.Dtos.LineDto;
import com.departmentstore.giftbox.api.dto.Dtos.NoteDto;
import com.departmentstore.giftbox.api.dto.Dtos.OrderDetailDto;
import com.departmentstore.giftbox.api.dto.Dtos.OrderItemDto;
import com.departmentstore.giftbox.api.dto.Dtos.PhotoDto;
import com.departmentstore.giftbox.api.dto.Dtos.RefundDto;
import com.departmentstore.giftbox.api.dto.Dtos.ReturnDto;
import com.departmentstore.giftbox.model.CustomerOrder;
import com.departmentstore.giftbox.model.DeductionLine;
import com.departmentstore.giftbox.model.InspectionPhoto;
import com.departmentstore.giftbox.model.OrderItem;
import com.departmentstore.giftbox.model.Refund;
import com.departmentstore.giftbox.model.ReturnLine;
import com.departmentstore.giftbox.model.ReturnRequest;
import com.departmentstore.giftbox.model.SupervisorNote;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 实体 → DTO 装配（须在事务内调用，避免懒加载问题） */
public final class Mappers {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Mappers() {
    }

    private static String fmt(java.time.LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }

    public static LineDto toDto(ReturnLine l) {
        return new LineDto(l.id, l.product.sku, l.product.name, l.product.kind.name(),
                l.expectedQty, l.receivedQty, l.intactQty, l.damagedQty, l.missingQty(),
                l.allocatedTotalCents, l.unitAllocCents, l.product.listPriceCents);
    }

    public static DeductionDto toDto(DeductionLine d) {
        return new DeductionDto(d.id, d.product.sku, d.product.name, d.category.name(),
                d.qty, d.unitPriceCents, d.amountCents, d.basis);
    }

    public static RefundDto toDto(Refund r) {
        if (r == null) {
            return null;
        }
        return new RefundDto(r.id, r.receiptNo, r.amountCents, r.paymentTxnId, r.counterId, fmt(r.createdAt));
    }

    public static PhotoDto toDto(InspectionPhoto p) {
        return new PhotoDto(p.id, "/api/photos/" + p.id, p.originalName, p.note, fmt(p.uploadedAt));
    }

    public static NoteDto toDto(SupervisorNote n) {
        return new NoteDto(n.id, n.author, n.text, fmt(n.createdAt));
    }

    public static ReturnDto toDto(ReturnRequest rr, Refund refund,
                                  List<InspectionPhoto> photos, List<SupervisorNote> notes) {
        OrderItem item = rr.orderItem;
        return new ReturnDto(rr.id, item.id, item.order.orderNo,
                item.giftBox.sku, item.giftBox.name, item.qty,
                rr.counterId, rr.status.name(), rr.inspectionPassed,
                rr.totalComboCents, rr.quotedAmountCents,
                rr.lines.stream().map(Mappers::toDto).toList(),
                rr.deductions.stream().map(Mappers::toDto).toList(),
                toDto(refund),
                photos.stream().map(Mappers::toDto).toList(),
                notes.stream().map(Mappers::toDto).toList(),
                fmt(rr.createdAt), fmt(rr.refundedAt));
    }

    public static OrderItemDto toItemDto(OrderItem item, Long returnId) {
        return new OrderItemDto(item.id, item.giftBox.sku, item.giftBox.name,
                item.qty, item.comboPriceCents, item.status.name(), returnId);
    }

    public static OrderDetailDto toDto(CustomerOrder order, List<OrderItemDto> items) {
        return new OrderDetailDto(order.orderNo, order.customerName, order.customerPhone,
                fmt(order.createdAt), items);
    }
}
