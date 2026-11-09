package net.likelion.bebc25.prodflow.shipment.repository;

import net.likelion.bebc25.prodflow.shipment.entity.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarrierRepository extends JpaRepository<Carrier, String> {

    List<Carrier> findAllByOrderByCodeAsc();
}
