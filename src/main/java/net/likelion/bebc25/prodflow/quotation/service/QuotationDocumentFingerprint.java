package net.likelion.bebc25.prodflow.quotation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import net.likelion.bebc25.prodflow.common.storage.StorageClient;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;

public final class QuotationDocumentFingerprint {
    public static final String METADATA_KEY = "confirmation-token";

    // 확인값 유틸리티 인스턴스 생성 차단
    private QuotationDocumentFingerprint() {
    }

    // 견적 내용과 PDF의 확인값 생성
    public static String create(QuotationDto.DetailResponse draft, byte[] pdf) {
        StringBuilder source = new StringBuilder();
        append(source, draft.id(), draft.quotationNo(), draft.clientId(), draft.clientCompany(), draft.clientName(),
                draft.clientEmail(), draft.clientPhone(), draft.clientAddress(), draft.createdAt().toLocalDate(),
                draft.validUntil(), draft.createdBy(), draft.vatIncluded(), draft.supplyAmount(), draft.vatAmount(),
                draft.totalAmount(), draft.requestedDueDate(), draft.estimatedDueDate(), draft.plannedStartDate(),
                draft.plannedEndDate());
        for (var item : draft.items()) {
            append(source, item.id(), item.productId(), item.productName(), item.deviceName(), item.quantity(),
                    item.unitPrice(), item.lineAmount());
        }
        return digest(source.toString().getBytes(StandardCharsets.UTF_8)) + "." + digest(pdf);
    }

    // 현재 초안과 저장 PDF의 일치 여부 확인
    public static boolean matches(QuotationDto.DetailResponse draft, StorageClient.StoredFile file) {
        return create(draft, file.content()).equals(file.metadata().get(METADATA_KEY));
    }

    // 저장 PDF 내용의 확인값 검증
    public static boolean hasValidContent(StorageClient.StoredFile file) {
        String token = file.metadata().get(METADATA_KEY);
        return token != null && token.matches("[a-f0-9]{64}\\.[a-f0-9]{64}")
                && token.substring(65).equals(digest(file.content()));
    }

    // 필드 경계를 구분한 확인값 원문 구성
    private static void append(StringBuilder source, Object... values) {
        for (Object value : values) {
            if (value == null) {
                source.append("-1:");
            } else {
                String text = value.toString();
                source.append(text.length()).append(':').append(text);
            }
        }
    }

    // SHA-256 확인값 계산
    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", ex);
        }
    }
}
