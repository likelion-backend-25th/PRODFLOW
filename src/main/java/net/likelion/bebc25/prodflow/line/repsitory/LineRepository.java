package net.likelion.bebc25.prodflow.line.repsitory;

import net.likelion.bebc25.prodflow.line.entity.Line;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineRepository extends JpaRepository<Line, Long> {

    // line_name 중복 검사
    boolean existsByLineName(String lineName);


}
