package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationService {
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final QuotationItemService quotationItemService;
    private final QuotationScheduleService scheduleService;

    // 견적 초안 저장
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationDto.DetailResponse create(@NotNull @Valid QuotationDto.CreateRequest request, Long userId) {
        // 작성자 조회 및 계정 상태 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("작성자 계정을 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("사용 중인 계정만 견적을 작성할 수 있습니다.");
        }
        if (user.isMustChangePassword()) {
            throw new AccessDeniedException("비밀번호를 먼저 변경해주세요.");
        }

        // 입력값 검증
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (request.requestedDueDate().isBefore(today)) {
            throw new IllegalArgumentException("희망 납기는 오늘 이후여야 합니다.");
        }
        // 유효기간 검증
        LocalDate validUntil = request.validUntil();
        if (validUntil == null) {
            validUntil = today.plusDays(14);
        }
        if (!validUntil.equals(today.plusDays(7)) && !validUntil.equals(today.plusDays(14))) {
            throw new IllegalArgumentException("견적 유효기간은 발행일로부터 7일 또는 14일이어야 합니다.");
        }
        // 품목별 VAT·원 단위 반올림 적용 후 총액 합산
        long totalAmount = calculateTotalAmount(request.items(), request.vatIncluded());

        // 납기 계산에 사용할 전체 품목 수량 합산
        long totalQuantity = 0;
        for (QuotationItemDto.CreateRequest item : request.items()) {
            totalQuantity += item.quantity();
        }

        // 최종 저장 시 납기 재계산
        QuotationScheduleResult schedule = scheduleService.calculate(totalQuantity, request.requestedDueDate(), today);

        // 거래처 조회. 신규 거래처면 이 트랜잭션 안에서 함께 저장
        Client client = findOrCreateClient(request, user);

        // NOT NULL 제약을 지키기 위해 임시 번호로 저장한 뒤, 생성된 ID로 번호를 확정
        String quotationNo = createQuotationNo(today);

        // 견적 엔티티 생성
        Quotation quotation = request.toEntity(quotationNo, client, user, schedule.estimatedDueDate(),
                schedule.plannedStartDate(), schedule.plannedEndDate(), validUntil, totalAmount);

        // 견적서 저장
        Quotation savedQuotation = quotationRepository.save(quotation);

        // quotationNo를 견적서 id로 변경하여 적용
        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        // 저장된 엔티티의 변경은 JPA 변경 감지로 반영되므로 save를 다시 호출안함.
        savedQuotation.changeQuotationNo("QT-" + date + "-" + savedQuotation.getId());

        // 품목을 저장. 품목 저장이 실패하면 견적과 신규 거래처 저장도 취소.
        List<QuotationItem> savedItems = quotationItemService.create(savedQuotation, request.items());

        // 견적번호·품목 변경 DB 반영 및 감사 필드 갱신
        quotationRepository.flush();

        // 금액을 계산하고 엔티티를 응답 DTO로 변환
        return toDetailResponse(savedQuotation, savedItems);
    }

    // 견적 초안 수정
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationDto.DetailResponse updateDraft(
            Long quotationId, @NotNull @Valid QuotationDto.CreateRequest request, Long userId
    ) {
        // 수정자 계정을 조회하고 사용 상태,최초 비밀번호 변경 완료 여부를 확인
        User editor = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("수정자 계정을 찾을 수 없습니다."));

        if (editor.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("사용 중인 계정만 견적을 수정할 수 있습니다.");
        }
        if (editor.isMustChangePassword()) {
            throw new AccessDeniedException("비밀번호를 먼저 변경해주세요.");
        }

        // 같은 견적의 수정이 겹치지 않도록 트랜잭션이 끝날 때까지 견적 행에 쓰기 잠금을 건다.
        Quotation quotation = quotationRepository.findByIdForUpdate(quotationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + quotationId));

        // 발송을 확정한 견적은 일반 수정 대상이 아니므로 초안만 허용
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalArgumentException("초안 상태의 견적만 수정할 수 있습니다.");
        }

        // 한국 날짜 기준으로 과거의 희망 납기는 거절
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (request.requestedDueDate().isBefore(today)) {
            throw new IllegalArgumentException("희망 납기는 오늘 이후여야 합니다.");
        }

        // 현재 PDF의 발행일은 최초 작성일이므로 수정 시에도 같은 날짜를 기준으로 검증
        LocalDate issuedDate = quotation.getCreatedAt().toLocalDate();

        // 유효기간을 생략하면 기존 값을 유지하고, 입력하면 발행일 기준 7일,14일만 허용
        LocalDate validUntil = request.validUntil() == null ? quotation.getValidUntil() : request.validUntil();

        if (!validUntil.equals(issuedDate.plusDays(7)) && !validUntil.equals(issuedDate.plusDays(14))) {
            throw new IllegalArgumentException("견적 유효기간은 발행일로부터 7일 또는 14일이어야 합니다.");
        }

        // 수정된 품목의 수량,단가,VAT로 총액과 납기 계산에 사용할 전체 수량을 다시 계산
        long totalAmount = calculateTotalAmount(request.items(), request.vatIncluded());
        long totalQuantity = request.items().stream().mapToLong(QuotationItemDto.CreateRequest::quantity).sum();

        // 수정 시점의 생산 조건으로 납기를 계산. 불가능하면 수정 트랜잭션을 롤백한다.
        QuotationScheduleResult schedule = scheduleService.calculate(totalQuantity, request.requestedDueDate(), today);
        // 기존 거래처를 연결하거나 신규 거래처를 같은 수정 트랜잭션에서 등록한다.
        Client client = findOrCreateClient(request, editor);

        // 견적 ID,번호,최초 작성자는 유지하고 거래처 스냅샷·일정·금액 등 초안 내용만 갱신
        quotation.changeDraft(client, client.getEmployee(), request.requestedDueDate(), schedule.estimatedDueDate(),
                schedule.plannedStartDate(), schedule.plannedEndDate(), validUntil, request.vatIncluded(), totalAmount);

        // 요청한 품목으로 교체. 실패하면 견적 변경과 신규 거래처 등록도 취소
        List<QuotationItem> items = quotationItemService.replaceDraftItems(quotation, request.items());

        // 변경 사항을 DB에 반영하고 감사 시각을 갱신한다. 실제 커밋은 서비스 메서드 반환 시 수행된다.
        quotationRepository.flush();

        // 커밋 후 PDF를 만들 수 있도록 수정된 견적과 품목의 상세 정보를 반환
        return toDetailResponse(quotation, items);
    }

    // 견적 총액 계산
    private long calculateTotalAmount(List<QuotationItemDto.CreateRequest> items, boolean vatIncluded) {
        try {
            long totalAmount = 0;
            for (QuotationItemDto.CreateRequest item : items) {
                long lineAmount = QuotationItem.calculateAmount(item.quantity(), item.unitPrice(), vatIncluded);
                totalAmount = Math.addExact(totalAmount, lineAmount);
            }
            return totalAmount;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("견적 총액이 허용 범위를 초과했습니다.", e);
        }
    }

    // 금액 계산 및 견적 상세 응답 변환
    private QuotationDto.DetailResponse toDetailResponse(Quotation quotation, List<QuotationItem> items) {
        try {
            long totalAmount = quotation.getTotalAmount(); // 저장된 부가세 포함 총액
            long supplyAmount = 0; // 부가세 제외 공급가액
            for (QuotationItem item : items) {
                // 입력 단가에 부가세가 포함되지 않은 경우 공급가액 계산
                if (!quotation.isVatIncluded()) {
                    long itemSupplyAmount = Math.multiplyExact(item.getUnitPrice(), (long) item.getQuantity());
                    supplyAmount = Math.addExact(supplyAmount, itemSupplyAmount);
                }
            }

            // 입력 단가가 부가세 포함이면 총액 ÷ 1.1로 공급가액 계산
            if (quotation.isVatIncluded()) {
                // 부가세가 포함된 총액에서 공급가를 계산. HALF_UP은 원 단위 반올림.
                supplyAmount = BigDecimal.valueOf(totalAmount)
                        .divide(new BigDecimal("1.1"), 0, RoundingMode.HALF_UP)
                        .longValueExact();
            }
            // 부가세 = 총액 - 공급가액
            long vatAmount = totalAmount - supplyAmount;

            // 계약금 = 총액의 50% (원 단위 반올림)
            long depositAmount = BigDecimal.valueOf(totalAmount)
                    .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                    .longValueExact();

            // 잔금 = 총액 - 계약금
            long balanceAmount = totalAmount - depositAmount;

            // 견적 정보, 품목 목록, 계산한 금액을 상세 응답 DTO로 변환
            return QuotationDto.DetailResponse.from(quotation, items, null,
                    supplyAmount, vatAmount, totalAmount, depositAmount, balanceAmount);

        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("견적 총액이 허용 범위를 초과했습니다.", ex);
        }
    }

    // 기존 거래처 조회 또는 신규 등록
    private Client findOrCreateClient(QuotationDto.CreateRequest request, User user) {
        // 기존 거래처 ID와 신규 거래처 정보가 모두 없으면 예외 발생
        if (request.clientId() == null && request.newClient() == null) {
            throw new IllegalArgumentException("기존 거래처 ID 또는 신규 거래처 정보를 입력해주세요.");
        }
        // 기존 거래처 선택과 신규 거래처 등록을 동시에 요청하면 예외 발생
        if (request.clientId() != null && request.newClient() != null) {
            throw new IllegalArgumentException("기존 거래처 ID 또는 신규 거래처 정보 중 하나만 입력해주세요.");
        }
        // 기존 거래처를 선택한 경우 ID로 조회하여 반환
        if (request.clientId() != null) {
            return clientRepository.findById(request.clientId())
                    .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));
        }
        // 신규 거래처 정보에서 회사명이 같은 기존 거래처 조회 (ID 오름차순 첫 번째)
        QuotationDto.ClientRequest newClient = request.newClient();
        Optional<Client> existingClient = clientRepository.findFirstByCompanyOrderByIdAsc(newClient.company());
        // 같은 회사명의 거래처가 있으면 새로 등록하지 않고 기존 거래처 반환
        if (existingClient.isPresent()) {
            return existingClient.get();
        }

        // 같은 회사명의 거래처가 없으면 입력 정보와 담당 사용자로 신규 거래처 생성
        Client client = new Client(newClient.company(), newClient.employee(),
                newClient.email(), newClient.phone(), newClient.toAddress(), user);

        // 신규 거래처를 DB에 저장하고 반환
        return clientRepository.save(client);
    }

    // 저장 전 임시 견적번호 생성
    private String createQuotationNo(LocalDate today) {
        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        return "QT-" + date + "-" + UUID.randomUUID();
    }

    // 견적 목록 조회
    public PageResponse<QuotationDto.Response> getQuotationList(
            String keyword, QuotationStatus status, @NotNull QuotationSortType sortType, Pageable pageable) {
        return getQuotationList(keyword, status, sortType, pageable, false);
    }

    // 기존 검색·정렬·페이징에 수주 대기 조건을 함께 적용
    public PageResponse<QuotationDto.Response> getQuotationList(
            String keyword, QuotationStatus status, @NotNull QuotationSortType sortType, Pageable pageable,
            boolean orderPending) {
        // 요청의 sort 대신 허용된 날짜 기준·방향 적용
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortType.toSort());

        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<Quotation> quotations = quotationRepository.search(
                normalizedKeyword, status, orderPending, sortedPageable);

        // 현재 페이지에 포함된 견적서 ID 추출
        List<Long> quotationIds = quotations.getContent().stream()
                .map(Quotation::getId)
                .toList();

        // 해당 견적서들에 연결된 수주를 한 번에 조회하고, 견적 ID를 키로 저장
        Map<Long, CustomerOrder> orders = findOrdersByQuotationIds(quotationIds);

        // 각 견적서와 연결 수주를 응답 DTO로 변환하면서 페이징 정보 유지
        // 연결된 수주가 없으면 orders.get()은 null을 반환
        Page<QuotationDto.Response> responsePage = quotations.map(quotation -> QuotationDto.Response.from(
                quotation,
                orders.get(quotation.getId())
        ));

        // DTO 페이지를 공통 페이지 응답 형식으로 변환
        return PageResponse.from(responsePage);
    }

    // 견적별 연결 수주 조회
    private Map<Long, CustomerOrder> findOrdersByQuotationIds(List<Long> quotationIds) {
        if (quotationIds.isEmpty()) {
            return Map.of();
        }
        return customerOrderRepository
                .findByQuotationIdIn(quotationIds)
                .stream()
                .collect(Collectors.toMap(
                        order -> order.getQuotation().getId(), // 키: 견적 ID
                        order -> order // 값: 연결된 수주 엔티티
                ));
    }

    // 견적 상세 조회
    public QuotationDto.DetailResponse getQuotationById(Long id) {
        Quotation quotation = quotationRepository.findDetailById(id)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + id));

        // 견적에 포함된 품목을 ID 오름차순으로 조회
        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderByIdAsc(id);

        // 엔티티와 품목 목록을 상세 응답 DTO로 변환
        return toDetailResponse(quotation, items);
    }
}
