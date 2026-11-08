package net.likelion.bebc25.prodflow.payment.policy;

import net.likelion.bebc25.prodflow.payment.enums.PaymentType;

import java.util.Objects;

public final class PaymentAmountPolicy {

    private PaymentAmountPolicy() {
    }

    public static long calculate(Long totalAmount, PaymentType paymentType) {
        Objects.requireNonNull(paymentType, "결제 유형은 필수입니다.");
        if (totalAmount == null || totalAmount < 2) {
            throw new IllegalArgumentException("수주 총액은 계약금과 잔금을 모두 1원 이상으로 나눌 수 있어야 합니다.");
        }

        long deposit = totalAmount / 2;
        return switch (paymentType) {
            case DEPOSIT -> deposit;
            case BALANCE -> totalAmount - deposit;
        };
    }
}
