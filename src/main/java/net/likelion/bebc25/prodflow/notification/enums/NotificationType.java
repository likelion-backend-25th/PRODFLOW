package net.likelion.bebc25.prodflow.notification.enums;

public enum NotificationType {
    // 납기 관련 알림 (납기 임박, 납기 지연)
    DELIVERY_IMMINENT,
    DELIVERY_DELAYED,

    // 생산라인 관련 알림 (라인 고장, 라인 점검 중, 라인 점검 완료)
    LINE_FAILURE,
    LINE_INSPECTION,
    LINT_INSPECTION_COMPLETE
}
