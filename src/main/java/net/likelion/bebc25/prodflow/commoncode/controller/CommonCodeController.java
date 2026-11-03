package net.likelion.bebc25.prodflow.commoncode.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.commoncode.dto.CommonCodeDto;
import net.likelion.bebc25.prodflow.commoncode.service.CommonCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/common-codes")
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    @GetMapping
    public ResponseEntity<List<CommonCodeDto.Response>> getActiveByGroupCode(
            @RequestParam String groupCode
    ) {
        return ResponseEntity.ok(commonCodeService.getActiveByGroupCode(groupCode));
    }
}
