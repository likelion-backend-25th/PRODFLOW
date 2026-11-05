package net.likelion.bebc25.prodflow.holiday;

import net.likelion.bebc25.prodflow.holiday.client.HolidayApiClient;
import net.likelion.bebc25.prodflow.global.exception.ExternalApiException;
import net.likelion.bebc25.prodflow.holiday.dto.HolidayApiResponse;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.holiday.service.HolidaySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "holiday.initialization.enabled=false",
        "holiday.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:holiday-sync;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,LINE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.show_sql=false",
        "jwt.secret=cXVvdGF0aW9uLXRlc3Qtc2VjcmV0LW11c3QtYmUtMzItYnl0ZXM=",
        "jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=120000"
})
class HolidaySyncServiceTest {
    @Autowired HolidaySyncService service;
    @Autowired HolidayRepository repository;
    @MockitoBean HolidayApiClient client;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void importsPublicHolidaysIncludingWeekendsAndPreservesExistingRowsOnRepeatedImport() {
        repository.saveAndFlush(new Holiday(LocalDate.of(2026, 1, 1), "기존 이름"));
        when(client.fetchHolidays(2026)).thenReturn(List.of(
                holiday("2026-01-01", "새해"),
                holiday("2026-05-05", "어린이날"),
                holiday("2026-05-05", "중복 이름"),
                holiday("2026-08-15", "토요일"),
                holiday("2026-03-01", "일요일"),
                new HolidayApiResponse(LocalDate.of(2026, 5, 1), "공휴일 아님", "N")));

        assertThat(service.importHolidays(2026)).isEqualTo(3);
        assertThat(service.importHolidays(2026)).isZero();
        assertThat(repository.findAll()).hasSize(4).anySatisfy(holiday -> {
            assertThat(holiday.getHolidayDate()).isEqualTo(LocalDate.of(2026, 5, 5));
            assertThat(holiday.getName()).isEqualTo("어린이날");
            assertThat(holiday.getCreatedAt()).isNotNull();
        }).anySatisfy(holiday -> assertThat(holiday.getName()).isEqualTo("기존 이름"));
        assertThat(repository.findAll()).extracting(Holiday::getHolidayDate)
                .contains(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 3, 1))
                .doesNotContain(LocalDate.of(2026, 5, 1));
    }

    @Test
    void apiFailurePreservesExistingData() {
        repository.saveAndFlush(new Holiday(LocalDate.of(2026, 1, 1), "새해"));
        when(client.fetchHolidays(2026)).thenThrow(new ExternalApiException("API 실패"));
        assertThatThrownBy(() -> service.importHolidays(2026)).isInstanceOf(ExternalApiException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void invalidResponsePreventsPartialImport() {
        for (HolidayApiResponse invalid : List.of(
                holiday("2027-01-01", "다른 연도"),
                holiday("2026-05-05", " "),
                new HolidayApiResponse(LocalDate.of(2026, 1, 1), "잘못된 휴일 여부", "UNKNOWN"))) {
            when(client.fetchHolidays(2026)).thenReturn(List.of(holiday("2026-05-05", "어린이날"), invalid));
            assertThatThrownBy(() -> service.importHolidays(2026)).isInstanceOf(ExternalApiException.class);
            assertThat(repository.count()).isZero();
        }
    }

    private HolidayApiResponse holiday(String date, String name) {
        return new HolidayApiResponse(LocalDate.parse(date), name, "Y");
    }
}
