package net.likelion.bebc25.prodflow.productionline.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto;
import net.likelion.bebc25.prodflow.productionline.serivce.LineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("api/v1/lines")
@RequiredArgsConstructor // final 받는 필드를 자동 생성
public class LineController {

    private final LineService lineService; // 등록 로직은 서비스

    
    @PostMapping
    public ResponseEntity<LineDto.Response> create(
            @RequestBody LineDto.CreateRequest request // 이름, 생산 능력, 도입일
    ) {
        LineDto.Response response = lineService.create(request); // 서비스 반환값을 저장
        return ResponseEntity.created(URI.create("api/v1/lines" + response.id())).body(response);
    }

}
