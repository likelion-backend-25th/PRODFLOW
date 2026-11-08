package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.DeliveryRiskRepository;
import net.likelion.bebc25.prodflow.dashboard.repository.PaymentStatisticsRepository;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryRiskService {

    // 납기 임박 기준일
    private static final int URGENT_DAYS = 3;

    private final DeliveryRiskRepository deliveryRiskRepository;
    private final PaymentStatisticsRepository paymentStatisticsRepository;
    private final Clock clock;

    public DashboardDto.DeliveryRiskStatisticsResponse getDeliveryRisks() {
        LocalDate today = LocalDate.now(clock);
        LocalDate riskDate = today.plusDays(URGENT_DAYS);

        List<CustomerOrder> orders =
                deliveryRiskRepository.findDeliveryRiskOrders(riskDate);

        List<Long> orderIds = orders.stream()
                .map(CustomerOrder::getId)
                .toList();

        Map<Long, List<OrderProduct>> productsByOrder = orderIds.isEmpty()
                ? Map.of()
                : deliveryRiskRepository.findOrderProducts(orderIds).stream()
                .collect(Collectors.groupingBy(
                        product -> product.getCustomerOrder().getId()
                ));

        Map<Long, Long> paidAmounts = orderIds.isEmpty()
                ? Map.of()
                : paymentStatisticsRepository.sumPaidAmountsByOrderIds(
                        orderIds,
                        PaymentStatus.PAID
                ).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));

        List<DashboardDto.DeliveryRiskStatisticsItemResponse> items = orders.stream()
                .map(order -> toResponseItem(
                        order,
                        productsByOrder.getOrDefault(order.getId(), List.of()),
                        today,
                        paidAmounts.getOrDefault(order.getId(), 0L)
                ))
                .toList();

        return new DashboardDto.DeliveryRiskStatisticsResponse(
                items.size(),
                items
        );
    }

    private DashboardDto.DeliveryRiskStatisticsItemResponse toResponseItem(
            CustomerOrder customerOrder,
            List<OrderProduct> products,
            LocalDate today,
            long paidAmount
    ) {
        LocalDate requestedDueDate = customerOrder.getRequestedDueDate();
        long daysUntilDue = ChronoUnit.DAYS.between(today, requestedDueDate);

        String riskType;
        String riskReason;
        if (daysUntilDue < 0) {
            riskType = "DELAYED";
            riskReason = "납기일이 지났습니다.";
        } else {
            riskType = "URGENT";
            riskReason = "납기일까지 " + daysUntilDue + "일 남았습니다.";
        }

        String productName = products.stream()
                .map(OrderProduct::getProductName)
                .collect(Collectors.joining(", "));
        int quantity = products.stream()
                .mapToInt(OrderProduct::getQuantity)
                .sum();

        return new DashboardDto.DeliveryRiskStatisticsItemResponse(
                customerOrder.getId(),
                customerOrder.getOrderNo(),
                customerOrder.getClient().getCompany(),
                productName,
                quantity,
                customerOrder.getRequestedDueDate(),
                customerOrder.getPlannedEndDate(),
                customerOrder.getStatusCode().getId(),
                riskType,
                riskReason,
                customerOrder.getPaymentStatus(),
                paidAmount
        );
    }
}
