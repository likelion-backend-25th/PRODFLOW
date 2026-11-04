package net.likelion.bebc25.prodflow.line.dto;

import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LineDto {

    // 라인 등록
    public record CreateRequest(
            String lineName,
            int productionCapacity
    ) {
    }

    // 하루 생산량 수정. 수량이 바뀔 때 reason을 line_capacity_log에 남긴다.
    public record UpdateRequest(
            String lineName,
            int productionCapacity,
            String reason
    ) {
    }

    public record UpdateStatusRequest(
            LineStatus status
    ) {
    }

    public record Response(
            Long id,
            String lineName,
            int productionCapacity,
            LineStatus status,
            LocalDateTime updatedAt
    ) {
        public static Response from(Line line) {
            return new Response(
                    line.getId(),
                    line.getLineName(),
                    line.getProductionCapacity(),
                    line.getStatus(),
                    line.getUpdatedAt());
        }
    }

    // 생산라인 목록의 한 줄. 생산 중인 작업이 없으면 작업 번호, 품목, 수주, 담당자는 null이다.
    public record LineList(
            Long lineId,
            String lineName,
            LineStatus status,
            int productionCapacity,
            String workNo,
            String productName,
            String orderNo,
            String managerName
    ) {
    }

    // 생산라인 목록
    public record ListResponse(
            int totalCount, // 전체 라인 수
            int runningCount, // 작동중인 라인 수
            int availableCount, // 작동 가능한 라인 수
            int inspectionOrBrokenCount, // 점검 혹은 고장난 라인 수
            List<LineList> lines // 목록 내 정보
    ) {
    }

    // 생산라인 상세. 현재 작업이 없으면 작업 번호, 품목, 수주, 일정, 담당자는 null이다.
    public record DetailResponse(
            Long lineId,
            String lineName,
            LineStatus status,
            int productionCapacity, // 일일 생산량이자 라인 현황의 최대 생산량
            int workOrderCount, // 이 라인에 배정된 작업지시 수
            int waitingCount, // PREPARE 진행중인 작업지시 수
            String workNo, // 작업지시서 번호
            String productName, // 담당 품목
            String orderNo, // 수주 번호
            LocalDate plannedStartDate, // 예상 시작일
            LocalDate plannedEndDate, // 예상 종료일
            String managerName, // 담당 직원
            List<DetailWorkOrder> workOrders, // 작업지시서 상세
            List<CapacityLog> capacityLogs // 생산량 변경 이력 리스트
    ) {
    }

    // 이 라인의 현재·예정 작업 한 건. PRODUCE, PREPARE, SUSPEND만 포함한다.
    public record DetailWorkOrder(
            String workNo,
            String orderNo,
            String productName,
            int quantity,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            String status
    ) {
    }

    // 하루 생산량 변경 이력 한 건.
    public record CapacityLog(
            LocalDateTime changedAt,
            int beforeCapacity,
            int afterCapacity,
            String reason,
            String managerName
    ) {
    }
}
