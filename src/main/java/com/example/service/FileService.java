package com.example.service;

import com.example.domain.requests.FileUpload;

public interface FileService {
    String createFileImage(FileUpload fileUpload, String filepath);

    void deleteFileImage(String filepath);
}
