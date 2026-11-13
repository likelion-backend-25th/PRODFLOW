package net.likelion.bebc25.prodflow.customer_order.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerOrderProgressServiceTest {

    @Autowired
    private CustomerOrderProgressService progressService;
    @Autowired
    private CustomerOrderService customerOrderService;
    @Autowired
    private StatusHistoryRepository statusHistoryRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("수주 진행 상태와 처리자·사유를 저장하고 기존 수주 이력 조회로 반환한다")
    void changeProgressWithHistory() {
        CustomerOrder order = em.find(CustomerOrder.class, 13L);
        User actor = em.find(User.class, 1L);
        String before = order.getStatusCode().getCode();
        String target = "IN_PRODUCTION".equals(before) ? "PACKING" : "IN_PRODUCTION";
        int historyCount = customerOrderService.getHistories(13L).size();

        progressService.changeProgress(order, target, actor, "작업지시 WO-2026-015 생산 시작");
        em.flush();
        em.clear();

        assertThat(em.find(CustomerOrder.class, 13L).getStatusCode().getCode()).isEqualTo(target);
        var histories = customerOrderService.getHistories(13L);
        assertThat(histories).hasSize(historyCount + 1);
        assertThat(histories.getFirst()).satisfies(history -> {
            assertThat(history.fromStatus()).isEqualTo(before);
            assertThat(history.toStatus()).isEqualTo(target);
            assertThat(history.userId()).isEqualTo(1L);
            assertThat(history.userName()).isEqualTo("서도윤");
            assertThat(history.note()).isEqualTo("작업지시 WO-2026-015 생산 시작");
        });
    }

    @Test
    @DisplayName("이미 같은 수주 상태면 변경 이력을 추가하지 않는다")
    void skipUnchangedProgress() {
        CustomerOrder order = em.find(CustomerOrder.class, 13L);
        long count = statusHistoryRepository.count();

        progressService.changeProgress(order, order.getStatusCode().getCode(), em.find(User.class, 1L), "상태 유지");
        em.flush();

        assertThat(statusHistoryRepository.count()).isEqualTo(count);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("호출한 업무가 실패하면 수주 진행 상태와 이력도 함께 롤백한다")
    void rollbackWithCallingTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String before = transaction.execute(status -> em.find(CustomerOrder.class, 13L).getStatusCode().getCode());
        long count = transaction.execute(status -> statusHistoryRepository.count());
        var historyIds = transaction.execute(status -> statusHistoryRepository
                .findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(HistoryEntityType.ORDER, 13L)
                .stream().map(history -> history.getId()).toList());

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            progressService.changeProgress(em.find(CustomerOrder.class, 13L), "IN_PRODUCTION",
                    em.find(User.class, 1L), "롤백 검증");
            em.flush();
            throw new IllegalStateException("후속 작업 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("후속 작업 실패");

        transaction.executeWithoutResult(status -> {
            assertThat(em.find(CustomerOrder.class, 13L).getStatusCode().getCode()).isEqualTo(before);
            assertThat(statusHistoryRepository.count()).isEqualTo(count);
            assertThat(statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                    HistoryEntityType.ORDER, 13L)).extracting(history -> history.getId())
                    .containsExactlyElementsOf(historyIds);
        });
    }

    @Test
    @DisplayName("전체 수주의 시드 이력은 처리자와 사유가 있다")
    void seedHistoriesMatchCurrentOrders() {
        var orders = em.createQuery("select o from CustomerOrder o order by o.id", CustomerOrder.class).getResultList();
        assertThat(orders).isNotEmpty();
        for (CustomerOrder order : orders) {
            var histories = statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                    HistoryEntityType.ORDER, order.getId());
            assertThat(histories).as("수주 %s의 이력", order.getOrderNo()).isNotEmpty()
                    .allSatisfy(history -> {
                        assertThat(history.getUserName()).isEqualTo(history.getUser().getName());
                        assertThat(history.getNote()).isNotBlank();
                        assertThat(history.getFromStatus()).isNotBlank();
                        assertThat(history.getToStatus()).isNotBlank();
                    });
        }
    }
}
