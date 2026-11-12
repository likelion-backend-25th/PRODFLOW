package net.likelion.bebc25.prodflow.shipment.repository;

import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    @Query("""
            SELECT s FROM Shipment s
            JOIN FETCH s.carrier
            WHERE s.orderId IN :orderIds
            """)
    List<Shipment> findByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);
    boolean existsByOrderId(Long orderId);

    boolean existsByCarrier_CodeAndTrackingNumber(String carrierCode, String trackingNumber);


    //아직 배송 완료가 아닌 운송장 (주기 조회 대상)
    //complete = false 이거나 null
    @Query("""
            SELECT s FROM Shipment s
            JOIN FETCH s.carrier
            WHERE s.startedAt IS NOT NULL AND (s.complete = false OR s.complete IS NULL)
            """)
    List<Shipment> findIncompleteShipments();
}
