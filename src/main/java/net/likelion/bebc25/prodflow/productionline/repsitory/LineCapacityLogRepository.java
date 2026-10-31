package net.likelion.bebc25.prodflow.productionline.repsitory;

import net.likelion.bebc25.prodflow.productionline.entity.LineCapacityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineCapacityLogRepository extends JpaRepository<LineCapacityLog, Long> {
}
