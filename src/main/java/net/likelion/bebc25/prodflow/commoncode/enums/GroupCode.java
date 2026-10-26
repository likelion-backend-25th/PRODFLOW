package net.likelion.bebc25.prodflow.commoncode.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupCode {

    DEVICE("기종"),
    ORDER_STATUS("수주 진행 상태");

    private final String groupName;
}
