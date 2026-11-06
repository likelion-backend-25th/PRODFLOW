package net.likelion.bebc25.prodflow.shipment.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.entity.Carrier;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.CarrierRepository;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final String ORDER_STATUS = "ORDER_STATUS";
    private static final String READY_TO_SHIP = "READY_TO_SHIP";
    private static final String SHIPPING = "SHIPPING";

    private final ShipmentRepository shipmentRepository;
    private final CarrierRepository carrierRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final UserRepository userRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional
    public ShipmentDto.Response register(ShipmentDto.CreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("등록한 직원을 찾을 수 없습니다."));

        if (request.orderId() == null) {
            throw new IllegalArgumentException("수주를 선택해 주세요.");
        }

        CustomerOrder order = customerOrderRepository.findById(request.orderId())
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));

        String currentStatus = order.getStatusCode().getCode();
        if (!READY_TO_SHIP.equals(currentStatus)) {
            throw new IllegalArgumentException("출고대기 수주만 운송장을 등록할 수 있습니다.");
        }
        if (shipmentRepository.existsByOrderId(order.getId())) {
            throw new IllegalArgumentException("이미 운송장이 등록된 수주입니다.");
        }

        String carrierCode = request.carrierCode() == null ? "" : request.carrierCode().trim();
        if (carrierCode.isEmpty()) {
            throw new IllegalArgumentException("택배사를 선택해 주세요.");
        }
        Carrier carrier = carrierRepository.findById(carrierCode)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 택배사입니다."));

        String trackingNumber = normalizeTrackingNumber(request.trackingNumber());
        if (shipmentRepository.existsByCarrier_CodeAndTrackingNumber(carrier.getCode(), trackingNumber)) {
            throw new IllegalArgumentException("같은 택배사에 이미 등록된 운송장 번호입니다.");
        }

        CommonCode shipping = commonCodeRepository
                .findByCodeGroup_GroupCodeAndCodeAndStatus(ORDER_STATUS, SHIPPING, CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("배송중 상태 코드를 찾을 수 없습니다."));

        Shipment shipment = shipmentRepository.save(
                Shipment.start(order.getId(), carrier, trackingNumber, LocalDateTime.now())
        );
        order.changeProgress(shipping);
        statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.ORDER,
                order.getId(),
                currentStatus,
                shipping.getCode(),
                user
        ));
        return ShipmentDto.Response.from(shipment, order);
    }

    private String normalizeTrackingNumber(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("운송장 번호를 입력해 주세요.");
        }
        String digits = raw.replace("-", "").replace(" ", "");
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit) || digits.length() > 50) {
            throw new IllegalArgumentException("운송장 번호는 숫자만 입력해 주세요.");
        }
        return digits;
    }
}
