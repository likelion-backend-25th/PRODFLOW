package net.likelion.bebc25.prodflow.customer_order;

import java.time.LocalDate;
import net.likelion.bebc25.prodflow.customer_order.controller.CustomerOrderController;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderListDto;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerOrderControllerTest {
    private final CustomerOrderService service = mock(CustomerOrderService.class);
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
        mvc = MockMvcBuilders.standaloneSetup(new CustomerOrderController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // 인증 정보 정리
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 수주 등록 응답 검증
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
        for (String invalid : java.util.List.of(body.replace("\"quantity\":100", "\"quantity\":99"),
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
                .thenReturn(new PageResponse<>(java.util.List.of(), 0, 8, 0, 0));
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

    // 중복 수주 등록 응답 검증
    @Test
    void duplicateOrderReturnsConflict() throws Exception {
        when(service.create(any(), eq(7L))).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 수주입니다."));
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HTTP_409"));
    }
}
