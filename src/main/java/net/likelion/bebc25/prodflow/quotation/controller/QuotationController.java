package net.likelion.bebc25.prodflow.quotation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {
    private final QuotationService quotationService;
    private final JwtProvider jwtProvider;

    @PostMapping
    public ResponseEntity<QuotationDto.DetailResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationDto.CreateRequest request
    ) {
        // Long userId = userDetails.getId();
        Long userId = 1L;

        QuotationDto.DetailResponse response = quotationService.create(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/quotations/" + response.id())).body(response);
    }
}
