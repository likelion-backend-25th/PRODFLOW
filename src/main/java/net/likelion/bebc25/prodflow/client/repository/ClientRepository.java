package net.likelion.bebc25.prodflow.client.repository;

import net.likelion.bebc25.prodflow.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
