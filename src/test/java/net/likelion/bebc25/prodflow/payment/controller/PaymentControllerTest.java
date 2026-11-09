package net.likelion.bebc25.prodflow.payment.controller;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PaymentHistoryDto;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {
    private final PaymentService paymentService = mock(PaymentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("현금 계약금 요청은 로그인 직원을 전달하고 생산대기 상태와 작업지시 ID를 응답한다")
    void recordManualDeposit() throws Exception {
        PaymentDto.ManualDepositResponse response = new PaymentDto.ManualDepositResponse(31L, 123L,
                5000L, PaymentMethod.MD, LocalDateTime.of(2026, 11, 8, 10, 0), "CONFIRMED",
                ConfirmationStatus.CONFIRMED, OrderPaymentStatus.PARTIALLY_PAID, List.of(41L, 42L));
        when(paymentService.recordManualDeposit(any(), eq(7L))).thenReturn(response);

        mvc.perform(post("/api/v1/payments/deposit/manual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":123,\"amount\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(31))
                .andExpect(jsonPath("$.paymentMethod").value("MD"))
                .andExpect(jsonPath("$.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.workOrderIds[0]").value(41));
        verify(paymentService).recordManualDeposit(argThat(request -> request.orderId() == 123L
                && request.amount() == 5000L && request.receivedAt() == null), eq(7L));
    }

    @Test
    @DisplayName("현금 입금 요청의 필수 값과 양수 금액을 검증한다")
    void rejectInvalidManualDepositRequest() throws Exception {
        for (String body : List.of("{}", "{\"orderId\":123,\"amount\":0}",
                "{\"orderId\":0,\"amount\":5000}", "{\"orderId\":123,\"amount\":-1}")) {
            mvc.perform(post("/api/v1/payments/deposit/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("이미 처리된 계약금은 409로 응답한다")
    void duplicateManualDepositReturnsConflict() throws Exception {
        when(paymentService.recordManualDeposit(any(), eq(7L)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 계약금입니다."));

        mvc.perform(post("/api/v1/payments/deposit/manual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":123,\"amount\":5000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("수주별 금액 요약과 수동 입금 내역을 응답한다")
    void getByOrderId() throws Exception {
        LocalDateTime receivedAt = LocalDateTime.of(2026, 11, 6, 10, 0);
        PaymentHistoryDto.Response history = new PaymentHistoryDto.Response(31L, 7L, null, null, receivedAt, receivedAt);
        PaymentDto.OrderPaymentResponse payment = new PaymentDto.OrderPaymentResponse(7L, PaymentType.DEPOSIT,
                5000L, PaymentStatus.PAID, PaymentMethod.MD, receivedAt, null, history);
        when(paymentService.getByOrderId(123L))
                .thenReturn(new PaymentDto.OrderResponse(123L, 10001L, 5000, 5000, 5001, List.of(payment)));

        mvc.perform(get("/api/v1/payments").param("orderId", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(123))
                .andExpect(jsonPath("$.totalAmount").value(10001))
                .andExpect(jsonPath("$.depositAmount").value(5000))
                .andExpect(jsonPath("$.paidAmount").value(5000))
                .andExpect(jsonPath("$.balanceAmount").value(5001))
                .andExpect(jsonPath("$.payments[0].paymentId").value(7))
                .andExpect(jsonPath("$.payments[0].paymentType").value("DEPOSIT"))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("MD"))
                .andExpect(jsonPath("$.payments[0].paymentHistory.receivedAt").value("2026-11-06T10:00:00"));
        verify(paymentService).getByOrderId(123L);
    }

    @Test
    @DisplayName("결제 미생성 수주도 200과 빈 결제 목록을 응답한다")
    void getOrderWithoutPayments() throws Exception {
        when(paymentService.getByOrderId(123L))
                .thenReturn(new PaymentDto.OrderResponse(123L, 10001L, 5000, 0, 10001, List.of()));

        mvc.perform(get("/api/v1/payments").param("orderId", "123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty())
                .andExpect(jsonPath("$.depositAmount").value(5000));
    }

    @Test
    @DisplayName("없는 수주는 404로 응답한다")
    void unknownOrderReturnsNotFound() throws Exception {
        when(paymentService.getByOrderId(Long.MAX_VALUE)).thenThrow(new NoSuchElementException("수주를 찾을 수 없습니다."));

        mvc.perform(get("/api/v1/payments").param("orderId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("수주를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("수주 ID 누락은 400으로 응답한다")
    void rejectMissingOrderId() throws Exception {
        when(paymentService.getByOrderId(null)).thenThrow(new IllegalArgumentException("수주 ID를 입력해 주세요."));

        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("수주 ID를 입력해 주세요."));
    }

    @Test
    @DisplayName("잘못된 수주 ID 형식은 서비스 호출 전에 거부한다")
    void rejectInvalidOrderId() throws Exception {
        mvc.perform(get("/api/v1/payments").param("orderId", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }
}
