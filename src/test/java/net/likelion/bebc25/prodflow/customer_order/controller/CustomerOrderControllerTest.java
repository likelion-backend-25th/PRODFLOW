package net.likelion.bebc25.prodflow.customer_order.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.customer_order.controller.CustomerOrderController;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderCancelService;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerOrderControllerTest {
    private final CustomerOrderService service = mock(CustomerOrderService.class);
    private final CustomerOrderCancelService cancelService = mock(CustomerOrderCancelService.class);
    private MockMvc mvc;
    private final String body = """
            {"quotationId":17,"items":[{"quotationItemId":31,"quantity":100}],
             "dueDate":"2027-06-30","useCompanyAddress":true,"receiverName":"수신자",
             "receiverPhone":"010-1234-5678","notifyChannel":"EMAIL","memo":"업무 메모"}
            """;

    // 테스트 환경 설정
    @BeforeEach
    void setUp() {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
        mvc = MockMvcBuilders.standaloneSetup(new CustomerOrderController(service, cancelService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // 인증 정보 정리
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 수주 등록 응답 검증
    @Test
    void cancelsOrderWithReasonAndAuthenticatedActor() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .patch("/api/v1/orders/14/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"거래처 요청\"}"))
                .andExpect(status().isOk());
        verify(cancelService).cancel(eq(14L), argThat(request -> request.reason().equals("거래처 요청")), eq(7L));
    }

    @Test
    void rejectsBlankOrTooLongCancellationReason() throws Exception {
        for (String reason : List.of("", "   ", "가".repeat(256))) {
            mvc.perform(MockMvcRequestBuilders
                            .patch("/api/v1/orders/14/cancel").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"" + reason + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cancelService);
    }

    @Test
    void createsOrderWithLocationAndInitialStatuses() throws Exception {
        when(service.create(any(), eq(7L))).thenReturn(new CustomerOrderDto.CreateResponse(1L, "PF-20261106-1",
                17L, 110000L, LocalDate.of(2027, 6, 30), LocalDate.of(2026, 11, 9),
                LocalDate.of(2027, 6, 20), LocalDate.of(2027, 6, 25), "WAITING", ConfirmationStatus.PENDING,
                OrderPaymentStatus.UNPAID));
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/orders/1"))
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.quotationId").value(17))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.confirmationStatus").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        verify(service).create(argThat(request -> request.items().getFirst().quantity() == 100
                && request.quotationId() == 17L && request.shippingAddress() == null), eq(7L));
    }

    // 잘못된 등록 요청 검증
    @Test
    void rejectsInvalidQuantityAddressReceiverAndChannelBeforeSaving() throws Exception {
        for (String invalid : List.of(body.replace("\"quantity\":100", "\"quantity\":99"),
                body.replace("\"useCompanyAddress\":true", "\"useCompanyAddress\":false"),
                body.replace("수신자", ""), body.replace("EMAIL", "PUSH"))) {
            mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    // 목록 조회 기본 페이지·검색 조건 검증
    @Test
    void listsOrdersWithCommonCodeFilterAndDefaultPageSize() throws Exception {
        when(service.getOrders(any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 8, 0, 0));
        mvc.perform(get("/api/v1/orders").param("statusCodeId", "7").param("keyword", "한빛"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(8));
        verify(service).getOrders(argThat(condition -> condition.tab() == OrderListTab.OPEN
                        && condition.statusCodeId() == 7L && condition.keyword().equals("한빛")
                        && condition.sortType() == OrderListSortType.CREATED_AT),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 8));
    }

    // 잘못된 목록 조회 조건 검증
    @Test
    void rejectsInvalidListQueryBeforeCallingService() throws Exception {
        mvc.perform(get("/api/v1/orders").param("statusCodeId", "WAITING"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders").param("tab", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("선택한 수주 ID로 상세 응답 조회")
    void getOrder() throws Exception {
        LocalDate dueDate = LocalDate.of(2027, 6, 30);
        CustomerOrderDetailDto.ClientResponse client = new CustomerOrderDetailDto.ClientResponse(
                2L, "테스트 거래처", "등록 당시 담당자", "client@test.com", "02-1111-2222", "서울시 회사 주소");
        CustomerOrderDetailDto.ItemResponse item = new CustomerOrderDetailDto.ItemResponse(
                31L, 5L, "저장된 품목명", "테스트 기종", 100, 1000L, 100000L, 10000L, 110000L);
        CustomerOrderDetailDto.Response response = new CustomerOrderDetailDto.Response(
                1L, "PF-DETAIL-001", 17L, "QT-DETAIL-001", 7L, "등록자", null, null,
                ConfirmationStatus.PENDING, 6L, "WAITING", "대기", OrderPaymentStatus.UNPAID,
                client, List.of(item), false, 100000L, 10000L, 110000L, 100L,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), null,
                dueDate.minusDays(10), "서울시 배송 주소", "홍수신", "010-3333-4444",
                NotifyChannel.EMAIL, "등록 메모", null);
        when(service.getOrder(1L)).thenReturn(response);

        mvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quotationId").value(17))
                .andExpect(jsonPath("$.client.contactName").value("등록 당시 담당자"))
                .andExpect(jsonPath("$.items[0].productName").value("저장된 품목명"))
                .andExpect(jsonPath("$.totalAmount").value(110000))
                .andExpect(jsonPath("$.dueDate").value("2027-06-30"))
                .andExpect(jsonPath("$.workOrders").doesNotExist())
                .andExpect(jsonPath("$.payments").doesNotExist())
                .andExpect(jsonPath("$.shipment").doesNotExist())
                .andExpect(jsonPath("$.histories").doesNotExist())
                .andExpect(jsonPath("$.notifications").doesNotExist());
        verify(service).getOrder(1L);
    }

    @Test
    @DisplayName("없는 수주 상세 조회는 404 응답")
    void unknownOrderReturnsNotFound() throws Exception {
        when(service.getOrder(Long.MAX_VALUE))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."));

        mvc.perform(get("/api/v1/orders/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"))
                .andExpect(jsonPath("$.message").value("수주를 찾을 수 없습니다."));
        verify(service).getOrder(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("수주 상태 변경 이력을 배열로 응답한다")
    void getHistories() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 6, 10, 0);
        CustomerOrderDto.HistoryResponse response = new CustomerOrderDto.HistoryResponse(
                31L, "WAITING", "CANCELLED", null, "SYSTEM", "계약금 마감", createdAt);
        when(service.getHistories(1L)).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/orders/1/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(31))
                .andExpect(jsonPath("$[0].fromStatus").value("WAITING"))
                .andExpect(jsonPath("$[0].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[0].userName").value("SYSTEM"))
                .andExpect(jsonPath("$[0].note").value("계약금 마감"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-06T10:00:00"));
        verify(service).getHistories(1L);
    }

    @Test
    @DisplayName("이력이 없는 수주는 빈 배열로 응답한다")
    void getHistoriesWithoutHistory() throws Exception {
        when(service.getHistories(1L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/orders/1/histories"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("없는 수주의 이력 조회는 404로 응답한다")
    void unknownOrderHistoriesReturnsNotFound() throws Exception {
        when(service.getHistories(Long.MAX_VALUE))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."));

        mvc.perform(get("/api/v1/orders/" + Long.MAX_VALUE + "/histories"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("수주를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("숫자가 아닌 수주 ID의 이력 조회는 서비스 호출 전에 거부한다")
    void rejectInvalidOrderIdForHistories() throws Exception {
        mvc.perform(get("/api/v1/orders/invalid/histories"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    // 중복 수주 등록 응답 검증
    @Test
    void duplicateOrderReturnsConflict() throws Exception {
        when(service.create(any(), eq(7L))).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "이미 등록된 수주입니다."));
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HTTP_409"));
    }
}
