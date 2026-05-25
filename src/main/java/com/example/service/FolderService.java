package com.example.service;

public interface FolderService {
    String createFolder(String basePath, String name);

    void deleteFolder(String basePath, String name);
}
