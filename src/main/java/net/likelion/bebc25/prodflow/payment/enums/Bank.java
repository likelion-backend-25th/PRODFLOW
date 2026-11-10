package net.likelion.bebc25.prodflow.payment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Bank {
    KOOKMIN("KB국민은행"),
    SHINHAN("신한은행"),
    WOORI("우리은행"),
    HANA("하나은행"),
    NONGHYUP("NH농협은행"),
    IBK("IBK기업은행"),
    BUSAN("부산은행"),
    KYONGNAM("경남은행"),
    KWANGJU("광주은행"),
    DAEGU("iM뱅크(대구은행)"),
    SUHYUP("Sh수협은행"),
    KFCC("새마을금고"),
    POST("우체국");

    private final String label;
}
