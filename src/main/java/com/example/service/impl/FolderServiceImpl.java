package com.example.service.impl;

import java.io.File;
import jakarta.enterprise.context.ApplicationScoped;
import com.example.service.FolderService;

@ApplicationScoped
public class FolderServiceImpl implements FolderService {
    @Override
    public String createFolder(String basePath, String name) {
        String folderPath = basePath + File.separator + name;

        File folder = new File(folderPath);

        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("❌ Failed to create directory: " + folderPath);
            return null;
        }

        return folderPath;
    }

    @Override
    public void deleteFolder(String basePath, String name) {
        String folderPath = basePath + File.separator + name;

        File folder = new File(folderPath);

        if (!folder.exists()) {
            System.err.println("⚠️ Directory does not exist: " + folderPath);
            return;
        }

        if (!deleteRecursive(folder)) {
            System.err.println("❌ Failed to delete directory: " + folderPath);
        } else {
            System.out.println("✅ Deleted directory: " + folderPath);
        }
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    if (!deleteRecursive(f)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }
}
