package net.likelion.bebc25.prodflow.productionline.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.productionline.entity.LineCapacityLog;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineCapacityLogRepository;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineService {

    private final LineRepository lineRepository;
    private final UserRepository userRepository;
    private final LineCapacityLogRepository lineCapacityLogRepository; // 라인 변경시 이력을 저장
    private final StatusHistoryRepository statusHistoryRepository; // 상태 변경시 이력을 저장


    /**
     * 라인 등록 (권한 : 최고 관리자, 수주 관리자)
     *
     * @param request (라인 이름, 생산량, 도입일)
     * @param userId  액세스 토큰 subject의 직원 id. 이름은 토큰에 없다.
     * @return save가 INSERT하고 Id를 채운다.
     * updated_at 처음 생성시 null 허용
     */
    @Transactional
    public LineDto.Response create(LineDto.CreateRequest request, Long userId) {

        String lineName = request.lineName() == null ? "" : request.lineName().trim();
        if (lineName.isBlank()) {
            throw new IllegalArgumentException("라인 이름은 공백 허용 안함");
        }
        // 등록 화면 규칙: 하루 생산량은 1 이상. 0과 음수는 저장하지 않음
        if (request.productionCapacity() < 1) {
            throw new IllegalArgumentException("하루 생산량은 1 이상");
        }
        if (lineRepository.existsByLineName(lineName)) {
            throw new IllegalArgumentException("이미 존재하는 이름");
        }

        // 토큰의 id로 로그인 직원을 찾는다. 없으면 라인을 만들지 않는다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("등록한 직원을 찾을 수 없습니다."));


        Line line = new Line(lineName, request.productionCapacity());
        // NOT NULL인 createdBy에 등록한 직원 이름을 넣는다.
        line.setCreatedBy(user.getName());
        // NOT NULL인 updatedBy도 등록 시점에는 같은 이름으로 채운다.
        line.setUpdatedBy(user.getName());
        return LineDto.Response.from(lineRepository.save(line));
    }

    @Transactional
    public LineDto.Response update(Long lineId, LineDto.UpdateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("수정한 직원을 찾을 수 없습니다."));
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));

        String lineName = request.lineName() == null ? "" : request.lineName().trim();
        // 라인 이름을 변경했는지 여부
        boolean nameChanged = !line.getLineName().equals(lineName);
        // 생산량을 수정했는지 여부
        boolean capacityChanged = line.getProductionCapacity() != request.productionCapacity();

        if (nameChanged && capacityChanged) {
            line.changeName(lineName);
            // 변경 전은 현재 라인 수량, 변경 후는 request.productionCapacity()로 이력에 저장된다.
            lineCapacityLogRepository.save(new LineCapacityLog(line, request.productionCapacity(), request.reason(), user));
            // 이력에 변경 후 수량이 들어간 뒤에 라인 생산량을 바꾼다.
            line.editProduction(request.productionCapacity());
            // 
            line.setUpdatedBy(user.getName());
        } else if (nameChanged) {
            line.changeName(lineName);
        } else if (capacityChanged) {
            // 변경 후 수량은 라인 수정 전에 요청값으로 이력에 저장된다.
            lineCapacityLogRepository.save(new LineCapacityLog(line, request.productionCapacity(), request.reason(), user));
            line.editProduction(request.productionCapacity());
        }
        return LineDto.Response.from(line);
    }

    public LineDto.Response changeStatus(Long lineId, LineDto.UpdateStatusRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("수정한 직원을 찾을 수 없습니다."));
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));

        // 현재 상태와 변경 요청 상태가 동일한경우
        LineStatus beforeStatus = line.getStatus();
        if (beforeStatus == request.status()) {
            throw new IllegalArgumentException("이미 같은 상태입니다.");
        }
        line.changeStatus(request.status()); // 요청에 맞도록 상태 변형
        line.setUpdatedBy(user.getName()); // 상태변경한 직원 이름
        statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.LINE, // 라인 타입으로 등록
                line.getId(),       // 라인 번호
                beforeStatus.name(), // 변경전 상태
                request.status().name(), // 변경후 상태
                user)); // 변경한 직원
        return LineDto.Response.from(line);
    }
}
