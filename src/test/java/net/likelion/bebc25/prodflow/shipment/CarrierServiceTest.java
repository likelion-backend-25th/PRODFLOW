package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.shipment.dto.CarrierDto;
import net.likelion.bebc25.prodflow.shipment.entity.Carrier;
import net.likelion.bebc25.prodflow.shipment.repository.CarrierRepository;
import net.likelion.bebc25.prodflow.shipment.service.CarrierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierServiceTest {

    @Mock
    CarrierRepository carrierRepository;
    @InjectMocks
    CarrierService carrierService;

    @Test
    @DisplayName("등록된 택배사 code/name 목록을 코드 순으로 반환한다")
    void findAll() {
        Carrier cj = mock(Carrier.class);
        Carrier post = mock(Carrier.class);
        when(cj.getCode()).thenReturn("04");
        when(cj.getName()).thenReturn("CJ대한통운");
        when(post.getCode()).thenReturn("01");
        when(post.getName()).thenReturn("우체국택배");
        when(carrierRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(post, cj));

        List<CarrierDto.Response> responses = carrierService.findAll();

        assertThat(responses).containsExactly(
                new CarrierDto.Response("01", "우체국택배"),
                new CarrierDto.Response("04", "CJ대한통운")
        );
    }
}
