package net.likelion.bebc25.prodflow.productionline;

import net.likelion.bebc25.prodflow.productionline.dto.LineDto.CreateRequest;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto.Response;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.productionline.serivce.LineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // 테스트가 끝나면 롤백해서 시드 라인 5건을 유지한다
class LineServiceTest {

    @Autowired
    LineService lineService;

    @Autowired
    LineRepository lineRepository;

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
}