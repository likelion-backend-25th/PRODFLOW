package net.likelion.bebc25.prodflow.commoncode.repository;

import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, Long> {

    Optional<CodeGroup> findByGroupCode(String groupCode);
}
