package net.likelion.bebc25.prodflow.customer_order.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderListDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerOrderQueryTest {
    @Autowired CustomerOrderService service;
    @Autowired EntityManager em;

    // 탭·페이지·전체 건수 검증
    @Test
    void pagesOpenOrdersAndIncludesCancelledOrdersInDone() {
        CustomerOrderListDto.SearchCondition open = condition(OrderListTab.OPEN, null, null);
        var first = service.getOrders(open, PageRequest.of(0, 8));
        long openTotal = first.getTotalElements();
        assertThat(openTotal).isGreaterThan(8);
        assertThat(first.getContent()).hasSize(8)
                .allSatisfy(row -> assertThat(row.status()).isNotIn("COMPLETED", "CANCELLED"));
        assertThat(service.getOrders(open, PageRequest.of(1, 8)).getContent())
                .hasSize((int) Math.min(8, openTotal - 8));
        assertThat(service.getOrders(open, PageRequest.of(99, 8)).getTotalElements()).isEqualTo(openTotal);
        assertThat(service.getOrders(open, PageRequest.of(99, 8)).getContent()).isEmpty();
        var done = service.getOrders(condition(OrderListTab.DONE, null, null), PageRequest.of(0, 8));
        assertThat(done.getContent()).isNotEmpty()
                .allSatisfy(row -> assertThat(row.status()).isIn("COMPLETED", "CANCELLED"));
        assertThat(service.getOrders(condition(OrderListTab.DONE, null, null), PageRequest.of(0, 8))
                .getTotalElements()).isEqualTo(service.getSummary().doneCount());
    }

    // 그룹 ID 2의 진행 상태 검색 검증
    @Test
    void usesCommonCodeIdsAndNamesIncludingNewCodes() {
        List<CustomerOrderListDto.StatusResponse> statuses = service.getStatuses();
        assertThat(statuses).extracting(CustomerOrderListDto.StatusResponse::id)
                .containsExactly(6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        var confirmed = service.getOrders(condition(OrderListTab.OPEN, null, 7L), PageRequest.of(0, 8));
        assertThat(confirmed.getTotalElements()).isEqualTo(3);
        assertThat(confirmed.getContent()).hasSize(3).allSatisfy(row -> {
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
        assertThatThrownBy(() -> service.getOrders(condition(OrderListTab.OPEN, null, Long.MAX_VALUE), PageRequest
                .of(0, 8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 동적 상태 코드·비활성 코드 검증
    @Test
    void readsUnknownCodesFromDatabaseAndExcludesOtherGroups() {
        long openBefore = service.getOrders(condition(OrderListTab.OPEN, null, null), PageRequest.of(0, 1))
                .getTotalElements();
        CommonCode code = CommonCode.create(em.find(CodeGroup.class, 2L), "CUSTOM", "추가 진행 상태", 11);
        em.persist(code);
        em.find(CustomerOrder.class, 1L).changeProgress(code);
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, code.getId()), PageRequest.of(0, 8))
                .getContent())
                .singleElement().satisfies(row -> assertThat(row.statusName()).isEqualTo("추가 진행 상태"));
        ReflectionTestUtils.setField(code, "status", CodeStatus.INACTIVE);
        assertThat(service.getStatuses()).extracting(CustomerOrderListDto.StatusResponse::id).doesNotContain(code
                .getId());
        em.find(CustomerOrder.class, 1L).changeProgress(em.find(CommonCode.class, 1L));
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, null), PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(openBefore - 1);
    }

    // 검색·필터 조합과 품목 중복 방지 검증
    @Test
    void combinesFiltersAndSearchesSnapshotNamesWithoutDuplicateOrders() {
        assertThat(service.getOrders(condition(OrderListTab.OPEN, " 케이스 ", null), PageRequest.of(0, 20))
                .getTotalElements()).isPositive();
        var byOrderNo = service.getOrders(
                condition(OrderListTab.OPEN, "PF-20261110-1", null), PageRequest.of(0, 8)).getContent();
        assertThat(byOrderNo).extracting(CustomerOrderListDto.Response::id).doesNotHaveDuplicates().contains(1L);
        assertThat(byOrderNo).filteredOn(row -> row.id().equals(1L)).singleElement().satisfies(row -> {
            assertThat(row.itemCount()).isEqualTo(1);
            assertThat(row.totalQuantity()).isEqualTo(1300);
            assertThat(row.totalAmount()).isEqualTo(em.find(CustomerOrder.class, 1L).getTotalAmount());
        });
        CustomerOrderListDto.SearchCondition filtered = new CustomerOrderListDto.SearchCondition(OrderListTab.OPEN,
                "한빛", ConfirmationStatus.PENDING, 6L, OrderPaymentStatus.UNPAID, OrderListSortType.CREATED_AT);
        assertThat(service.getOrders(filtered, PageRequest.of(0, 8)).getContent())
                .extracting(CustomerOrderListDto.Response::id).contains(9L);
        for (String literal : List.of("%", "_", "!", "없는 검색어")) {
            assertThat(service.getOrders(condition(OrderListTab.OPEN, literal, null), PageRequest.of(0, 8))
                    .getContent()).isEmpty();
        }
    }

    // 배송 완료일 정렬·월 집계 검증
    @Test
    void sortsByDeliveryCompletionAndUsesKoreanCalendarMonth() {
        LocalDate month = LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);
        em.createQuery("update Shipment s set s.deliveredAt = :date")
                .setParameter("date", month.minusMonths(2).atStartOfDay()).executeUpdate();
        em.createQuery("update Shipment s set s.deliveredAt = :date where s.orderId = 5")
                .setParameter("date", month.minusDays(1).atStartOfDay()).executeUpdate();
        em.createQuery("update Shipment s set s.deliveredAt = :date where s.orderId = 7")
                .setParameter("date", month.atStartOfDay()).executeUpdate();
        em.createQuery("update Shipment s set s.deliveredAt = :date where s.orderId = 8")
                .setParameter("date", month.plusMonths(1).atStartOfDay()).executeUpdate();
        em.clear();
        CustomerOrderListDto.SearchCondition done = new CustomerOrderListDto.SearchCondition(OrderListTab.DONE,
                null, ConfirmationStatus.CONFIRMED, null, null, OrderListSortType.COMPLETED_AT);
        assertThat(service.getOrders(done, PageRequest.of(0, 3)).getContent())
                .extracting(CustomerOrderListDto.Response::id).startsWith(8L, 7L, 5L);
        CustomerOrderListDto.SummaryResponse summary = service.getSummary();
        assertThat(summary.waiting()).isPositive();
        assertThat(summary.doneThisMonth()).isEqualTo(1);
        assertThat(summary.openCount()).isEqualTo(
                service.getOrders(condition(OrderListTab.OPEN, null, null), PageRequest.of(0, 1)).getTotalElements());
        assertThat(summary.doneCount()).isEqualTo(
                service.getOrders(condition(OrderListTab.DONE, null, null), PageRequest.of(0, 1)).getTotalElements());
        em.createQuery("update Shipment s set s.deliveredAt = null where s.orderId = 8").executeUpdate();
        em.clear();
        var afterNull = service.getOrders(done, PageRequest.of(0, 200)).getContent();
        assertThat(afterNull).extracting(CustomerOrderListDto.Response::id).startsWith(7L, 5L);
        assertThat(afterNull).filteredOn(row -> row.id().equals(8L)).singleElement()
                .satisfies(row -> assertThat(row.completedAt()).isNull());
    }

    // 전체 검색 결과 CSV 검증
    @Test
    void filtersCompletedAndCancelledOrdersAndExportsCancelledRows() {
        var cancelled = service.getOrders(condition(OrderListTab.DONE, null, 15L), PageRequest.of(0, 8));
        assertThat(cancelled.getContent()).isNotEmpty().allSatisfy(row -> {
            assertThat(row.status()).isEqualTo("CANCELLED");
            assertThat(row.confirmationStatus()).isEqualTo(ConfirmationStatus.CANCELLED);
        });
        assertThat(cancelled.getContent()).extracting(CustomerOrderListDto.Response::id).contains(11L);
        assertThat(service.getOrders(condition(OrderListTab.OPEN, null, 15L), PageRequest.of(0, 8)).getContent())
                .isEmpty();
        assertThat(service.getOrders(condition(OrderListTab.DONE, null, 12L), PageRequest.of(0, 8)).getContent())
                .isNotEmpty().allSatisfy(row -> assertThat(row.status()).isEqualTo("COMPLETED"));
        CustomerOrderListDto.SearchCondition completedOnly = new CustomerOrderListDto.SearchCondition(OrderListTab.DONE,
                null, ConfirmationStatus.CONFIRMED, null, null, OrderListSortType.COMPLETED_AT);
        assertThat(service.getOrders(completedOnly, PageRequest.of(0, 3)).getContent())
                .isNotEmpty().allSatisfy(row -> assertThat(row.status()).isEqualTo("COMPLETED"));
        String csv = new String(service.export(condition(OrderListTab.DONE, null, 15L)), StandardCharsets.UTF_8);
        assertThat(csv.lines().count()).isEqualTo(cancelled.getTotalElements() + 1);
        assertThat(csv).contains("PF-20261105-11");
    }

    @Test
    void exportsAllMatchingRowsAndEscapesSpreadsheetCells() {
        var open = service.getOrders(condition(OrderListTab.OPEN, null, null), PageRequest.of(0, 1));
        String csv = new String(service.export(condition(OrderListTab.OPEN, null, null)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv.lines().count()).isEqualTo(open.getTotalElements() + 1);
        em.createQuery("update OrderProduct p set p.productName = :name where p.id = 1")
                .setParameter("name", "=SUM(1,2)\"테스트").executeUpdate();
        em.clear();
        String filtered = new String(service.export(condition(OrderListTab.OPEN, "=SUM", null)), StandardCharsets
                .UTF_8);
        assertThat(filtered.lines().count()).isEqualTo(2);
        assertThat(filtered).contains("\"'=SUM(1,2)\"\"테스트");
    }

    // 기본 검색 조건 생성
    private CustomerOrderListDto.SearchCondition condition(OrderListTab tab, String keyword, Long statusCodeId) {
        return new CustomerOrderListDto.SearchCondition(tab, keyword, null, statusCodeId, null, OrderListSortType
                .CREATED_AT);
    }
}
