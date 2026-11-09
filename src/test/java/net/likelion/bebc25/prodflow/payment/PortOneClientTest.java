package net.likelion.bebc25.prodflow.payment;

import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.payment.client.PortOneClient;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class PortOneClientTest {
    @Autowired
    private PortOneClient portOneClient;

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    @Disabled("실제 PortOne API 호출 테스트")
    @Test
    @DisplayName("수주와 거래처 정보로 신한은행 가상계좌를 발급한다")
    void issueInstantPayment() {
        // given
        PortOneDto.IssueRequest request = createIssueRequest();

        // when: PortOne 가상계좌 발급 요청
        portOneClient.issueInstantPayment(request);

        // 발급된 결제 정보 재조회
        PortOneDto.PaymentSnapshot snapshot =
                portOneClient.getPayment(request.paymentId());

        // then
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.id()).isEqualTo(request.paymentId());

        // 가상계좌 정보 확인
        assertThat(snapshot.method()).isNotNull();
        assertThat(snapshot.method().bank()).isNotBlank();
        assertThat(snapshot.method().accountNumber()).isNotBlank();

        // 발급 결과 출력
        System.out.println("결제 ID: " + snapshot.id());
        System.out.println("은행: " + snapshot.method().bank());
        System.out.println("계좌번호: " + snapshot.method().accountNumber());
    }

    @Disabled
    @Test
    @DisplayName("가상계좌 발급에 사용한 paymentId로 결제 정보를 조회한다")
    void getPayment() {
        // given: 단독 실행 시에도 조회할 결제가 존재하도록 먼저 발급한다.
        PortOneDto.IssueRequest request = createIssueRequest();
        portOneClient.issueInstantPayment(request);

        // when: 발급 요청의 paymentId를 그대로 사용한다.
        PortOneDto.PaymentSnapshot response = portOneClient.getPayment(request.paymentId());

        System.out.println(response);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(request.paymentId());
        assertThat(response.status()).isEqualTo("VIRTUAL_ACCOUNT_ISSUED");
        assertThat(response.amount()).isNotNull();
        assertThat(response.amount().total()).isEqualTo(request.amount());
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.method()).isNotNull();
        assertThat(response.method().type()).isEqualTo("PaymentMethodVirtualAccount");
        assertThat(response.method().bank()).isEqualTo(request.bank());
        assertThat(response.method().accountNumber()).isNotBlank();
        assertThat(response.method().expiredAt()).isNotNull();
    }

    private PortOneDto.IssueRequest createIssueRequest() {
        // given: data.sql의 미입금 수주(PF-2026-009)와 연결된 거래처를 조회한다.
        CustomerOrder order = customerOrderRepository.findById(9L)
                .orElseThrow(() -> new IllegalStateException("테스트용 수주(id=9)가 필요합니다."));
        Client client = order.getClient();

        // paymentId는 영문/숫자로 생성하고, 멱등키는 36자의 ASCII UUID를 사용한다.
        String paymentId = "payment" + UUID.randomUUID().toString().replace("-", "");
        String idempotencyKey = UUID.randomUUID().toString();
        // 시드의 입금기한은 과거일 수 있으므로 발급 테스트에서는 미래 시각을 사용한다.
        OffsetDateTime dueDate = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
                .plusDays(3).withNano(0);
        return new PortOneDto.IssueRequest(
                paymentId,
                order.getOrderNo(),
                order.getTotalAmount(),
                client.getCompany(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress().getRoadAddress(),
                client.getAddress().getDetailAddress(),
                "SHINHAN",
                dueDate,
                idempotencyKey
        );

    }
}
