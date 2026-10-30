package net.likelion.bebc25.prodflow.quotation.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationService {
    private final QuotationRepository quotationRepository;

    public QuotationDto.DetailResponse save(QuotationDto.CreateRequest request) {

        return QuotationDto.DetailResponse.from(
                Quotation quotation,
                List< QuotationItem > items,
                CustomerOrder customerOrder,
                Long supplyAmount,
                Long vatAmount,
                Long totalAmount,
                Long depositAmount,
                Long balanceAmount);
    }
}
