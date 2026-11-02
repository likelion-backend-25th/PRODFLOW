package net.likelion.bebc25.prodflow.product.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yaml 의 aws.* 값을 자바 객체로 담는 설정 클래스
@ConfigurationProperties(prefix = "aws") // yaml 에서 aws: 아래 키들과 매핑
public record S3Properties(
        String region, // aws.region → S3 리전 (예: ap-northeast-2)
        S3 s3          // aws.s3: 아래 중첩 설정 (bucket 등)
) {
    // aws.s3.bucket 에 대응
    public record S3(
            String bucket // S3 버킷 이름 (.env 의 AWS_S3_BUCKET)
    ) {
    }

    // 호출부에서 properties.bucket() 으로 바로 쓰려고 만든 편의 메서드
    public String bucket() {
        return s3.bucket(); // 중첩 s3.bucket 값을 꺼내 반환
    }
}
