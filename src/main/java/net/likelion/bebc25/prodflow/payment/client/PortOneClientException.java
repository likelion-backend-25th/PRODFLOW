package net.likelion.bebc25.prodflow.payment.client;

/**
 * uncertain=true 이면 실제 PG 발급 결과를 알 수 없는 상태입니다.
 * 새 paymentId로 무조건 재발급하지 말고 GET 조회 / 동일 멱등키 재시도를 먼저 수행해야 합니다.
 */
public class PortOneClientException extends RuntimeException {
    private final boolean uncertain;

    public PortOneClientException(String message, boolean uncertain, Throwable cause) {
        super(message, cause);
        this.uncertain = uncertain;
    }

    public boolean isUncertain() {
        return uncertain;
    }
}
