package net.likelion.bebc25.prodflow.product.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

public class S3StorageClient implements StorageClient {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3StorageClient(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, String contentType, long contentLength) {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalStateException("AWS_S3_BUCKET 이 설정되지 않았습니다.");
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
    }
}
