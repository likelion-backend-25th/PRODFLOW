package net.likelion.bebc25.prodflow.customer_order;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderListDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerOrderQueryTest {
    @Autowired CustomerOrderService service;
    @Autowired EntityManager em;

    // 탭·페이지·전체 건수 검증
    @Test
    void pagesOpenAndDoneWithoutCancelledOrders() {
        CustomerOrderListDto.SearchCondition open = condition(OrderListTab.OPEN, null, null);
        assertThat(service.getOrders(open, PageRequest.of(0, 8)).getTotalElements()).isEqualTo(9);
        assertThat(service.getOrders(open, PageRequest.of(0, 8)).getContent()).hasSize(8)
                .allSatisfy(row -> assertThat(row.status()).isNotIn("COMPLETED", "CANCELLED"));
        assertThat(service.getOrders(open, PageRequest.of(1, 8)).getContent()).hasSize(1);
        assertThat(service.getOrders(open, PageRequest.of(99, 8)).getTotalElements()).isEqualTo(9);
        assertThat(service.getOrders(open, PageRequest.of(99, 8)).getContent()).isEmpty();
        assertThat(service.getOrders(condition(OrderListTab.DONE, null, null), PageRequest.of(0, 8)).getContent())
                .hasSize(2).allSatisfy(row -> assertThat(row.status()).isEqualTo("COMPLETED"));
    }

    // 그룹 ID 2의 진행 상태 검색 검증
    @Test
    void usesCommonCodeIdsAndNamesIncludingNewCodes() {
        List<CustomerOrderListDto.StatusResponse> statuses = service.getStatuses();
        assertThat(statuses).extracting(CustomerOrderListDto.StatusResponse::id)
                .containsExactly(6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, 7L), PageRequest.of(0, 8)).getContent())
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.statusCodeId()).isEqualTo(7L);
                    assertThat(row.status()).isEqualTo("CONFIRMED");
                    assertThat(row.statusName()).isEqualTo("생산대기");
                });
        CustomerOrder order = em.find(CustomerOrder.class, 1L);
        order.changeProgress(em.find(CommonCode.class, 14L));
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, 14L), PageRequest.of(0, 8)).getContent())
                .singleElement().satisfies(row -> assertThat(row.status()).isEqualTo("PAUSE"));
        assertThatThrownBy(() -> service.getOrders(condition(OrderListTab.OPEN, null, 1L), PageRequest.of(0, 8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("그룹 ID 2");
        assertThatThrownBy(() -> service.getOrders(condition(OrderListTab.OPEN, null, Long.MAX_VALUE), PageRequest.of(0, 8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 동적 상태 코드·비활성 코드 검증
    @Test
    void readsUnknownCodesFromDatabaseAndExcludesOtherGroups() {
        CommonCode code = CommonCode.create(em.find(CodeGroup.class, 2L), "CUSTOM", "추가 진행 상태", 11);
        em.persist(code);
        em.find(CustomerOrder.class, 1L).changeProgress(code);
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, code.getId()), PageRequest.of(0, 8)).getContent())
                .singleElement().satisfies(row -> assertThat(row.statusName()).isEqualTo("추가 진행 상태"));
        ReflectionTestUtils.setField(code, "status", CodeStatus.INACTIVE);
        assertThat(service.getStatuses()).extracting(CustomerOrderListDto.StatusResponse::id).doesNotContain(code.getId());
        em.find(CustomerOrder.class, 1L).changeProgress(em.find(CommonCode.class, 1L));
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, null), PageRequest.of(0, 20)).getTotalElements()).isEqualTo(8);
    }

    // 검색·필터 조합과 품목 중복 방지 검증
    @Test
    void combinesFiltersAndSearchesSnapshotNamesWithoutDuplicateOrders() {
        assertThat(service.getOrders(condition(OrderListTab.OPEN, " 케이스 ", null), PageRequest.of(0, 20)).getTotalElements()).isEqualTo(9);
        assertThat(service.getOrders(condition(OrderListTab.OPEN, "PF-2026-001", null), PageRequest.of(0, 8)).getContent())
                .singleElement().satisfies(row -> {
                    assertThat(row.itemCount()).isEqualTo(3);
                    assertThat(row.totalQuantity()).isEqualTo(6000);
                    assertThat(row.totalAmount()).isEqualTo(em.find(CustomerOrder.class, 1L).getTotalAmount());
                });
        CustomerOrderListDto.SearchCondition filtered = new CustomerOrderListDto.SearchCondition(OrderListTab.OPEN,
                "한빛", ConfirmationStatus.PENDING, 6L, OrderPaymentStatus.UNPAID, OrderListSortType.CREATED_AT);
        assertThat(service.getOrders(filtered, PageRequest.of(0, 8)).getContent())
                .extracting(CustomerOrderListDto.Response::id).containsExactly(9L);
        for (String literal : List.of("%", "_", "!", "없는 검색어")) {
            assertThat(service.getOrders(condition(OrderListTab.OPEN, literal, null), PageRequest.of(0, 8)).getContent()).isEmpty();
        }
    }

    // 배송 완료일 정렬·월 집계 검증
    @Test
    void sortsByDeliveryCompletionAndUsesKoreanCalendarMonth() {
        LocalDate month = LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);
        em.createQuery("update Shipment s set s.deliveredAt = :date where s.orderId = 7")
                .setParameter("date", month.atStartOfDay()).executeUpdate();
        em.createQuery("update Shipment s set s.deliveredAt = :date where s.orderId = 8")
                .setParameter("date", month.plusMonths(1).atStartOfDay()).executeUpdate();
        em.clear();
        CustomerOrderListDto.SearchCondition done = new CustomerOrderListDto.SearchCondition(OrderListTab.DONE,
                null, null, null, null, OrderListSortType.COMPLETED_AT);
        assertThat(service.getOrders(done, PageRequest.of(0, 3)).getContent())
                .extracting(CustomerOrderListDto.Response::id).containsExactly(8L, 7L);
        CustomerOrderListDto.SummaryResponse summary = service.getSummary();
        assertThat(summary.waiting()).isEqualTo(2);
        assertThat(summary.doneThisMonth()).isEqualTo(1);
        assertThat(summary.openCount()).isEqualTo(9);
        assertThat(summary.doneCount()).isEqualTo(2);
        em.createQuery("update Shipment s set s.deliveredAt = null where s.orderId = 8").executeUpdate();
        em.clear();
        assertThat(service.getOrders(done, PageRequest.of(0, 3)).getContent())
                .extracting(CustomerOrderListDto.Response::id).containsExactly(7L, 8L);
        assertThat(service.getOrders(done, PageRequest.of(0, 3)).getContent().get(1).completedAt()).isNull();
    }

    // 전체 검색 결과 CSV 검증
    @Test
    void exportsAllMatchingRowsAndEscapesSpreadsheetCells() {
        String csv = new String(service.export(condition(OrderListTab.OPEN, null, null)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv.lines().count()).isEqualTo(10);
        em.createQuery("update OrderProduct p set p.productName = :name where p.id = 1")
                .setParameter("name", "=SUM(1,2)\"테스트").executeUpdate();
        em.clear();
        String filtered = new String(service.export(condition(OrderListTab.OPEN, "=SUM", null)), StandardCharsets.UTF_8);
        assertThat(filtered.lines().count()).isEqualTo(2);
        assertThat(filtered).contains("\"'=SUM(1,2)\"\"테스트");
    }

    // 기본 검색 조건 생성
    private CustomerOrderListDto.SearchCondition condition(OrderListTab tab, String keyword, Long statusCodeId) {
        return new CustomerOrderListDto.SearchCondition(tab, keyword, null, statusCodeId, null, OrderListSortType.CREATED_AT);
    }
}
