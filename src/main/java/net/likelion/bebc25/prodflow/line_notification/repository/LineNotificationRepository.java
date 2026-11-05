package net.likelion.bebc25.prodflow.line_notification.repository;

import net.likelion.bebc25.prodflow.line_notification.entity.LineNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineNotificationRepository extends JpaRepository<LineNotification, Long> {

    // 특정 사용자 알림 목록 (최신순)
    List<LineNotification> findByTarget_IdOrderByCreatedAtDesc(Long targetId);

    // 특정 사용자의 읽지 않은 알림 수
    long countByTarget_IdAndReadAtIsNull(Long targetId);

    // 본인에게 온 알림인지 확인하며 조회
    Optional<LineNotification> findByIdAndTarget_Id(Long notificationId, Long targetId);

}
