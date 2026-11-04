package net.likelion.bebc25.prodflow.commoncode.repository;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    // 작업지시 등 기존 사용
    List<CommonCode> findByCodeGroup_GroupCodeAndStatus(
            String groupCode,
            CodeStatus status
    );

    // 그룹과 코드가 같고 사용 중인 공통코드 한 건
    Optional<CommonCode> findByCodeGroup_GroupCodeAndCodeAndStatus(
            String groupCode,
            String code,
            CodeStatus status
    // 드롭다운 목록
    @Query("""
            SELECT c FROM CommonCode c
            WHERE c.codeGroup.groupCode = :groupCode
              AND c.status = :status
            ORDER BY c.sortOrder ASC
            """)
    List<CommonCode> findActiveByGroup(
            @Param("groupCode") String groupCode,
            @Param("status") CodeStatus status
    );

    // 같은 기종명 찾기 (재사용)
    @Query("""
            SELECT c FROM CommonCode c
            WHERE c.codeGroup.groupCode = :groupCode
              AND c.status = :status
              AND LOWER(c.name) = LOWER(:name)
            ORDER BY c.sortOrder ASC
            """)
    List<CommonCode> findActiveByGroupAndName(
            @Param("groupCode") String groupCode,
            @Param("status") CodeStatus status,
            @Param("name") String name
    );
}
