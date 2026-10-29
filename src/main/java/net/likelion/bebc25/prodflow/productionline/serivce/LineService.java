package net.likelion.bebc25.prodflow.productionline.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineService {

    private final LineRepository lineRepository;

    /**
     * 라인 등록 (권한 : 최고 관리자, 수주 관리자)
     *
     * @param request (라인 이름, 생산량, 도입일)
     * @return save가 INSERT하고 Id를 채욵다.
     * updated_at 처음 생성시 null 허용
     */
    @Transactional
    public LineDto.Response create(LineDto.CreateRequest request) {
        Line line = new Line(
                request.lineName(),
                request.productionCapacity(),
                request.introducedDate()
        );
        return LineDto.Response.from(lineRepository.save(line));
    }
}
