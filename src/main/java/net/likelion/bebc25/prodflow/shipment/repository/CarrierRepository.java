package net.likelion.bebc25.prodflow.shipment.repository;

import net.likelion.bebc25.prodflow.shipment.entity.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRepository extends JpaRepository<Carrier, String> {
}
