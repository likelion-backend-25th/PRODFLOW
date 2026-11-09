package net.likelion.bebc25.prodflow.customer_order.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomerOrderProgressService {

    private final CommonCodeRepository commonCodeRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    // 호출한 업무와 같은 트랜잭션에서 수주 진행 상태와 이력을 함께 반영한다.
    public void changeProgress(CustomerOrder order, String status, User actor, String note) {
        String before = order.getStatusCode().getCode();
        if (before.equals(status)) {
            return;
        }
        CommonCode next = commonCodeRepository
                .findByCodeGroup_GroupCodeAndCodeAndStatus("ORDER_STATUS", status, CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("수주 상태 코드를 찾을 수 없습니다."));
        order.changeProgress(next);
        StatusHistory history = new StatusHistory(HistoryEntityType.ORDER, order.getId(), before, status, actor);
        history.writeNote(note);
        statusHistoryRepository.save(history);
    }
}
