package net.likelion.bebc25.prodflow.common.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

// 파일 저장
public interface StorageClient {

    // 파일 저장
    void upload(String objectKey, InputStream inputStream, String contentType, long contentLength);

    // 메타데이터를 포함한 파일 저장
    void upload(String objectKey, InputStream inputStream, String contentType, long contentLength,
                Map<String, String> metadata);

    // 파일 내용과 메타데이터 조회
    StoredFile read(String objectKey);

    // 저장 파일 삭제
    void delete(String objectKey);

    // 기간 제한 조회 URL 발급
    String createPresignedGetUrl(String objectKey, Duration expiration);

    record StoredFile(byte[] content, Map<String, String> metadata) {
    }
}
