package net.likelion.bebc25.prodflow.quotation.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class QuotationRepositoryTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private QuotationRepository quotationRepository;

    private Long quotationId;

    // 검색할 견적 직접 준비
    @BeforeEach
    void setUp() {
        User user = User.builder().name("견적 작성자").email("quotation-repository@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        em.persist(user);
        Client client = new Client("견적 검색 테스트 회사", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);

        LocalDate dueDate = LocalDate.now().plusDays(30);
        Quotation quotation = new Quotation("QT-REPOSITORY-TEST", client, client.getEmployee(), user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(14),
                false, QuotationStatus.DRAFT, 220000L);
        Quotation savedQuotation = quotationRepository.save(quotation);
        quotationId = savedQuotation.getId();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("저장한 견적 상세 조회")
    void findDetailById() {
        Quotation quotation = quotationRepository.findDetailById(quotationId).orElseThrow();

        assertThat(quotation.getQuotationNo()).isEqualTo("QT-REPOSITORY-TEST");
        assertThat(quotation.getClient().getCompany()).isEqualTo("견적 검색 테스트 회사");
        assertThat(quotation.getUser().getName()).isEqualTo("견적 작성자");
    }

    @Test
    @DisplayName("견적번호와 초안 상태로 검색")
    void searchDraft() {
        Page<Quotation> result = quotationRepository.search("QT-REPOSITORY-TEST", QuotationStatus.DRAFT,
                false, PageRequest.of(0, 8));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(quotationId);
    }

    @Test
    @DisplayName("견적 목록 페이지네이션")
    void pagination() {
        Page<Quotation> first = quotationRepository.search("QT-REPOSITORY-TEST", null, false, PageRequest.of(0, 1));
        Page<Quotation> next = quotationRepository.search("QT-REPOSITORY-TEST", null, false, PageRequest.of(1, 1));

        assertThat(first.getContent()).hasSize(1);
        assertThat(first.getTotalPages()).isEqualTo(1);
        assertThat(next.getContent()).isEmpty();
        assertThat(next.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("초안은 수주 대기 조회에서 제외")
    void excludeDraftFromOrderPending() {
        Page<Quotation> result = quotationRepository.search("QT-REPOSITORY-TEST", null, true, PageRequest.of(0, 8));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("승인 후 수주가 없는 견적만 수주 대기로 조회")
    void searchOrderPending() {
        Quotation quotation = em.find(Quotation.class, quotationId);
        quotation.sent();
        quotation.approve();
        em.flush();
        em.clear();

        Page<Quotation> before = quotationRepository.search("QT-REPOSITORY-TEST", QuotationStatus.APPROVED,
                true, PageRequest.of(0, 8));
        assertThat(before.getContent()).hasSize(1);

        Quotation findQuotation = em.find(Quotation.class, quotationId);
        Client client = findQuotation.getClient();
        CommonCode waiting = em.find(CommonCode.class, 6L);
        CustomerOrder order = new CustomerOrder("PF-QUOTATION-REPOSITORY-TEST", findQuotation, client,
                client.getEmployee(), client.getEmail(), client.getPhone(), findQuotation.getClientAddress(),
                findQuotation.getUser(), findQuotation.getRequestedDueDate(), findQuotation.getEstimatedDueDate(),
                findQuotation.getPlannedStartDate(), findQuotation.getPlannedEndDate(), LocalDate.now().plusDays(3),
                "서울시 배송로 2", "홍수신", "010-3333-4444", NotifyChannel.EMAIL, null, waiting, false, 220000L);
        em.persist(order);
        em.flush();
        em.clear();

        Page<Quotation> after = quotationRepository.search("QT-REPOSITORY-TEST", QuotationStatus.APPROVED,
                true, PageRequest.of(0, 8));
        assertThat(after.getContent()).isEmpty();
    }
}
