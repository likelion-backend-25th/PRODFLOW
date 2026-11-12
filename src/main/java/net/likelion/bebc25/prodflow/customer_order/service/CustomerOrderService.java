package net.likelion.bebc25.prodflow.customer_order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderListDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
@Validated
@RequiredArgsConstructor
public class CustomerOrderService {
    private final CustomerOrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final UserRepository userRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final QuotationScheduleService scheduleService;
    private final StatusHistoryRepository statusHistoryRepository;

    // 수주 상태 변경 이력 전체 조회
    public List<CustomerOrderDto.HistoryResponse> getHistories(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다.");
        }
        return statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(HistoryEntityType.ORDER,
                orderId)
                .stream()
                .map(CustomerOrderDto.HistoryResponse::from)
                .toList();
    }

    // 수주 기본 정보와 저장 당시의 품목·금액을 조회한다.
    public CustomerOrderDetailDto.Response getOrder(Long orderId) {
        CustomerOrder order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."));

        List<OrderProduct> items = orderProductRepository.findAllByCustomerOrderId(orderId);
        List<CustomerOrderDetailDto.ItemResponse> itemResponses = new ArrayList<>();

        long supplyAmount = 0;

        for (OrderProduct item : items) {
            long itemSupplyAmount = order.isVatIncluded() ? BigDecimal.valueOf(item.lineAmount())
                    .divide(new BigDecimal("1.1"), 0, RoundingMode.HALF_UP).longValueExact()
                    : item.getUnitPrice() * item.getQuantity();
            supplyAmount += itemSupplyAmount;

            itemResponses.add(CustomerOrderDetailDto.ItemResponse.from(item, itemSupplyAmount));
        }

        // VAT 포함 총액의 공급가액은 견적 상세와 동일하게 총액 기준으로 반올림한다.
        if (order.isVatIncluded()) {
            supplyAmount = BigDecimal.valueOf(order.getTotalAmount())
                    .divide(new BigDecimal("1.1"), 0, RoundingMode.HALF_UP).longValueExact();
        }

        boolean unassigned = !orderRepository.findUnassignedOrders(List.of(orderId)).isEmpty();
        CustomerOrderListDto.DelayResponse delay = delayOf(order, unassigned, LocalDate.now(ZoneId.of("Asia/Seoul")));

        return CustomerOrderDetailDto.Response.from(order, itemResponses, supplyAmount, delay);
    }

    // 수주 등록
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public CustomerOrderDto.CreateResponse create(@NotNull @Valid CustomerOrderDto.CreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("등록자 계정을 찾을 수 없습니다."));

        if (user.getStatus() != UserStatus.ACTIVE || user.isMustChangePassword()
                || (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ORDER_MANAGER)) {
            throw new AccessDeniedException("사용 중이며 비밀번호 변경을 완료한 계정만 수주를 등록할 수 있습니다.");
        }

        // 서로 다른 견적도 납기 계산부터 저장까지 직렬화해 같은 생산 용량의 중복 사용을 막는다.
        // READ_COMMITTED로 잠금 대기 후 먼저 등록된 수주를 조회한다.
        CommonCode waiting = commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("수주 대기 상태 코드가 등록되어 있지 않습니다."));

        Quotation quotation = quotationRepository.findByIdForUpdate(request.quotationId())
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적입니다. ID: " + request.quotationId()));

        orderRepository.findByQuotationId(quotation.getId()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이미 등록된 수주가 있습니다. 수주 ID: " + existing.getId() + ", 수주번호: " + existing.getOrderNo());
        });

        if (quotation.getStatus() != QuotationStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "승인된 견적으로만 수주를 등록할 수 있습니다.");
        }

        List<QuotationItem> quotedItems = quotationItemRepository.findByQuotationIdOrderByIdAsc(quotation.getId());
        Map<Long, Integer> quantities = validateItems(request.items(), quotedItems);

        long totalQuantity = 0;
        long totalAmount = 0;

        try {
            for (QuotationItem item : quotedItems) {
                int quantity = quantities.get(item.getId());
                totalQuantity = Math.addExact(totalQuantity, quantity);
                totalAmount = Math.addExact(totalAmount,
                        QuotationItem.calculateAmount(quantity, item.getUnitPrice(), quotation.isVatIncluded()));
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("수주 수량 또는 금액이 허용 범위를 초과합니다.", ex);
        }

        String shippingAddress = resolveShippingAddress(request, quotation.getClient().getAddress());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        boolean sameDayStart = Boolean.TRUE.equals(request.sameDayStart());
        QuotationScheduleResult schedule = sameDayStart
                ? scheduleService.calculate(totalQuantity, request.dueDate(), today, true)
                : scheduleService.calculate(totalQuantity, request.dueDate(), today);

        CustomerOrder order = new CustomerOrder(
                "TEMP-" + UUID.randomUUID(),
                quotation,
                quotation.getClient(),
                quotation.getClientName(),
                quotation.getClientEmail(),
                quotation.getClientPhone(),
                quotation.getClientAddress(),
                user,
                quotation.getRequestedDueDate(),
                schedule.estimatedDueDate(),
                schedule.plannedStartDate(),
                schedule.plannedEndDate(),
                sameDayStart ? today : today.plusDays(3),
                shippingAddress,
                request.receiverName(),
                request.receiverPhone(),
                request.notifyChannel(),
                request.memo(),
                waiting,
                quotation.isVatIncluded(),
                totalAmount);
        orderRepository.save(order);
        order.changeOrderNo("PF-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + order.getId());
        orderProductRepository.saveAll(quotedItems.stream()
                .map(item -> new OrderProduct(order, item.getProduct(), item.getProductName(),
                        quantities.get(item.getId()), item.getUnitPrice())).toList());
        orderRepository.flush();

        StatusHistory history = new StatusHistory(
                HistoryEntityType.ORDER, order.getId(), "NONE", "WAITING", user);
        history.writeNote("승인된 견적으로 수주를 등록했습니다. 계약금 확인 전 대기 상태입니다.");
        statusHistoryRepository.save(history);

        return CustomerOrderDto.CreateResponse.from(order);
    }

    // 수주 품목·수량 검증
    private Map<Long, Integer> validateItems(List<CustomerOrderDto.ItemRequest> requested, List<QuotationItem> quoted) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (CustomerOrderDto.ItemRequest item : requested) {
            if (quantities.putIfAbsent(item.quotationItemId(), item.quantity()) != null) {
                throw new IllegalArgumentException("같은 견적 품목을 중복 전달할 수 없습니다.");
            }
        }
        if (quantities.size() != quoted.size() || quoted.isEmpty()) {
            throw new IllegalArgumentException("승인 견적의 모든 품목을 빠짐없이 전달해주세요.");
        }
        for (QuotationItem item : quoted) {
            Integer quantity = quantities.get(item.getId());
            if (quantity == null || quantity < 100 || quantity > item.getQuantity()) {
                throw new IllegalArgumentException("수주 수량은 품목별 100개 이상, 승인 견적 수량 이하여야 합니다.");
            }
        }
        return quantities;
    }

    // 배송지 결정
    private String resolveShippingAddress(CustomerOrderDto.CreateRequest request, Address companyAddress) {
        if (Boolean.TRUE.equals(request.useCompanyAddress())) {
            if (companyAddress == null || isBlank(companyAddress.getZipcode())
                    || isBlank(companyAddress.getRoadAddress()) || isBlank(companyAddress.getDetailAddress())) {
                throw new IllegalArgumentException("등록된 회사 주소가 없습니다. 배송지를 직접 선택해주세요.");
            }
            return formatAddress(companyAddress.getZipcode(), companyAddress.getRoadAddress(), companyAddress
                    .getDetailAddress());
        }
        CustomerOrderDto.ShippingAddress address = request.shippingAddress();
        if (address == null) {
            throw new IllegalArgumentException("직접 배송 시 배송지를 입력해주세요.");
        }
        return formatAddress(address.zipcode(), address.roadAddress(), address.detailAddress());
    }

    // 빈 문자열 확인
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 배송지 문자열 변환
    private String formatAddress(String zipcode, String roadAddress, String detailAddress) {
        return "(" + zipcode.trim() + ") " + roadAddress.trim() + " " + detailAddress.trim();
    }

    // 수주 목록 조회
    public PageResponse<CustomerOrderListDto.Response> getOrders(CustomerOrderListDto.SearchCondition condition,
            Pageable pageable) {
        if (condition.statusCodeId() != null && orderRepository.countOrderStatus(condition.statusCodeId()) == 0) {
            throw new IllegalArgumentException("그룹 ID 2에 속한 수주 진행 상태 ID를 선택해주세요.");
        }
        Page<CustomerOrder> orders = orderRepository.searchOrders(normalizeKeyword(condition.keyword()), condition
                .tab() == OrderListTab.DONE,
                condition.confirmationStatus(), condition.statusCodeId(), condition.paymentStatus(),
                condition.sortType() == OrderListSortType.COMPLETED_AT,
                pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : Pageable
                        .unpaged());
        List<Long> ids = orders.getContent().stream().map(CustomerOrder::getId).toList();
        Map<Long, List<OrderProduct>> items = orderRepository.findItems(ids).stream()
                .collect(Collectors.groupingBy(item -> item.getCustomerOrder().getId()));
        Map<Long, Shipment> shipments = orderRepository.findShipments(ids).stream()
                .collect(Collectors.toMap(Shipment::getOrderId, Function.identity()));
        Set<Long> unassigned = Set.copyOf(orderRepository.findUnassignedOrders(ids));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return PageResponse.from(orders.map(order -> toResponse(order, items.getOrDefault(order.getId(), List.of()),
                shipments.get(order.getId()), unassigned.contains(order.getId()), today)));
    }

    // 수주 목록 응답 변환
    private CustomerOrderListDto.Response toResponse(CustomerOrder order, List<OrderProduct> items, Shipment shipment,
                                                     boolean unassigned, LocalDate today) {
        List<CustomerOrderListDto.ItemResponse> responses = items.stream()
                .map(item -> new CustomerOrderListDto.ItemResponse(item.getId(), item.getProduct().getId(),
                        item.getProductName(), item.getQuantity())).toList();
        return new CustomerOrderListDto.Response(order.getId(), order.getOrderNo(), order.getClient().getId(),
                order.getClient().getCompany(), responses, items.size(), items.stream().mapToLong(
                        OrderProduct::getQuantity).sum(),
                order.getTotalAmount(), order.getEstimatedDueDate(), order.getPlannedStartDate(), order
                        .getPlannedEndDate(),
                order.getDepositDueDate(), order.getConfirmationStatus(), order.getStatusCode().getId(),
                order.getStatusCode().getCode(), order.getStatusCode().getName(),
                order.getPaymentStatus(), shipment == null ? null : shipment.getCarrier().getName(),
                shipment == null ? null : shipment.getTrackingNumber(), shipment == null ? null : shipment
                        .getDeliveredAt(),
                order.getCreatedAt(), delayOf(order, unassigned, today));
    }

    // 수주 전체 집계 조회
    public CustomerOrderListDto.SummaryResponse getSummary() {
        Map<String, Long> counts = orderRepository.countByStatus().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
        LocalDate month = LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);
        long done = counts.getOrDefault("COMPLETED", 0L) + counts.getOrDefault("CANCELLED", 0L);
        long open = counts.entrySet().stream().filter(entry -> !Set.of("COMPLETED", "CANCELLED").contains(entry
                .getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        return new CustomerOrderListDto.SummaryResponse(counts.getOrDefault("CONFIRMED", 0L),
                counts.getOrDefault("IN_PRODUCTION", 0L) + counts.getOrDefault("PACKING", 0L) + counts.getOrDefault(
                        "ON_HOLD", 0L),
                counts.getOrDefault("READY_TO_SHIP", 0L) + counts.getOrDefault("SHIPPING", 0L),
                orderRepository.countCompleted(month.atStartOfDay(), month.plusMonths(1).atStartOfDay()), open, done);
    }

    // 수주 진행 상태 선택 목록 조회
    public List<CustomerOrderListDto.StatusResponse> getStatuses() {
        return orderRepository.findStatuses(CodeStatus.ACTIVE).stream().map(code -> new CustomerOrderListDto
                .StatusResponse(
                code.getId(), code.getCode(), code.getName(), code.getSortOrder())).toList();
    }

    // 검색 결과 전체 CSV 생성
    public byte[] export(CustomerOrderListDto.SearchCondition condition) {
        List<CustomerOrderListDto.Response> orders = getOrders(condition, Pageable.unpaged()).getContent();
        StringBuilder csv = new StringBuilder("\uFEFF수주번호,거래처,품목,수주수량,수주금액,납기일,수주확정상태,진행상태,입금상태,배송완료일\r\n");
        for (CustomerOrderListDto.Response order : orders) {
            List<String> values = List.of(order.orderNo(), order.clientCompany(),
                    order.items().stream().map(item -> item.productName() + " " + item.quantity() + "개").collect(
                            Collectors.joining(" / ")),
                    Long.toString(order.totalQuantity()), Long.toString(order.totalAmount()), order.dueDate()
                            .toString(),
                    order.confirmationStatus().name(), order.statusName(), order.paymentStatus().name(),
                    order.completedAt() == null ? "" : order.completedAt().toString());
            csv.append(values.stream().map(this::csvCell).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // CSV 셀 이스케이프
    private String csvCell(String value) {
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // 납기 지연·주의 판단
    private CustomerOrderListDto.DelayResponse delayOf(CustomerOrder order, boolean unassigned, LocalDate today) {
        String status = order.getStatusCode().getCode();
        if (Set.of("COMPLETED", "CANCELLED").contains(status)) {
            return null;
        }
        if (order.getEstimatedDueDate().isBefore(today)) {
            return new CustomerOrderListDto.DelayResponse("late", "납기 " + ChronoUnit.DAYS.between(order
                    .getEstimatedDueDate(), today) + "일 초과");
        }
        if (Set.of("CONFIRMED", "IN_PRODUCTION", "ON_HOLD").contains(status) && order.getPlannedEndDate().isBefore(
                today)) {
            return new CustomerOrderListDto.DelayResponse("late", "생산 완료 예정일 경과");
        }
        if (status.equals("ON_HOLD")) {
            return new CustomerOrderListDto.DelayResponse("risk", "생산 일시정지");
        }
        if (status.equals("CONFIRMED") && unassigned) {
            return new CustomerOrderListDto.DelayResponse("risk", "담당자 미배정");
        }
        if (status.equals("CONFIRMED") && order.getPlannedStartDate().isBefore(today)) {
            return new CustomerOrderListDto.DelayResponse("risk", "생산 시작 예정일 경과");
        }
        if (status.equals("WAITING") && !order.getDepositDueDate().isAfter(today)) {
            return new CustomerOrderListDto.DelayResponse("risk", "계약금 입금 마감일 " + (order.getDepositDueDate().equals(
                    today) ? "도래" : "경과"));
        }
        if (status.equals("WAITING") && !order.getPlannedStartDate().isAfter(today)) {
            return new CustomerOrderListDto.DelayResponse("risk", "선금 미확인 · 생산 시작일 도래");
        }
        if (!status.equals("SHIPPING") && ChronoUnit.DAYS.between(today, order.getEstimatedDueDate()) <= 3) {
            return new CustomerOrderListDto.DelayResponse("risk", "납기 임박 · 미출고");
        }
        return null;
    }

    // 검색어 정규화
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    public CustomerOrderDto.ClientDetailResponse getRecentByClientId(Long clientId) {
        CustomerOrder order = orderRepository.findFirstByClient_IdOrderByCreatedAtDesc(clientId)
                .orElseThrow(()
                        -> new IllegalArgumentException("해당 거래처 ID의 수주 내역을 찾을 수 없습니다. id = " + clientId));
        List<OrderProduct> orderProductList = orderProductRepository.findAllByCustomerOrderId(order.getId());
        String productName = orderProductList.size() > 1
                ? orderProductList.getFirst().getProductName() + " 외 " + (orderProductList.size() - 1)
                : orderProductList.getFirst().getProductName();
        return CustomerOrderDto.ClientDetailResponse.from(order, productName);
    }

    public Page<CustomerOrderDto.ClientDetailResponse> findByClientId(Long clientId, Pageable pageable) {
        Page<CustomerOrder> customerOrders = orderRepository.findAllByClient_Id(clientId, pageable);

        return customerOrders.map(order -> {
            List<OrderProduct> orderProductList = orderProductRepository.findAllByCustomerOrderId(order.getId());
            String productName = orderProductList.size() > 1
                    ? orderProductList.getFirst().getProductName() + " 외 " + (orderProductList.size() - 1)
                    : orderProductList.getFirst().getProductName();

            return CustomerOrderDto.ClientDetailResponse.from(order, productName);
        });
    }
}
