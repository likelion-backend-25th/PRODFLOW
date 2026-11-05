package net.likelion.bebc25.prodflow.common.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

public class S3StorageClient implements StorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3StorageClient(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, String contentType, long contentLength) {
        validateBucket();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
    }

    @Override
    public void delete(String objectKey) {
        validateBucket();
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build());
    }

    @Override
    public String createPresignedGetUrl(String objectKey, Duration expiration) {
        validateBucket();
        if (expiration == null || expiration.isNegative() || expiration.isZero()
                || expiration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("조회 URL 유효기간은 0초 초과 7일 이하여야 합니다.");
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .responseContentDisposition("inline")
                .responseCacheControl("no-store")
                .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .getObjectRequest(request)
                        .build())
                .url().toString();
    }

    private void validateBucket() {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalStateException("AWS_S3_BUCKET 이 설정되지 않았습니다.");
        }
    }
}
