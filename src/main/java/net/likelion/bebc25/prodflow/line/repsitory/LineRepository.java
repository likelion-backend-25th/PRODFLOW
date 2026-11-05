package net.likelion.bebc25.prodflow.line.repsitory;

import net.likelion.bebc25.prodflow.line.dto.LineDto;
import net.likelion.bebc25.prodflow.line.entity.Line;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LineRepository extends JpaRepository<Line, Long> {

    // line_name 중복 검사
    boolean existsByLineName(String lineName);

    // 라인에서 아우터 조인한다. PRODUCE 조건은 ON에 둔다.
    // WHERE에 두면 생산 중인 작업이 없는 라인이 빠진다.
    // 품목, 수주, 담당자는 그 작업지시에만 붙어 있고, 작업이 없으면 null이다.
    @Query("""
            select new net.likelion.bebc25.prodflow.line.dto.LineDto$LineList(
                l.id,
                l.lineName,
                l.status,
                l.productionCapacity,
                wo.workNo,
                op.productName,
                co.orderNo,
                u.name
            )
            from Line l
            left join CommonCode cc
                on cc.code = 'PRODUCE'
               and cc.codeGroup.groupCode = 'WORK_ORDER_STATUS'
            left join WorkOrder wo
                on wo.line = l
               and wo.commonCode = cc
            left join OrderProduct op
                on op = wo.orderProduct
            left join CustomerOrder co
                on co = op.customerOrder
            left join User u
                on u = wo.user
            order by l.id asc, wo.id asc
            """)
    List<LineDto.LineList> findLineList();
}
