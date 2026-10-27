package net.likelion.bebc25.prodflow.commoncode.repository;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.enums.GroupCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    // 기종 목록 조회 메서드 (codeGroup.groupCode + status)
    List<CommonCode> findByCodeGroup_GroupCodeAndStatus(
            GroupCode groupCode,
            CodeStatus status
    );
}
