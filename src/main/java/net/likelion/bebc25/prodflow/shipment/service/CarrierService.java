package net.likelion.bebc25.prodflow.shipment.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.shipment.dto.CarrierDto;
import net.likelion.bebc25.prodflow.shipment.repository.CarrierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarrierService {

    private final CarrierRepository carrierRepository;

    // 출고 화면 택배사 셀렉트용. DB에 등록된 택배사만 내려준다.
    public List<CarrierDto.Response> findAll() {
        List<CarrierDto.Response> responses = new ArrayList<>();
        for (var carrier : carrierRepository.findAllByOrderByCodeAsc()) {
            responses.add(CarrierDto.Response.from(carrier));
        }
        return responses;
    }
}
