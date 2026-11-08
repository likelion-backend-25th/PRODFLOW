package net.likelion.bebc25.prodflow.shipment.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {

    private static final String ORDER_STATUS = "ORDER_STATUS";
    private static final String TAB_PACKING = "PACKING";
    private static final String TAB_READY_TO_SHIP = "READY_TO_SHIP";
    private static final String TAB_SHIPPING = "SHIPPING";
    private static final String TAB_BALANCE = "BALANCE";
    private static final String COMPLETED = "COMPLETED";

    private final CustomerOrderRepository customerOrderRepository;
    private final OrderProductRepository orderProductRepository;
    private final ShipmentRepository shipmentRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;

    // 배송 관리 화면용 목록. 탭별로 수주를 모으고 상단 건수도 같이 내려준다.
    public ShipmentDto.ListResponse findList(String tab) {
        String normalized = normalizeTab(tab);
        List<CustomerOrder> orders = findOrdersByTab(normalized);

        List<Long> orderIds = new ArrayList<>();
        for (CustomerOrder order : orders) {
            orderIds.add(order.getId());
        }

        Map<Long, List<OrderProduct>> productsByOrderId = groupProducts(orderIds);
        Map<Long, Shipment> shipmentByOrderId = groupShipments(orderIds);

        List<ShipmentDto.ListItem> items = new ArrayList<>();
        for (CustomerOrder order : orders) {
            items.add(toListItem(
                    order,
                    productsByOrderId.getOrDefault(order.getId(), List.of()),
                    shipmentByOrderId.get(order.getId())
            ));
        }

        return new ShipmentDto.ListResponse(normalized, countTabs(), items);
    }

    // 화면의 배송 완료 버튼. 운송장을 완료로 두고 수주도 COMPLETED로 바꾼다.
    @Transactional
    public ShipmentDto.CompleteResponse complete(Long shipmentId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("처리한 직원을 찾을 수 없습니다."));

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new NoSuchElementException("운송장을 찾을 수 없습니다."));

        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));

        if (!TAB_SHIPPING.equals(order.getStatusCode().getCode())) {
            throw new IllegalArgumentException("배송중 수주만 배송 완료 처리할 수 있습니다.");
        }

        shipment.markDelivered();
        completeOrder(order, user);

        return new ShipmentDto.CompleteResponse(
                shipment.getId(),
                order.getId(),
                order.getOrderNo(),
                order.getStatusCode().getCode(),
                shipment.getComplete(),
                shipment.getDeliveredAt()
        );
    }

    // 배송조회 refresh 결과가 완료면 수주를 COMPLETED로 맞춘다. 이미 완료면 그대로 둔다.
    @Transactional
    public String completeOrderIfDelivered(Shipment shipment, Long userId) {
        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(shipment.getComplete())) {
            return order.getStatusCode().getCode();
        }
        if (COMPLETED.equals(order.getStatusCode().getCode())) {
            return COMPLETED;
        }
        if (!TAB_SHIPPING.equals(order.getStatusCode().getCode())) {
            return order.getStatusCode().getCode();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("처리한 직원을 찾을 수 없습니다."));
        completeOrder(order, user);
        return COMPLETED;
    }

    private void completeOrder(CustomerOrder order, User user) {
        String fromStatus = order.getStatusCode().getCode();
        CommonCode completed = commonCodeRepository
                .findByCodeGroup_GroupCodeAndCodeAndStatus(ORDER_STATUS, COMPLETED, CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("완료 상태 코드를 찾을 수 없습니다."));

        order.changeProgress(completed);
        statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.ORDER,
                order.getId(),
                fromStatus,
                completed.getCode(),
                user
        ));
    }

    private String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return TAB_READY_TO_SHIP;
        }
        String value = tab.trim().toUpperCase();
        if (TAB_PACKING.equals(value)
                || TAB_READY_TO_SHIP.equals(value)
                || TAB_SHIPPING.equals(value)
                || TAB_BALANCE.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException(
                "배송 탭은 PACKING, READY_TO_SHIP, SHIPPING, BALANCE 중 하나여야 합니다."
        );
    }

    private List<CustomerOrder> findOrdersByTab(String tab) {
        if (TAB_BALANCE.equals(tab)) {
            return customerOrderRepository.findBalanceWaitingOrders(OrderPaymentStatus.PAID);
        }
        return customerOrderRepository.findByStatusCodeOrderByEstimatedDueDateAsc(tab);
    }

    private ShipmentDto.TabCounts countTabs() {
        return new ShipmentDto.TabCounts(
                customerOrderRepository.countByStatusCode_Code(TAB_PACKING),
                customerOrderRepository.countByStatusCode_Code(TAB_READY_TO_SHIP),
                customerOrderRepository.countByStatusCode_Code(TAB_SHIPPING),
                customerOrderRepository.countByStatusCode_CodeAndPaymentStatusNot(
                        COMPLETED,
                        OrderPaymentStatus.PAID
                )
        );
    }

    private Map<Long, List<OrderProduct>> groupProducts(List<Long> orderIds) {
        Map<Long, List<OrderProduct>> map = new HashMap<>();
        if (orderIds.isEmpty()) {
            return map;
        }
        for (OrderProduct product : orderProductRepository.findByCustomerOrder_IdIn(orderIds)) {
            Long orderId = product.getCustomerOrder().getId();
            map.computeIfAbsent(orderId, key -> new ArrayList<>()).add(product);
        }
        return map;
    }

    private Map<Long, Shipment> groupShipments(List<Long> orderIds) {
        Map<Long, Shipment> map = new HashMap<>();
        if (orderIds.isEmpty()) {
            return map;
        }
        for (Shipment shipment : shipmentRepository.findByOrderIdIn(orderIds)) {
            map.put(shipment.getOrderId(), shipment);
        }
        return map;
    }

    private ShipmentDto.ListItem toListItem(
            CustomerOrder order,
            List<OrderProduct> products,
            Shipment shipment
    ) {
        int totalQuantity = 0;
        for (OrderProduct product : products) {
            totalQuantity += product.getQuantity();
        }

        Long shipmentId = null;
        String carrierCode = null;
        String carrierName = null;
        String trackingNumber = null;
        LocalDateTime startedAt = null;
        LocalDateTime deliveredAt = null;
        if (shipment != null) {
            shipmentId = shipment.getId();
            carrierCode = shipment.getCarrier().getCode();
            carrierName = shipment.getCarrier().getName();
            trackingNumber = shipment.getTrackingNumber();
            startedAt = shipment.getStartedAt();
            deliveredAt = shipment.getDeliveredAt();
        }

        return new ShipmentDto.ListItem(
                order.getId(),
                order.getOrderNo(),
                order.getClient().getCompany(),
                productSummary(products),
                totalQuantity,
                order.getShippingAddress(),
                order.getEstimatedDueDate(),
                order.getStatusCode().getCode(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                shipmentId,
                carrierCode,
                carrierName,
                trackingNumber,
                startedAt,
                deliveredAt
        );
    }

    // 품목이 여러 개면 "첫 품목 외 N건"으로 줄인다.
    private String productSummary(List<OrderProduct> products) {
        if (products.isEmpty()) {
            return "";
        }
        String firstName = products.get(0).getProductName();
        if (products.size() == 1) {
            return firstName;
        }
        return firstName + " 외 " + (products.size() - 1) + "건";
    }
}
