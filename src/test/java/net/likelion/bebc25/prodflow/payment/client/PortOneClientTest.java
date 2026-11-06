package net.likelion.bebc25.prodflow.payment.client;

import net.likelion.bebc25.prodflow.payment.client.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.properties.PortOneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOneClientTest {
    private static final String ID = "PFVA1234567890123456";
    private static final String IDEMPOTENCY = "pf-va-issue-1234567890123456";
    private PortOneClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneClient(builder.build(), new PortOneProperties(
                "test-secret-not-real", "store-test", "channel-test", "https://example.com/webhooks/portone"));
    }

    private PortOneDto.IssueRequest request() {
        return new PortOneDto.IssueRequest(
                ID, "ORD-001 계약금", 500_000L,
                "김수주", "customer@example.com", "010-1111-2222",
                "SHINHAN", OffsetDateTime.parse("2099-05-01T00:00:00+09:00"), IDEMPOTENCY);
    }

    private String issuedPayment() {
        return """
                {
                  "payment": {
                    "id": "PFVA1234567890123456",
                    "status": "VIRTUAL_ACCOUNT_ISSUED",
                    "amount": { "total": 500000 },
                    "method": {
                      "type": "PaymentMethodVirtualAccount",
                      "bank": "SHINHAN",
                      "accountNumber": "123456789012",
                      "remitteeName": "회사명",
                      "expiredAt": "2099-05-01T00:00:00+09:00"
                    }
                  }
                }
                """;
    }

    @Test
    void 발급_요청의_인증_멱등키_필드와_응답을_검증한다() {
        server.expect(requestTo("https://api.portone.io/payments/" + ID + "/instant"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "PortOne test-secret-not-real"))
                .andExpect(header("Idempotency-Key", "\"" + IDEMPOTENCY + "\""))
                .andExpect(jsonPath("$.channelKey").value("channel-test"))
                .andExpect(jsonPath("$.storeId").value("store-test"))
                .andExpect(jsonPath("$.amount.total").value(500000))
                .andExpect(jsonPath("$.method.virtualAccount.bank").value("SHINHAN"))
                .andExpect(jsonPath("$.method.virtualAccount.option.type").value("NORMAL"))
                .andExpect(jsonPath("$.method.virtualAccount.expiry.dueDate").value("2099-05-01T00:00:00+09:00"))
                .andRespond(withSuccess(issuedPayment(), MediaType.APPLICATION_JSON));

        PortOneDto.IssueResult result = client.issueVirtualAccount(request());
        assertEquals(ID, result.paymentId());
        assertEquals("SHINHAN", result.bankCode());
        assertEquals("123456789012", result.accountNumber());
        assertEquals(2099, result.expiresAt().getYear());
        server.verify();
    }

    @Test
    void 결제_단건_조회는_래퍼_없는_응답으로_파싱한다() {
        server.expect(requestTo("https://api.portone.io/payments/" + ID))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "PortOne test-secret-not-real"))
                .andRespond(withSuccess("""
                        {
                          "id": "PFVA1234567890123456",
                          "status": "PAID",
                          "transactionId": "tx-123",
                          "paidAt": "2099-01-01T12:00:00+09:00",
                          "amount": {"total": 500000},
                          "method": {"type": "PaymentMethodVirtualAccount", "bank": "SHINHAN", "accountNumber": "123456789012"}
                        }
                        """, MediaType.APPLICATION_JSON));
        PortOneDto.PaymentSnapshot snapshot = client.getPayment(ID);
        assertEquals("PAID", snapshot.status());
        assertEquals(Long.valueOf(500_000L), snapshot.totalAmount());
        assertEquals("tx-123", snapshot.transactionId());
        server.verify();
    }

    @Test
    void 다른_결제금액이면_발급을_성공처리하지_않는다() {
        server.expect(requestTo("https://api.portone.io/payments/" + ID + "/instant"))
                .andRespond(withSuccess(issuedPayment().replace("500000", "400000"), MediaType.APPLICATION_JSON));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.issueVirtualAccount(request()));
        assertTrue(error.getMessage().contains("금액"));
        server.verify();
    }

    @Test
    void 포트원_서버_오류시_발급결과_불확실로_표시한다() {
        server.expect(requestTo("https://api.portone.io/payments/" + ID + "/instant"))
                .andRespond(withServerError());
        PortOneClientException error = assertThrows(PortOneClientException.class,
                () -> client.issueVirtualAccount(request()));
        assertTrue(error.isUncertain());
        server.verify();
    }

    @Test
    void 응답에_계좌정보가_없으면_결제단건조회로_보완한다() {
        server.expect(requestTo("https://api.portone.io/payments/" + ID + "/instant"))
                .andRespond(withSuccess("""
                        {"payment":{"id":"PFVA1234567890123456","status":"VIRTUAL_ACCOUNT_ISSUED","amount":{"total":500000}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.portone.io/payments/" + ID))
                .andRespond(withSuccess("""
                        {
                          "id": "PFVA1234567890123456",
                          "status": "VIRTUAL_ACCOUNT_ISSUED",
                          "amount": {"total": 500000},
                          "method": {
                            "type": "PaymentMethodVirtualAccount",
                            "bank": "SHINHAN",
                            "accountNumber": "123456789012",
                            "remitteeName": "회사명",
                            "expiredAt": "2099-05-01T00:00:00+09:00"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        PortOneDto.IssueResult result = client.issueVirtualAccount(request());
        assertEquals("123456789012", result.accountNumber());
        server.verify();
    }
}
