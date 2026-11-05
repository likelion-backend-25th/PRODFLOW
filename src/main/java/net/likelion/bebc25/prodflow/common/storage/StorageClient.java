package net.likelion.bebc25.prodflow.common.storage;

import java.io.InputStream;
import java.time.Duration;

// 파일 저장
public interface StorageClient {

    void upload(String objectKey, InputStream inputStream, String contentType, long contentLength);

    void delete(String objectKey);

    String createPresignedGetUrl(String objectKey, Duration expiration);
}
