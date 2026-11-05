package net.likelion.bebc25.prodflow.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final CustomerOrderRepository customerOrderRepository;

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORDER_MANAGER')")
    public ClientDto.DetailResponse createClient(ClientDto.Request request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 사용자입니다. id = " + request.userId()));

        Client client = clientRepository.save(request.toEntity(user));

        Long totalOrder = customerOrderRepository.countByClientId(client.getId());
        Long totalAmount = customerOrderRepository.sumTotalAmountByClientId(client.getId());
        Long ongoingOrders = customerOrderRepository.countActiveOrdersByClientId(client.getId());

        return ClientDto.DetailResponse.from(client, totalOrder, totalAmount, ongoingOrders);
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORDER_MANAGER')")
    public List<ClientDto.ImportResponse> registerClients(MultipartFile multipartFile) {
        if (multipartFile == null) {
            throw new IllegalArgumentException("파일이 존재하지 않습니다.");
        }
        if (!multipartFile.getOriginalFilename().endsWith(".xlsx")
                && !multipartFile.getOriginalFilename().endsWith(".xls")) {
            throw new IllegalArgumentException("엑셀 파일(.xlsx, .xls)이 아닙니다.");
        }

        List<ClientDto.ImportResponse> list = new ArrayList<>();

        try {
            Workbook workbook = WorkbookFactory.create(multipartFile.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            if (!isValidExcel(headerRow)) {
                throw new IllegalArgumentException("엑셀 파일 형식이 맞지 않습니다.");
            }

            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) {
                Client client = rowToClient(sheet.getRow(i));
                clientRepository.save(client);
                list.add(ClientDto.ImportResponse.from(client.getId(), client.getCompany()));
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("거래처 엑셀 파일을 가져오는 중 오류가 발생했습니다.", e);
        }

        return list;
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORDER_MANAGER')")
    public ClientDto.DetailResponse updateClient(Long clientId, ClientDto.Request request) {
        Long totalOrder = customerOrderRepository.countByClientId(clientId);
        Long totalAmount = customerOrderRepository.sumTotalAmountByClientId(clientId);
        Long ongoingOrders = customerOrderRepository.countActiveOrdersByClientId(clientId);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 거래처 입니다. id = " + clientId));

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 사용자입니다. id = " + request.userId()));

        client.update(
                request.company(),
                request.employee(),
                request.email(),
                request.phone(),
                request.address(),
                user
        );

        return ClientDto.DetailResponse.from(client, totalOrder, totalAmount, ongoingOrders);
    }

    public ClientDto.DetailResponse getClientDetail(Long clientId) {
        Long totalOrder = customerOrderRepository.countByClientId(clientId);
        Long totalAmount = customerOrderRepository.sumTotalAmountByClientId(clientId);
        Long ongoingOrders = customerOrderRepository.countActiveOrdersByClientId(clientId);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 거래처 입니다. id = " + clientId));
        return ClientDto.DetailResponse.from(client, totalOrder, totalAmount, ongoingOrders);
    }

    public Page<ClientDto.Response> findClients(String keyword, Pageable pageable) {
        Page<Client> clients;

        clients = keyword != null
                ? clientRepository.findByKeyword(keyword, pageable)
                : clientRepository.findAll(pageable);

        return clients.map(client -> {
            Long totalOrder = customerOrderRepository.countByClientId(client.getId());
            Long totalAmount = customerOrderRepository.sumTotalAmountByClientId(client.getId());
            LocalDateTime recentOrderedAt = LocalDateTime.now();

            return ClientDto.Response.from(
                    client,
                    totalOrder,
                    totalAmount,
                    recentOrderedAt
            );
        });
    }

    private boolean isValidExcel(Row headerRow) {
        if (!headerRow.getCell(0).getStringCellValue().equals("company")
                || !headerRow.getCell(1).getStringCellValue().equals("employee")
                || !headerRow.getCell(2).getStringCellValue().equals("email")
                || !headerRow.getCell(3).getStringCellValue().equals("phone")
                || !headerRow.getCell(4).getStringCellValue().equals("zipcode")
                || !headerRow.getCell(5).getStringCellValue().equals("road_address")
                || !headerRow.getCell(6).getStringCellValue().equals("detail_address")
                || !headerRow.getCell(7).getStringCellValue().equals("user_id")
                || !headerRow.getCell(8).getStringCellValue().equals("created_at")
                || !headerRow.getCell(9).getStringCellValue().equals("updated_at")
                || !headerRow.getCell(10).getStringCellValue().equals("created_by")
                || !headerRow.getCell(11).getStringCellValue().equals("updated_by")
        ) {
            return false;
        }
        return true;
    }

    private LocalDateTime strToLdt(String str) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(str, formatter);
    }

    private Client rowToClient(Row row) {
        long userId;
        Client client;

        try {
            userId = (long) row.getCell(7).getNumericCellValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("형식에 맞지 않은 데이터가 존재합니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 사용자입니다. id = " + userId));

        try {
            client = new Client(
                    row.getCell(0).getStringCellValue(),
                    row.getCell(1).getStringCellValue(),
                    row.getCell(2).getStringCellValue(),
                    row.getCell(3).getStringCellValue(),
                    new Address(
                            row.getCell(4).getStringCellValue(),
                            row.getCell(5).getStringCellValue(),
                            row.getCell(6).getStringCellValue()
                    ),
                    user
            );
            String createdAt = row.getCell(8).getStringCellValue();
            String updatedAt = row.getCell(9).getStringCellValue();
            String createdBy = row.getCell(10).getStringCellValue();
            String updatedBy = row.getCell(11).getStringCellValue();
            client.setCreatedAt(strToLdt(createdAt.isEmpty() ? null : createdAt));
            client.setUpdatedAt(strToLdt(updatedAt.isEmpty() ? null : updatedAt));
            client.setCreatedBy(createdBy.isEmpty() ? null : createdBy);
            client.setUpdatedBy(updatedBy.isEmpty() ? null : updatedBy);

        } catch (Exception e) {
            throw new IllegalArgumentException("형식에 맞지 않은 데이터가 존재합니다.");
        }

        return client;
    }
}
