package com.departmentstore.giftbox.api.dto;

import java.util.List;

/** API 视图对象（金额一律为分，前端负责格式化） */
public final class Dtos {

    private Dtos() {
    }

    public record LineDto(long id, String sku, String name, String kind,
                          int expectedQty, int receivedQty, int intactQty, int damagedQty, int missingQty,
                          long allocatedTotalCents, long unitAllocCents, long listPriceCents) {
    }

    public record DeductionDto(long id, String sku, String name, String category,
                               int qty, long unitPriceCents, long amountCents, String basis) {
    }

    public record RefundDto(long id, String receiptNo, long amountCents,
                            String paymentTxnId, String counterId, String createdAt) {
    }

    public record PhotoDto(long id, String url, String originalName, String note, String uploadedAt) {
    }

    public record NoteDto(long id, String author, String text, String createdAt) {
    }

    public record ReturnDto(long id, long orderItemId, String orderNo,
                            String giftBoxSku, String giftBoxName, int boxQty,
                            String counterId, String status, boolean inspectionPassed,
                            long totalComboCents, long quotedAmountCents,
                            List<LineDto> lines, List<DeductionDto> deductions,
                            RefundDto refund, List<PhotoDto> photos, List<NoteDto> notes,
                            String createdAt, String refundedAt) {
    }

    public record OrderItemDto(long id, String giftBoxSku, String giftBoxName,
                               int qty, long comboPriceCents, String status, Long returnId) {
    }

    public record OrderDetailDto(String orderNo, String customerName, String customerPhone,
                                 String createdAt, List<OrderItemDto> items) {
    }

    public record OrderSummaryDto(String orderNo, String customerName, String createdAt,
                                  int itemCount, long totalCents) {
    }

    public record InventoryDto(String sku, String name, String skuKind, String state, long qty) {
    }

    public record ErrorDto(String error) {
    }
}
