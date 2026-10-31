package net.likelion.bebc25.prodflow.productionline;

import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto.CreateRequest;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto.Response;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto.UpdateRequest;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto.UpdateStatusRequest;
import net.likelion.bebc25.prodflow.productionline.entity.LineCapacityLog;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineCapacityLogRepository;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.productionline.serivce.LineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional // 테스트가 끝나면 롤백해서 시드 라인 5건을 유지한다
class LineServiceTest {

    @Autowired
    LineService lineService;

    @Autowired
    LineRepository lineRepository;

    @Autowired
    LineCapacityLogRepository lineCapacityLogRepository;

    @Autowired
    StatusHistoryRepository statusHistoryRepository;

    @Test
    @DisplayName("라인 등록 시 이름, 생산량, 초기 상태 AVAILABLE이 저장된다")
    void createLine() {
        // data.sql에 없는 이름. 화면 기본 생산량은 500이다.
        CreateRequest request = new CreateRequest("F라인", 500);

        // 시드 직원 id 1의 이름은 서도윤이다. 토큰 없이 서비스에 id를 직접 넘긴다.
        Response created = lineService.create(request, 1L);

        // IDENTITY라서 INSERT 후 id가 채워져야 한다.
        assertThat(created.id()).isNotNull();
        assertThat(created.lineName()).isEqualTo("F라인");
        assertThat(created.productionCapacity()).isEqualTo(500);
        // 엔티티 기본값이 AVAILABLE이다. 요청에는 상태가 없다.
        assertThat(created.status()).isEqualTo(LineStatus.AVAILABLE);
        // 도입 일시는 createdAt으로 자동 기록된다.
        assertThat(lineRepository.findById(created.id())).isPresent();
        assertThat(lineRepository.findById(created.id()).orElseThrow().getCreatedAt()).isNotNull();
        // 토큰 id 1의 User.name이 작성자로 저장된다.
        assertThat(lineRepository.findById(created.id()).orElseThrow().getCreatedBy()).isEqualTo("서도윤");

        // 시드 5건 + 방금 등록 1건
        assertThat(lineRepository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("이름만 바꾸면 생산량과 이력은 그대로다")
    void updateNameOnly() {
        // 시드 A라인의 생산량 1000은 유지한다. 이력은 생산량이 바뀔 때만 생긴다.
        UpdateRequest request = new UpdateRequest("A라인-개정", 1000, null);
        long logCount = lineCapacityLogRepository.count();

        // 직원 1은 서도윤. 컨트롤러가 토큰에서 꺼낼 id를 테스트에서는 직접 넘긴다.
        Response updated = lineService.update(1L, request, 1L);

        assertThat(updated.lineName()).isEqualTo("A라인-개정");
        assertThat(updated.productionCapacity()).isEqualTo(1000);
        // 상태 변경 API가 아니므로 시드 상태 OPERATING이 유지된다.
        assertThat(updated.status()).isEqualTo(LineStatus.OPERATING);
        assertThat(lineCapacityLogRepository.count()).isEqualTo(logCount);
        // 이름만 바꾸는 분기는 setUpdatedBy를 호출하지 않아 시드 값 system이 남는다.
        assertThat(lineRepository.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("생산량만 바꾸면 변경 전 수량은 현재 라인 값, 변경 후 수량은 요청값으로 이력에 남는다")
    void updateCapacityOnly() {
        UpdateRequest request = new UpdateRequest("A라인", 800, "설비 점검으로 감축");

        Response updated = lineService.update(1L, request, 1L);

        assertThat(updated.lineName()).isEqualTo("A라인");
        assertThat(updated.productionCapacity()).isEqualTo(800);

        // 시드 이력은 이 라인의 변경 후 수량이 800인 건이 없다.
        LineCapacityLog saved = lineCapacityLogRepository.findAll().stream()
                .filter(log -> log.getLine().getId().equals(1L) && log.getAfterCapacity() == 800)
                .findFirst()
                .orElseThrow();
        // 로그를 editProduction보다 먼저 만들어 변경 전은 시드 수량 1000이다.
        assertThat(saved.getBeforeCapacity()).isEqualTo(1000);
        assertThat(saved.getAfterCapacity()).isEqualTo(800);
        assertThat(saved.getReason()).isEqualTo("설비 점검으로 감축");
        // 이력의 담당 직원은 이름 문자열이 아니라 토큰 id의 User다.
        assertThat(saved.getChangedBy().getId()).isEqualTo(1L);
        assertThat(saved.getChangedBy().getName()).isEqualTo("서도윤");
        // 생산량만 바꾸는 분기도 setUpdatedBy를 호출하지 않는다.
        assertThat(lineRepository.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("이름과 생산량을 같이 바꾸면 이력과 라인 수정자 이름이 같이 저장된다")
    void updateNameAndCapacity() {
        UpdateRequest request = new UpdateRequest("A라인-확장", 1200, "설비 증설");

        Response updated = lineService.update(1L, request, 1L);

        assertThat(updated.lineName()).isEqualTo("A라인-확장");
        assertThat(updated.productionCapacity()).isEqualTo(1200);
        assertThat(lineRepository.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("서도윤");

        LineCapacityLog saved = lineCapacityLogRepository.findAll().stream()
                .filter(log -> log.getLine().getId().equals(1L) && log.getAfterCapacity() == 1200)
                .findFirst()
                .orElseThrow();
        assertThat(saved.getBeforeCapacity()).isEqualTo(1000);
        assertThat(saved.getReason()).isEqualTo("설비 증설");
        assertThat(saved.getChangedBy().getName()).isEqualTo("서도윤");
    }

    @Test
    @DisplayName("없는 라인을 수정하면 400으로 매핑될 예외가 난다")
    void updateMissingLine() {
        UpdateRequest request = new UpdateRequest("없는라인", 1000, null);

        assertThatThrownBy(() -> lineService.update(999L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("라인을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("없는 직원 id로 수정하면 400으로 매핑될 예외가 난다")
    void updateMissingUser() {
        UpdateRequest request = new UpdateRequest("A라인", 1000, null);

        assertThatThrownBy(() -> lineService.update(1L, request, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정한 직원을 찾을 수 없습니다.");
    }
    
    @Test
    @DisplayName("라인 상태를 바꾸면 enum 이름이 status_history에 남는다")
    void changeLineStatus() {
        // 시드 A라인은 OPERATING이다. 직원 1의 이름은 서도윤이다.
        Response updated = lineService.changeStatus(1L, new UpdateStatusRequest(LineStatus.INSPECTION), 1L);

        assertThat(updated.status()).isEqualTo(LineStatus.INSPECTION);
        assertThat(lineRepository.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("서도윤");

        StatusHistory saved = statusHistoryRepository.findAll().stream()
                .filter(history -> history.getEntity() == HistoryEntityType.LINE
                        && history.getEntityId().equals(1L)
                        && "INSPECTION".equals(history.getToStatus()))
                .findFirst()
                .orElseThrow();
        // name() 결과가 이력의 변경 전, 변경 후 문자열이다.
        assertThat(saved.getFromStatus()).isEqualTo("OPERATING");
        assertThat(saved.getToStatus()).isEqualTo("INSPECTION");
        assertThat(saved.getUserName()).isEqualTo("서도윤");
        assertThat(saved.getUser().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 라인 상태로 바꾸면 이력을 남기지 않는다")
    void changeLineStatusToSameValue() {
        long historyCount = statusHistoryRepository.count();

        assertThatThrownBy(() -> lineService.changeStatus(1L, new UpdateStatusRequest(LineStatus.OPERATING), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 같은 상태입니다.");
        assertThat(statusHistoryRepository.count()).isEqualTo(historyCount);
    }
}