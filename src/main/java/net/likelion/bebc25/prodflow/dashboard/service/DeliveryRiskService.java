package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.DeliveryRiskRepository;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryRiskService {

    // 납기 임박 기준일
    private static final int URGENT_DAYS = 3;

    private final DeliveryRiskRepository deliveryRiskRepository;

    public DashboardDto.DeliveryRiskStatisticsResponse getDeliveryRisks() {
        LocalDate today = LocalDate.now();
        LocalDate riskDate = today.plusDays(URGENT_DAYS);

        List<WorkOrder> workOrders = deliveryRiskRepository.findDeliveryRiskWorkOrders(riskDate);

        // 한 수주에 작업지시서가 여러 개 있어도 수주별로 한 건만 조회
        Map<Long, WorkOrder> orderWorkOrders = workOrders.stream()
                .collect(Collectors.toMap(
                        wo -> wo.getOrderProduct().getCustomerOrder().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));

        List<DashboardDto.DeliveryRiskStatisticsItemResponse> items =
                orderWorkOrders.values().stream()
                        .map(workOrder -> toResponseItem(workOrder, today))
                        .sorted((first, second) ->
                                first.requestedDueDate()
                                        .compareTo(second.requestedDueDate()))
                        .toList();

        return new DashboardDto.DeliveryRiskStatisticsResponse(
                items.size(),
                items
        );
    }

    private DashboardDto.DeliveryRiskStatisticsItemResponse toResponseItem(
            WorkOrder workOrder,
            LocalDate today
    ) {
        var orderProduct = workOrder.getOrderProduct();
        var customerOrder = orderProduct.getCustomerOrder();

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

        return new DashboardDto.DeliveryRiskStatisticsItemResponse(
                customerOrder.getId(),
                customerOrder.getOrderNo(),
                customerOrder.getClient().getCompany(),
                orderProduct.getProductName(),
                customerOrder.getRequestedDueDate(),
                customerOrder.getPlannedEndDate(),
                workOrder.getCommonCode().getId(),
                riskType,
                riskReason
        );
    }

}
