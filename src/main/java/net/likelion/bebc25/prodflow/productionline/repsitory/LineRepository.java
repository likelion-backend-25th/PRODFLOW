package net.likelion.bebc25.prodflow.productionline.repsitory;

import net.likelion.bebc25.prodflow.productionline.entity.Line;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineRepository extends JpaRepository<Line, Long> {
}
