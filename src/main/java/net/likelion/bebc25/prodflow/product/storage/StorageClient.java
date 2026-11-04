package net.likelion.bebc25.prodflow.product.storage;

import java.io.InputStream;

// 파일 저장
public interface StorageClient {

    void upload(String objectKey, InputStream inputStream, String contentType, long contentLength);
}
