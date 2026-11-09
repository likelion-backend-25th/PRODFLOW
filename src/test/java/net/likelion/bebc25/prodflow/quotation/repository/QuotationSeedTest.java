package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationScheduleRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;

import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.hibernate.Hibernate;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class QuotationSeedTest {
    @Autowired QuotationRepository quotations;
    @Autowired QuotationScheduleRepository schedules;
    @Autowired QuotationItemRepository items;
    @Autowired CustomerOrderRepository orders;
    @Autowired EntityManager em;
    @Autowired QuotationService service;

    @Test
    void orderPendingFiltersBeforePaginationAndKeepsSearchAndSort() {
        em.createQuery("update Quotation q set q.status = :status")
                .setParameter("status", QuotationStatus.APPROVED).executeUpdate();
        em.createQuery("update Quotation q set q.status = :status where q.id = 16")
                .setParameter("status", QuotationStatus.SENT).executeUpdate();
        em.clear();

        for (QuotationSortType sortType : QuotationSortType.values()) {
            var expected = quotations.findAll(Sort.by("id")).stream()
                    .filter(q -> q.getStatus() == QuotationStatus.APPROVED)
                    .filter(q -> orders.findByQuotationIdIn(List.of(q.getId())).isEmpty())
                    .map(q -> q.getId()).toList();
            var full = service.getQuotationList(null, null, sortType, PageRequest.of(0, 100), true);
            assertThat(full.getContent()).extracting(q -> q.id()).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(full.getContent()).allSatisfy(q -> {
                assertThat(q.status()).isEqualTo(QuotationStatus.APPROVED);
                assertThat(q.customerOrderId()).isNull();
            });
            assertThat(expected.size()).isGreaterThan(1);
            var page = service.getQuotationList(null, null, sortType, PageRequest.of(1, 1), true);
            assertThat(page.getTotalElements()).isEqualTo(expected.size());
            assertThat(page.getTotalPages()).isEqualTo(expected.size());
            assertThat(page.getContent()).isEqualTo(full.getContent().subList(1, 2));
            var empty = service.getQuotationList(null, null, sortType, PageRequest.of(99, 2), true);
            assertThat(empty.getContent()).isEmpty();
            assertThat(empty.getTotalElements()).isEqualTo(expected.size());
            var search = service.getQuotationList(" 한빛 ", null, sortType, PageRequest.of(0, 100), true);
            assertThat(search.getContent()).isEqualTo(full.getContent().stream()
                    .filter(q -> q.clientCompany().contains("한빛")).toList());
        }
        assertThat(service.getQuotationList(null, QuotationStatus.SENT,
                QuotationSortType.CREATED_AT, PageRequest.of(0, 12), true).getContent()).isEmpty();
        assertThat(service.getQuotationList(null, null,
                QuotationSortType.CREATED_AT, PageRequest.of(0, 12), false).getTotalElements()).isEqualTo(16);
    }

    @Test
    void combinesStatusWithEveryDateSortKeywordAndPagination() {
        em.createQuery("update Quotation q set q.status = :status")
                .setParameter("status", QuotationStatus.APPROVED).executeUpdate();
        Map<QuotationStatus, Long> singleStatusIds = Map.of(
                QuotationStatus.SENT, 1L, QuotationStatus.CANCELLED, 2L,
                QuotationStatus.EXPIRED, 3L, QuotationStatus.REJECTED, 4L);
        singleStatusIds.forEach((quotationStatus, id) ->
                em.createQuery("update Quotation q set q.status = :status where q.id = :id")
                        .setParameter("status", quotationStatus).setParameter("id", id).executeUpdate());
        em.clear();

        for (var entry : singleStatusIds.entrySet()) {
            for (QuotationSortType sortType : QuotationSortType.values()) {
                var response = service.getQuotationList(null, entry.getKey(), sortType, PageRequest.of(0, 12));
                assertThat(response.getTotalElements()).isEqualTo(1);
                assertThat(response.getContent()).singleElement().satisfies(quotation -> {
                    assertThat(quotation.id()).isEqualTo(entry.getValue());
                    assertThat(quotation.status()).isEqualTo(entry.getKey());
                });
            }
        }

        for (QuotationSortType sortType : QuotationSortType.values()) {
            var approved = service.getQuotationList(" ", QuotationStatus.APPROVED, sortType, PageRequest.of(0, 5));
            assertThat(approved.getTotalElements()).isEqualTo(12);
            assertThat(approved.getTotalPages()).isEqualTo(3);
            assertThat(approved.getContent()).hasSize(5)
                    .allSatisfy(quotation -> assertThat(quotation.status()).isEqualTo(QuotationStatus.APPROVED));
        }

        var search = service.getQuotationList(" 한빛 ", QuotationStatus.APPROVED,
                QuotationSortType.CREATED_AT, PageRequest.of(0, 12));
        assertThat(search.getContent()).extracting(quotation -> quotation.id())
                .containsExactlyInAnyOrder(5L, 9L, 15L);
        assertThat(search.getTotalElements()).isEqualTo(3);

        var noMatch = service.getQuotationList("QT-20261105-1", QuotationStatus.APPROVED,
                QuotationSortType.CREATED_AT, PageRequest.of(0, 12));
        assertThat(noMatch.getContent()).isEmpty();
        assertThat(noMatch.getTotalElements()).isZero();
        var outOfRange = service.getQuotationList(null, QuotationStatus.APPROVED,
                QuotationSortType.CREATED_AT, PageRequest.of(99, 5));
        assertThat(outOfRange.getContent()).isEmpty();
        assertThat(outOfRange.getTotalElements()).isEqualTo(12);
    }

    @Test
    void sortsDatesWithFixedDirectionsAndIdTieBreakAcrossSearchAndPages() {
        LocalDate base = LocalDate.of(2026, 1, 10);
        em.createQuery("""
                update Quotation q set q.createdAt = :createdAt, q.requestedDueDate = :date,
                q.estimatedDueDate = :date, q.validUntil = :date
                """).setParameter("createdAt", base.atStartOfDay()).setParameter("date", base).executeUpdate();
        em.createQuery("update Quotation q set q.createdAt = :date where q.id = 1")
                .setParameter("date", base.plusDays(2).atStartOfDay()).executeUpdate();
        em.createQuery("update Quotation q set q.createdAt = :date where q.id = 2")
                .setParameter("date", base.plusDays(1).atStartOfDay()).executeUpdate();
        em.createQuery("update Quotation q set q.requestedDueDate = :date where q.id = 2")
                .setParameter("date", base.plusDays(2)).executeUpdate();
        em.createQuery("update Quotation q set q.requestedDueDate = :date where q.id = 3")
                .setParameter("date", base.plusDays(1)).executeUpdate();
        em.createQuery("update Quotation q set q.estimatedDueDate = :date where q.id = 3")
                .setParameter("date", base.plusDays(2)).executeUpdate();
        em.createQuery("update Quotation q set q.estimatedDueDate = :date where q.id = 1")
                .setParameter("date", base.plusDays(1)).executeUpdate();
        em.createQuery("update Quotation q set q.validUntil = :date where q.id = 1")
                .setParameter("date", base.minusDays(2)).executeUpdate();
        em.createQuery("update Quotation q set q.validUntil = :date where q.id = 2")
                .setParameter("date", base.minusDays(1)).executeUpdate();
        em.clear();

        // 외부 sort 입력보다 Enum의 정렬 우선
        var pageable = PageRequest.of(0, 3, Sort.by("id"));
        assertThat(service.getQuotationList(null, null, QuotationSortType.CREATED_AT, pageable).getContent())
                .extracting(quotation -> quotation.id()).containsExactly(1L, 2L, 16L);
        assertThat(service.getQuotationList(null, null, QuotationSortType.REQUESTED_DUE_DATE, pageable).getContent())
                .extracting(quotation -> quotation.id()).containsExactly(2L, 3L, 16L);
        assertThat(service.getQuotationList(null, null, QuotationSortType.ESTIMATED_DUE_DATE, pageable).getContent())
                .extracting(quotation -> quotation.id()).containsExactly(3L, 1L, 16L);
        assertThat(service.getQuotationList(null, null, QuotationSortType.VALID_UNTIL, pageable).getContent())
                .extracting(quotation -> quotation.id()).containsExactly(16L, 15L, 14L);
        assertThat(service.getQuotationList("QT-", null, QuotationSortType.REQUESTED_DUE_DATE, pageable).getContent())
                .extracting(quotation -> quotation.id()).containsExactly(2L, 3L, 16L);
        var nextPage = service.getQuotationList(null, null, QuotationSortType.VALID_UNTIL, PageRequest.of(1, 3));
        assertThat(nextPage.getContent()).extracting(quotation -> quotation.id())
                .containsExactly(13L, 12L, 11L);
        assertThat(nextPage.getTotalElements()).isEqualTo(16);
    }

    @Test
    void listReturnsPageResponseWithAmountsAndLinkedOrder() {
        var response = service.getQuotationList(
                " QT-20261105-1 ", null, QuotationSortType.CREATED_AT, PageRequest.of(0, 12));
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(12);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        long total = quotations.findById(1L).orElseThrow().getTotalAmount();
        assertThat(response.getContent()).singleElement().satisfies(quotation -> {
            assertThat(quotation.quotationNo()).isEqualTo("QT-20261105-1");
            assertThat(quotation.totalAmount()).isEqualTo(total);
            assertThat(quotation.customerOrderId()).isEqualTo(1L);
            assertThat(quotation.orderNo()).isEqualTo("PF-20261106-1");
        });
    }

    @Test
    void emptyListKeepsTotalCountForOutOfRangePage() {
        var response = service.getQuotationList(null, null, QuotationSortType.CREATED_AT, PageRequest.of(99, 12));
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isEqualTo(99);
        assertThat(response.getTotalElements()).isEqualTo(16);
        assertThat(response.getTotalPages()).isEqualTo(2);

        var noMatch = service.getQuotationList("없는견적검색어", null, QuotationSortType.CREATED_AT, PageRequest.of(0, 12));
        assertThat(noMatch.getContent()).isEmpty();
        assertThat(noMatch.getTotalElements()).isZero();
        assertThat(noMatch.getTotalPages()).isZero();
    }

    @Test
    void pagesQuotationsWithClientAndUserAndConvertsPageMetadata() {
        em.clear();
        var page = quotations.findAll(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "id")));
        assertThat(page.getContent()).extracting(quotation -> quotation.getId())
                .containsExactly(11L, 10L, 9L, 8L, 7L);
        assertThat(page.getContent()).allSatisfy(quotation -> {
            assertThat(Hibernate.isInitialized(quotation.getClient())).isTrue();
            assertThat(Hibernate.isInitialized(quotation.getUser())).isTrue();
        });
        var response = PageResponse.from(page.map(quotation -> quotation.getQuotationNo()));
        assertThat(response.getContent()).hasSize(5);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(16);
        assertThat(response.getTotalPages()).isEqualTo(4);
        var empty = PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(empty.getContent()).isEmpty();
        assertThat(empty.getTotalPages()).isZero();
    }

    @Test
    void findsOnlyRequestedQuotationsLinkedOrders() {
        assertThat(orders.findByQuotationIdIn(List.of(1L, 2L)))
                .extracting(order -> order.getOrderNo())
                .containsExactlyInAnyOrder("PF-20261106-1", "PF-20261030-2");
        assertThat(orders.findByQuotationIdIn(List.of(Long.MAX_VALUE))).isEmpty();
    }

    @Test
    void seedLoadsWithContactSnapshotsAndExistingProductionReadyOrdersAreIncluded() {
        assertThat(quotations.count()).isEqualTo(16);
        assertThat(quotations.findAll()).allSatisfy(quotation -> {
            long expectedTotal = items.findByQuotationIdOrderByIdAsc(quotation.getId()).stream()
                    .mapToLong(item -> item.getLineAmount()).sum();
            assertThat(quotation.getTotalAmount()).isEqualTo(expectedTotal);
            assertThat(quotation.getClientName()).isNotBlank();
            assertThat(quotation.getClientAddress()).isNotBlank();
            assertThat(quotation.getClientEmail()).isNotBlank();
            assertThat(quotation.getClientPhone()).isNotBlank();
        });
        var orders = schedules.findActiveOrders();
        assertThat(orders).anyMatch(order -> "CONFIRMED".equals(order.getStatusCode().getCode()));
        var ids = orders.stream().map(order -> order.getId()).toList();
        assertThat(schedules.findOrderProducts(ids)).isNotEmpty();
        assertThat(schedules.findFixedPlans(ids)).isNotEmpty();
    }
}
