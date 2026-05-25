package com.example.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.domain.requests.FileUpload;
import com.example.service.FileService;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FileServiceImpl implements FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    @Override
    public String createFileImage(FileUpload fileUpload, String targetPathStr) {
        try {
            if (fileUpload == null || fileUpload.base64Data() == null) {
                logger.error("❌ FileUpload or base64Data is null");
                return null;
            }

            // Remove base64 header if present (e.g., "data:image/png;base64,")
            String base64Data = fileUpload.base64Data();
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] fileBytes = Base64.getDecoder().decode(base64Data.trim());
            Path targetPath = Paths.get(targetPathStr).toAbsolutePath().normalize();

            // Create parent directories if they don't exist
            Files.createDirectories(targetPath.getParent());

            // Write bytes directly to target path
            Files.write(targetPath, fileBytes);
            logger.info("💾 File successfully saved to: {}", targetPath);

            String normalizedPath = targetPath.toString().replace("\\", "/");
            int resourcesIdx = normalizedPath.indexOf("META-INF/resources/");
            if (resourcesIdx != -1) {
                return normalizedPath.substring(resourcesIdx + "META-INF/resources/".length());
            }

            return targetPath.toString();
        } catch (IOException | IllegalArgumentException e) {
            logger.error("Failed to save file to path: {}", targetPathStr, e);
            return null;
        }
    }

    @Override
    public void deleteFileImage(String filePathStr) {
        if (filePathStr == null || filePathStr.trim().isEmpty()) {
            return;
        }

        try {
            String targetPathStr = "src/main/resources/META-INF/resources/" + filePathStr;
            Path targetPath = Paths.get(targetPathStr).toAbsolutePath().normalize();

            if (Files.exists(targetPath)) {
                Files.delete(targetPath);
                logger.info("🗑️ File successfully deleted: {}", targetPath);
            }
        } catch (IOException e) {
            logger.error("💥 Failed to delete file: {}", filePathStr, e);
        }
    }
}
