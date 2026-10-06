package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.model.InspectionPhoto;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;

/** 检查照片文件下载 */
@Path("/api/photos")
public class PhotoResource {

    @ConfigProperty(name = "giftbox.photos.dir")
    String photosDir;

    @GET
    @Path("/{id}")
    @Transactional
    public Response file(@PathParam("id") long id) {
        InspectionPhoto photo = InspectionPhoto.findById(id);
        if (photo == null) {
            throw new NotFoundException("照片不存在: " + id);
        }
        File f = java.nio.file.Path.of(photosDir).resolve(photo.filename).toFile();
        if (!f.isFile()) {
            throw new NotFoundException("照片文件缺失: " + photo.filename);
        }
        String type = photo.filename.toLowerCase().endsWith(".png") ? "image/png"
                : photo.filename.toLowerCase().endsWith(".gif") ? "image/gif"
                : photo.filename.toLowerCase().endsWith(".webp") ? "image/webp"
                : "image/jpeg";
        return Response.ok(f, type).build();
    }
}
