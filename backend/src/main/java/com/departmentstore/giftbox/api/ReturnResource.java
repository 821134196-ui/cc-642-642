package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.api.dto.Dtos.PhotoDto;
import com.departmentstore.giftbox.api.dto.Dtos.ReturnDto;
import com.departmentstore.giftbox.model.InspectionPhoto;
import com.departmentstore.giftbox.model.Refund;
import com.departmentstore.giftbox.model.ReturnRequest;
import com.departmentstore.giftbox.model.SupervisorNote;
import com.departmentstore.giftbox.service.ReturnService;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 退货办理：建单、逐件核对、报价、顾客确认退款、照片与主管意见 */
@Path("/api/returns")
@Produces(MediaType.APPLICATION_JSON)
public class ReturnResource {

    @Inject
    ReturnService returnService;

    @ConfigProperty(name = "giftbox.photos.dir")
    String photosDir;

    public record CreateReturnRequest(long orderItemId, String counterId, String idempotencyKey) {
    }

    public record UpdateLinesRequest(List<ReturnService.LineInput> lines, boolean inspectionPassed) {
    }

    public record ConfirmRequest(String counterId) {
    }

    public record NoteRequest(String author, String text) {
    }

    // ---------- 建单（幂等） ----------

    @POST
    public ReturnDto create(CreateReturnRequest req) {
        if (req == null || req.counterId() == null || req.counterId().isBlank()
                || req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            throw new BadRequestException("counterId 与 idempotencyKey 必填");
        }
        try {
            ReturnRequest rr = returnService.createReturn(req.orderItemId(), req.counterId(), req.idempotencyKey());
            return assemble(rr.id);
        } catch (PersistenceException e) {
            // 并发建单撞唯一约束：同一幂等键 → 返回已有单据；否则说明该订单行已有退货单
            ReturnRequest byKey = returnService.findByIdempotencyKey(req.idempotencyKey());
            if (byKey != null) {
                return assemble(byKey.id);
            }
            throw new WebApplicationException("该订单行已存在退货单，不能重复建单", 409);
        }
    }

    // ---------- 查询 ----------

    @GET
    @Path("/{id}")
    public ReturnDto get(@PathParam("id") long id) {
        return assemble(id);
    }

    // ---------- 逐件核对 ----------

    @PUT
    @Path("/{id}/lines")
    public ReturnDto updateLines(@PathParam("id") long id, UpdateLinesRequest req) {
        if (req == null || req.lines() == null) {
            throw new BadRequestException("lines 必填");
        }
        ReturnRequest rr = returnService.updateLines(id, req.lines(), req.inspectionPassed());
        return assemble(rr.id);
    }

    // ---------- 报价 ----------

    @POST
    @Path("/{id}/quote")
    public ReturnDto quote(@PathParam("id") long id) {
        ReturnRequest rr = returnService.quote(id);
        return assemble(rr.id);
    }

    // ---------- 顾客确认退款（幂等 + 并发安全） ----------

    @POST
    @Path("/{id}/confirm")
    public ReturnDto confirm(@PathParam("id") long id, ConfirmRequest req) {
        String counterId = req != null && req.counterId() != null && !req.counterId().isBlank()
                ? req.counterId() : "COUNTER";
        try {
            Refund refund = returnService.confirm(id, counterId);
            if (refund == null) {
                throw new WebApplicationException("退款状态异常", 500);
            }
            return assemble(id);
        } catch (PersistenceException e) {
            // 两个柜台同时确认：乐观锁/唯一约束拦截后，另一笔已生效，返回同一回执
            Refund existing = returnService.findRefundByReturnId(id);
            if (existing != null) {
                return assemble(id);
            }
            throw new WebApplicationException("退货处理冲突，请刷新后重试", 409);
        }
    }

    // ---------- 检查照片（本地保存） ----------

    @POST
    @Path("/{id}/photos")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public PhotoDto uploadPhoto(@PathParam("id") long id,
                                @RestForm("file") FileUpload file,
                                @RestForm("note") String note) throws IOException {
        if (file == null) {
            throw new BadRequestException("缺少照片文件");
        }
        ReturnRequest rr = returnService.requireReturn(id);

        String original = file.fileName() == null ? "photo" : file.fileName();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1 && original.length() - dot <= 6) {
            ext = original.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
        }
        if (ext.isEmpty()) {
            ext = ".jpg";
        }
        String filename = "ret" + id + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ext;
        java.nio.file.Path dir = java.nio.file.Path.of(photosDir);
        Files.createDirectories(dir);
        Files.copy(file.uploadedFile(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        InspectionPhoto photo = addPhoto(rr, filename, original, note);
        return Mappers.toDto(photo);
    }

    @Transactional
    InspectionPhoto addPhoto(ReturnRequest rr, String filename, String original, String note) {
        InspectionPhoto photo = new InspectionPhoto();
        photo.returnRequest = rr;
        photo.filename = filename;
        photo.originalName = original;
        photo.note = note;
        photo.uploadedAt = LocalDateTime.now();
        photo.persist();
        return photo;
    }

    // ---------- 主管意见 ----------

    @POST
    @Path("/{id}/notes")
    @Transactional
    public ReturnDto addNote(@PathParam("id") long id, NoteRequest req) {
        if (req == null || req.author() == null || req.author().isBlank()
                || req.text() == null || req.text().isBlank()) {
            throw new BadRequestException("author 与 text 必填");
        }
        ReturnRequest rr = returnService.requireReturn(id);
        SupervisorNote note = new SupervisorNote();
        note.returnRequest = rr;
        note.author = req.author();
        note.text = req.text();
        note.createdAt = LocalDateTime.now();
        note.persist();
        return assemble(id);
    }

    // ---------- 装配 ----------

    @Transactional
    ReturnDto assemble(long returnId) {
        ReturnRequest rr = returnService.requireReturn(returnId);
        Refund refund = Refund.findByReturnRequest(rr);
        List<InspectionPhoto> photos = InspectionPhoto.list("returnRequest", rr);
        List<SupervisorNote> notes = SupervisorNote.list("returnRequest", rr);
        return Mappers.toDto(rr, refund, photos, notes);
    }
}
