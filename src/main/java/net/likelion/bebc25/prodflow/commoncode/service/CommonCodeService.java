package net.likelion.bebc25.prodflow.commoncode.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.commoncode.dto.CommonCodeDto;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CodeGroupRepository;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommonCodeService {

    private static final String DEVICE_GROUP = "DEVICE";

    private final CommonCodeRepository commonCodeRepository;
    private final CodeGroupRepository codeGroupRepository;

    public List<CommonCodeDto.Response> getActiveByGroupCode(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw new IllegalArgumentException("groupCode는 필수입니다.");
        }
        String trimmed = groupCode.trim();
        List<CommonCode> codes = commonCodeRepository
                .findActiveByGroup(trimmed, CodeStatus.ACTIVE);

        List<CommonCodeDto.Response> responses = new ArrayList<>();
        for (CommonCode code : codes) {
            responses.add(CommonCodeDto.Response.from(code));
        }
        return responses;
    }

    @Transactional
    public CommonCode resolveOrCreateDevice(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            throw new IllegalArgumentException("기종명은 필수입니다.");
        }
        String normalized = normalizeName(deviceName);

        List<CommonCode> existing = commonCodeRepository
                .findActiveByGroupAndName(DEVICE_GROUP, CodeStatus.ACTIVE, normalized);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }

        CodeGroup deviceGroup = codeGroupRepository.findByGroupCode(DEVICE_GROUP)
                .orElseThrow(() -> new EntityNotFoundException("DEVICE 코드 그룹을 찾을 수 없습니다."));

        List<CommonCode> devices = commonCodeRepository
                .findActiveByGroup(DEVICE_GROUP, CodeStatus.ACTIVE);

        int nextSortOrder = 1;
        if (!devices.isEmpty()) {
            nextSortOrder = devices.getLast().getSortOrder() + 1;
        }

        String code = "DEV" + nextSortOrder;
        CommonCode created = CommonCode.create(deviceGroup, code, normalized, nextSortOrder);
        return commonCodeRepository.save(created);
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
