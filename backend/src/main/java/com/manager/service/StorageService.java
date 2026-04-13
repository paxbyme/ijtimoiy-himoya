package com.manager.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.firebase.cloud.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    @Value("${firebase.storage.bucket:}")
    private String bucket;

    public String uploadFile(byte[] bytes, String path, String contentType) throws Exception {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Firebase Storage bucket is not configured (FIREBASE_STORAGE_BUCKET)");
        }

        String downloadToken = UUID.randomUUID().toString();
        Storage storage = StorageClient.getInstance().bucket(bucket).getStorage();
        BlobId blobId = BlobId.of(bucket, path);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .setMetadata(Map.of("firebaseStorageDownloadTokens", downloadToken))
                .build();
        storage.create(blobInfo, bytes);

        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        return "https://firebasestorage.googleapis.com/v0/b/" + bucket + "/o/"
                + encodedPath + "?alt=media&token=" + downloadToken;
    }
}
