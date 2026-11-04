package net.likelion.bebc25.prodflow.commoncode.repository;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    // 기종 목록 조회 메서드 (codeGroup.groupCode + status)
    List<CommonCode> findByCodeGroup_GroupCodeAndStatus(
            String groupCode,
            CodeStatus status
    );

    // 그룹과 코드가 같고 사용 중인 공통코드 한 건
    Optional<CommonCode> findByCodeGroup_GroupCodeAndCodeAndStatus(
            String groupCode,
            String code,
            CodeStatus status
    );
}
