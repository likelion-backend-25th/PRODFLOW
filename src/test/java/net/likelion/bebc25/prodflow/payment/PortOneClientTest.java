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

    @Disabled
    @Test
    @DisplayName("수주와 거래처 정보로 신한은행 가상계좌를 발급한다")
    void issueInstantPayment() {
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
        PortOneDto.IssueRequest request = new PortOneDto.IssueRequest(
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

        // when: 실제 PortOne API를 호출한다. 외부 발급 결과는 @Transactional로 롤백되지 않는다.
        PortOneDto.IssueResponse response = portOneClient.issueInstantPayment(request);

        System.out.println(response.pgTxId());

        // then: 가상계좌 발급은 입금 전이므로 paidAt은 검증하지 않는다.
        assertThat(response).isNotNull();
        assertThat(response.pgTxId()).isNotBlank();
    }
}
