package net.likelion.bebc25.prodflow.client.repository;

import net.likelion.bebc25.prodflow.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    // 특정 거래처명과 일치하는 기존 거래처 조회
    Optional<Client> findFirstByCompanyOrderByIdAsc(String company);
}
