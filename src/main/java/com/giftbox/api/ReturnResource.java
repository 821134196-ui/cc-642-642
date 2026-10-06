package com.giftbox.api;

import com.giftbox.domain.InspectionPhoto;
import com.giftbox.domain.ReturnCase;
import com.giftbox.service.QueryService;
import com.giftbox.service.ReturnService;
import com.giftbox.storage.PhotoStorageService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/** 退货办理全流程接口。 */
@Path("/api/returns")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ReturnResource {

    @Inject
    ReturnService returns;

    @Inject
    QueryService queries;

    @Inject
    ViewMapper mapper;

    @Inject
    PhotoStorageService photos;

    public record CreateReturnRequest(Long orderId, String counter) {
    }

    public record LineUpdate(Integer intactQty, Integer damagedQty, Integer missingQty, String note) {
    }

    public record NoteRequest(String supervisor, Boolean approved, String opinion) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> create(CreateReturnRequest req) {
        if (req == null || req.orderId() == null) {
            throw new IllegalArgumentException("orderId 必填");
        }
        String counter = req.counter() == null || req.counter().isBlank() ? "未命名柜台" : req.counter();
        ReturnCase rc = returns.createReturn(req.orderId(), counter);
        return mapper.returnCase(rc, true);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        ReturnCase rc = queries.getReturn(id);
        if (rc == null) {
            return Response.status(404).entity(Map.of("error", "退货单不存在")).build();
        }
        return Response.ok(mapper.returnCase(rc, true)).build();
    }

    /** 逐件登记：完好/破损/缺失数量与备注。 */
    @PUT
    @Path("/{id}/lines/{lineId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateLine(@PathParam("id") Long id,
                                          @PathParam("lineId") Long lineId,
                                          LineUpdate req) {
        returns.updateLine(id, lineId, req == null ? null : req.intactQty(),
                req == null ? null : req.damagedQty(),
                req == null ? null : req.missingQty(),
                req == null ? null : req.note());
        ReturnCase rc = returns.mustGetReturn(id);
        return mapper.returnCase(rc, true);
    }

    /** 主管意见。 */
    @POST
    @Path("/{id}/supervisor-notes")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> addNote(@PathParam("id") Long id, NoteRequest req) {
        if (req == null || req.supervisor() == null || req.supervisor().isBlank()) {
            throw new IllegalArgumentException("supervisor 必填");
        }
        boolean approved = req.approved() != null && req.approved();
        returns.addSupervisorNote(id, req.supervisor(), approved, req.opinion());
        return mapper.returnCase(returns.mustGetReturn(id), true);
    }

    /** 检查照片：原始字节直传，文件名/上传者走请求头，本地保存。 */
    @POST
    @Path("/{id}/photos")
    @Consumes({"image/jpeg", "image/png", "image/webp", "application/octet-stream"})
    public Map<String, Object> uploadPhoto(@PathParam("id") Long id,
                                           @jakarta.ws.rs.HeaderParam("X-Filename") String filename,
                                           @jakarta.ws.rs.HeaderParam("X-Uploaded-By") String uploadedBy,
                                           @jakarta.ws.rs.HeaderParam("Content-Type") String contentType,
                                           byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("照片内容为空");
        }
        if (data.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("照片不能超过 10MB");
        }
        String realName = decodeHeader(filename);
        String by0 = decodeHeader(uploadedBy);
        String by = by0 == null || by0.isBlank() ? "柜台" : by0;
        PhotoStorageService.SavedPhoto saved = photos.save(id, realName, contentType, data);
        InspectionPhoto p = returns.attachPhoto(id, saved.fileName(), realName,
                contentType == null ? "application/octet-stream" : contentType,
                saved.sizeBytes(), by);
        return Map.of(
                "id", p.getId(),
                "fileName", p.getFileName(),
                "originalName", p.getOriginalName() == null ? "" : p.getOriginalName(),
                "url", "/api/returns/" + id + "/photos/" + p.getId() + "/raw");
    }

    @GET
    @Path("/{id}/photos/{photoId}/raw")
    public Response photoRaw(@PathParam("id") Long id, @PathParam("photoId") Long photoId)
            throws IOException {
        ReturnCase rc = returns.mustGetReturn(id);
        InspectionPhoto p = rc.getPhotos().stream()
                .filter(x -> x.getId().equals(photoId)).findFirst()
                .orElseThrow(() -> new ReturnService.NotFoundException("照片不存在"));
        java.nio.file.Path path = photos.resolve(id, p.getFileName());
        if (!Files.exists(path)) {
            return Response.status(404).entity(Map.of("error", "照片文件丢失")).build();
        }
        return Response.ok(Files.readAllBytes(path))
                .type(p.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : p.getContentType())
                .header("Content-Disposition", "inline; filename=\"" + p.getFileName() + "\"")
                .build();
    }

    /** 顾客确认退款明细。 */
    @POST
    @Path("/{id}/confirm")
    public Map<String, Object> confirm(@PathParam("id") Long id) {
        returns.confirm(id);
        return mapper.returnCase(returns.mustGetReturn(id), true);
    }

    /** 办理退款 + 入库（并发安全、重复调用幂等）。 */
    @POST
    @Path("/{id}/complete")
    public Map<String, Object> complete(@PathParam("id") Long id) {
        ReturnService.Completion c = returns.complete(id);
        Map<String, Object> m = mapper.returnCase(c.returnCase(), true);
        m.put("alreadyDone", c.alreadyDone());
        m.put("rejected", c.rejected());
        if (c.refundRecord() != null) {
            m.put("resultReceipt", c.refundRecord().getReceiptNo());
            m.put("result", c.refundRecord().getResult().name());
        }
        return m;
    }

    /**
     * 旧退款回执重放：仅回显原成功回执，不会再次退款，也不会增加库存。
     */
    @POST
    @Path("/replay-receipt/{receiptNo}")
    public Map<String, Object> replay(@PathParam("receiptNo") String receiptNo) {
        return mapper.refund(returns.replayReceipt(receiptNo));
    }

    /** 解码经 encodeURIComponent 编码的请求头（中文文件名/上传者）。 */
    private static String decodeHeader(String v) {
        if (v == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return v;
        }
    }
}
