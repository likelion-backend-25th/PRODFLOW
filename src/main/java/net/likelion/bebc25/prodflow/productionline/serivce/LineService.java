package net.likelion.bebc25.prodflow.productionline.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
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

        // 검증에 쓴 공백 제거 이름으로 저장
        Line line = new Line(lineName, request.productionCapacity());
        // created_by, updated_by에 User.name을 넣음
        line.recordWriter(user.getName());
        return LineDto.Response.from(lineRepository.save(line));
    }
}
