package com.giftbox.storage;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/** 检查照片本地保存：按退货单建子目录，文件名随机化，限定扩展名。 */
@ApplicationScoped
public class PhotoStorageService {

    @ConfigProperty(name = "giftbox.storage.photo-dir")
    String photoDir;

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(dir());
    }

    public Path dir() {
        return Paths.get(photoDir).toAbsolutePath().normalize();
    }

    public Path caseDir(Long returnId) throws IOException {
        Path d = dir().resolve("return-" + returnId).normalize();
        Files.createDirectories(d);
        return d;
    }

    public SavedPhoto save(Long returnId, String originalName, String contentType, byte[] data)
            throws IOException {
        String ext = sanitizeExt(originalName, contentType);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = caseDir(returnId).resolve(fileName).normalize();
        Files.write(target, data);
        return new SavedPhoto(fileName, target, contentType, (long) data.length);
    }

    public Path resolve(Long returnId, String fileName) {
        Path base = dir().resolve("return-" + returnId).normalize();
        Path p = base.resolve(fileName).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return p;
    }

    private String sanitizeExt(String originalName, String contentType) {
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (name.endsWith(".png")) {
            return ".png";
        }
        if (name.endsWith(".webp")) {
            return ".webp";
        }
        if (contentType != null && contentType.contains("png")) {
            return ".png";
        }
        return ".jpg";
    }

    public record SavedPhoto(String fileName, Path path, String contentType, Long sizeBytes) {
    }
}
