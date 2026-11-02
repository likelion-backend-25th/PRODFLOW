package net.likelion.bebc25.prodflow.quotation.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationService {
    private final QuotationRepository quotationRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final QuotationItemService quotationItemService;
    private final QuotationScheduleService scheduleService;

    @Transactional
    public QuotationDto.DetailResponse create(QuotationDto.CreateRequest request, Long userId) {
        // 작성자 조회 및 견적 작성 권한을 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "작성자 계정을 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용 중인 계정만 견적을 작성할 수 있습니다.");
        }
        // 비밀번호 변경 안해도 이용가능한지 확인 필요
        if (user.isMustChangePassword()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비밀번호를 먼저 변경해주세요.");
        }
        if (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ORDER_MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "견적 작성 권한이 없습니다.");
        }

        // 입력값 검증
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (request.requestedDueDate() == null || request.requestedDueDate().isBefore(today)) {
            throw new IllegalArgumentException("희망 납기는 오늘 이후여야 합니다.");
        }
        // 유효기간 검증
        LocalDate validUntil = request.validUntil();
        if (validUntil == null) {
            validUntil = today.plusDays(14);
        }
        if (validUntil.isBefore(today.plusDays(7)) || validUntil.isAfter(today.plusDays(14))) {
            throw new IllegalArgumentException("견적 유효기간은 발행일로부터 7~14일이어야 합니다.");
        }
        if (request.contactName() == null || request.contactName().isBlank()) {
            throw new IllegalArgumentException("거래처 담당자명을 입력해주세요.");
        }
        if (request.items() == null) {
            throw new IllegalArgumentException("견적 품목을 입력해주세요.");
        }

        // 견적 품목 입력값 및 중복 여부 검증
        quotationItemService.validateItems(request.items());

        // 납기 계산에 사용할 전체 품목 수량 합산
        long totalQuantity = 0;
        for (QuotationItemDto.CreateRequest item : request.items()) {
            totalQuantity += item.quantity();
        }

        /**
         *  납기일 계산
         */
        QuotationScheduleService.Schedule schedule = scheduleService.calculate(totalQuantity, request.requestedDueDate(), today);

        // 거래처 조회. 신규 거래처면 이 트랜잭션 안에서 함께 저장
        Client client = findOrCreateClient(request, user);
//        Client client = clientService.findOrCreate(request.clientId(), request.newClient(), user);

        // NOT NULL 제약을 지키기 위해 임시 번호로 저장한 뒤, 생성된 ID로 번호를 확정
        String quotationNo = createQuotationNo(today);

        // 견적 엔티티 생성
        Quotation quotation = request.toEntity(quotationNo, client, user, schedule.estimatedDueDate(),
                schedule.plannedStartDate(), schedule.plannedEndDate(), validUntil);

        // 견적에 거래처 정보 저장
        quotation.snapshotContact(request.contactName(), client.getEmail(), client.getPhone(), client.getAddress());
        // 견적서 저장
        Quotation savedQuotation = quotationRepository.save(quotation);

        // quotationNo를 견적서 id로 변경하여 적용
        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        // 저장된 엔티티의 변경은 JPA 변경 감지로 반영되므로 save를 다시 호출안함.
        savedQuotation.changeQuotationNo("QT-" + date + "-" + savedQuotation.getId());

        // 품목을 저장. 품목 저장이 실패하면 견적과 신규 거래처 저장도 취소.
        List<QuotationItem> savedItems = quotationItemService.create(savedQuotation, request.items());

        // 금액을 계산하고 엔티티를 응답 DTO로 변환
        return createResponse(savedQuotation, savedItems);
    }

    // 견적 금액과 계약금·잔금을 계산한 뒤 상세 응답 DTO 생성
    private QuotationDto.DetailResponse createResponse(Quotation quotation, List<QuotationItem> items) {
        try {
            long totalAmount = 0; // 부가세 포함 금액
            long supplyAmount = 0; // 부가세 제외 공급가액
            for (QuotationItem item : items) {
                // 품목별 최종 금액을 총액에 합산 (long 범위 초과 시 예외 발생)
                totalAmount = Math.addExact(totalAmount, item.getLineAmount());

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

            // 영속성 컨텍스트의 변경 사항을 DB에 반영 (트랜잭션 커밋은 아님)
            quotationRepository.flush();

            // 견적 정보, 품목 목록, 계산한 금액을 상세 응답 DTO로 변환
            return QuotationDto.DetailResponse.from(quotation, items, null,
                    supplyAmount, vatAmount, totalAmount, depositAmount, balanceAmount);

        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("견적 총액이 허용 범위를 초과했습니다.", ex);
        }
    }

    // 기존 거래처를 조회하거나 신규 거래처 등록
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
        Client client = new Client(newClient.company(), newClient.representativeName(),
                newClient.email(), newClient.phone(), newClient.toAddress(), user);

        // 신규 거래처를 DB에 저장하고 반환
        return clientRepository.save(client);
    }

    private String createQuotationNo(LocalDate today) {
        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        return "QT-" + date + "-" + UUID.randomUUID();
    }
}
