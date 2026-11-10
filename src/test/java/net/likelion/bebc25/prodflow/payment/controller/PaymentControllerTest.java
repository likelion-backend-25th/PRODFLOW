package net.likelion.bebc25.prodflow.payment.controller;

import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PaymentHistoryDto;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.service.PaymentQueryService;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest {

    private PaymentService paymentService;
    private PaymentQueryService paymentQueryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {

        // 테스트마다 새로운 Mock 객체 생성
        paymentService = mock(PaymentService.class);
        paymentQueryService = mock(PaymentQueryService.class);

        // 로그인 사용자 설정
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );

        // MockMvc 설정
        mvc = MockMvcBuilders.standaloneSetup(
                        new PaymentController(
                                paymentService,
                                paymentQueryService
                        )
                )
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("현금 계약금 요청은 로그인 직원을 전달하고 생산대기 상태와 작업지시 ID를 응답한다")
    void recordManualDeposit() throws Exception {

        PaymentDto.ManualDepositResponse response =
                new PaymentDto.ManualDepositResponse(
                        31L,
                        123L,
                        5000L,
                        PaymentMethod.MD,
                        OffsetDateTime.parse("2026-11-08T10:00:00+09:00"),
                        "CONFIRMED",
                        ConfirmationStatus.CONFIRMED,
                        OrderPaymentStatus.PARTIALLY_PAID,
                        List.of(41L, 42L)
                );

        when(paymentService.recordManualDeposit(any(), eq(7L)))
                .thenReturn(response);

        mvc.perform(
                        post("/api/v1/payments/deposit/manual")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orderId\":123,\"amount\":5000}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(31))
                .andExpect(jsonPath("$.paymentMethod").value("MD"))
                .andExpect(jsonPath("$.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.workOrderIds[0]").value(41));

        verify(paymentService).recordManualDeposit(
                argThat(request ->
                        request.orderId() == 123L
                                && request.amount() == 5000L
                                && request.paidAt() == null
                ),
                eq(7L)
        );
    }

    @Test
    @DisplayName("현금 입금 요청의 필수 값과 양수 금액을 검증한다")
    void rejectInvalidManualDepositRequest() throws Exception {

        for (String body : List.of(
                "{}",
                "{\"orderId\":123,\"amount\":0}",
                "{\"orderId\":0,\"amount\":5000}",
                "{\"orderId\":123,\"amount\":-1}"
        )) {

            mvc.perform(
                            post("/api/v1/payments/deposit/manual")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                    )
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("이미 처리된 계약금은 409로 응답한다")
    void duplicateManualDepositReturnsConflict() throws Exception {

        when(paymentService.recordManualDeposit(any(), eq(7L)))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "이미 처리된 계약금입니다."
                        )
                );

        mvc.perform(
                        post("/api/v1/payments/deposit/manual")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orderId\":123,\"amount\":5000}")
                )
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("수주별 금액 요약과 수동 입금 내역을 응답한다")
    void getByOrderId() throws Exception {

        OffsetDateTime paidAt =
                OffsetDateTime.parse("2026-11-06T10:00:00+09:00");

        LocalDateTime createdAt =
                LocalDateTime.of(2026, 11, 6, 10, 0);

        PaymentHistoryDto.Response history =
                new PaymentHistoryDto.Response(
                        31L,
                        7L,
                        null,
                        null,
                        paidAt,
                        createdAt
                );

        PaymentDto.OrderPaymentResponse payment =
                new PaymentDto.OrderPaymentResponse(
                        7L,
                        PaymentType.DEPOSIT,
                        5000L,
                        PaymentStatus.PAID,
                        PaymentMethod.MD,
                        createdAt,
                        null,
                        history
                );

        when(paymentService.getByOrderId(123L))
                .thenReturn(
                        new PaymentDto.OrderResponse(
                                123L,
                                10001L,
                                5000,
                                5000,
                                5001,
                                List.of(payment)
                        )
                );

        mvc.perform(
                        get("/api/v1/payments")
                                .param("orderId", "123")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(123))
                .andExpect(jsonPath("$.totalAmount").value(10001))
                .andExpect(jsonPath("$.depositAmount").value(5000))
                .andExpect(jsonPath("$.paidAmount").value(5000))
                .andExpect(jsonPath("$.balanceAmount").value(5001))
                .andExpect(jsonPath("$.payments[0].paymentId").value(7))
                .andExpect(jsonPath("$.payments[0].paymentType").value("DEPOSIT"))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("MD"))
                .andExpect(jsonPath("$.payments[0].paymentHistory.paidAt")
                        .value("2026-11-06T10:00:00+09:00"));

        verify(paymentService).getByOrderId(123L);
    }

    @Test
    @DisplayName("결제 미생성 수주도 200과 빈 결제 목록을 응답한다")
    void getOrderWithoutPayments() throws Exception {

        when(paymentService.getByOrderId(123L))
                .thenReturn(
                        new PaymentDto.OrderResponse(
                                123L,
                                10001L,
                                5000,
                                0,
                                10001,
                                List.of()
                        )
                );

        mvc.perform(
                        get("/api/v1/payments")
                                .param("orderId", "123")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments").isEmpty())
                .andExpect(jsonPath("$.depositAmount").value(5000));
    }

    @Test
    @DisplayName("없는 수주는 404로 응답한다")
    void unknownOrderReturnsNotFound() throws Exception {

        when(paymentService.getByOrderId(Long.MAX_VALUE))
                .thenThrow(
                        new NoSuchElementException(
                                "수주를 찾을 수 없습니다."
                        )
                );

        mvc.perform(
                        get("/api/v1/payments")
                                .param("orderId", Long.toString(Long.MAX_VALUE))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("수주를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("수주 ID가 없으면 전체 입금 목록을 조회한다")
    void getPaymentsWithoutOrderId() throws Exception {

        // given: 페이지 번호와 크기가 지정된 빈 목록
        Page<PaymentDto.ListResponse> emptyPage =
                Page.empty(PageRequest.of(0, 10));

        when(paymentQueryService.getPayments(
                any(PaymentDto.SearchRequest.class),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        // when & then
        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 목록 조회 서비스가 호출되었는지 확인
        verify(paymentQueryService).getPayments(
                any(PaymentDto.SearchRequest.class),
                any(Pageable.class)
        );

        // 수주별 결제 조회 서비스는 호출되지 않아야 함
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("잘못된 수주 ID 형식은 서비스 호출 전에 거부한다")
    void rejectInvalidOrderId() throws Exception {

        mvc.perform(
                        get("/api/v1/payments")
                                .param("orderId", "invalid")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }
}
