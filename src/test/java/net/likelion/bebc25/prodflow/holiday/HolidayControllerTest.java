package net.likelion.bebc25.prodflow.holiday;

import net.likelion.bebc25.prodflow.holiday.service.HolidaySyncService;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "holiday.initialization.enabled=false",
        "holiday.scheduling.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:holiday-controller;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,LINE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.show_sql=false",
        "jwt.secret=cXVvdGF0aW9uLXRlc3Qtc2VjcmV0LW11c3QtYmUtMzItYnl0ZXM=",
        "jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=120000"
})
@AutoConfigureMockMvc
class HolidayControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HolidaySyncService service;
    @Autowired HolidayRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void allRolesCanReadOnlyCurrentYearInDateOrderIncludingWeekendHolidays() throws Exception {
        int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        LocalDate saturday = start.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        repository.saveAllAndFlush(List.of(
                new Holiday(end, "연말"), new Holiday(saturday, "주말 공휴일"),
                new Holiday(start, "새해"), new Holiday(start.minusDays(1), "지난해"),
                new Holiday(end.plusDays(1), "다음해")));
        for (String role : new String[]{"SUPER_ADMIN", "ORDER_MANAGER", "PRODUCTION_MANAGER", "INQUIRY_MANAGER"}) {
            mvc.perform(get("/api/v1/holidays").with(user("직원").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].holidayDate").value(start.toString()))
                    .andExpect(jsonPath("$[0].id").isNumber())
                    .andExpect(jsonPath("$[1].holidayDate").value(saturday.toString()))
                    .andExpect(jsonPath("$[1].name").value("주말 공휴일"))
                    .andExpect(jsonPath("$[2].holidayDate").value(end.toString()));
        }
        verifyNoInteractions(service);
    }

    @Test
    void returnsEmptyListWithoutImportingWhenNoHolidaysAreStored() throws Exception {
        mvc.perform(get("/api/v1/holidays").with(user("직원").roles("INQUIRY_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousHolidayRead() throws Exception {
        mvc.perform(get("/api/v1/holidays")).andExpect(status().isUnauthorized());
    }

    @Test
    void superAdminCanImportAndReceiveAddedCount() throws Exception {
        when(service.importHolidays(2026)).thenReturn(12);
        mvc.perform(post("/api/v1/holidays/import").param("year", "2026")
                        .with(user("관리자").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.addedCount").value(12));
        verify(service).importHolidays(2026);
    }

    @Test
    void repeatedImportCanReturnZero() throws Exception {
        when(service.importHolidays(2026)).thenReturn(0);
        mvc.perform(post("/api/v1/holidays/import").param("year", "2026")
                        .with(user("관리자").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(0));
    }

    @Test
    void rejectsMissingOrInvalidYearBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/holidays/import").with(user("관리자").roles("SUPER_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.status").value(400));
        for (String year : new String[]{"", "abc", "0", "-1", "10000", "999999999999"}) {
            mvc.perform(post("/api/v1/holidays/import").param("year", year)
                            .with(user("관리자").roles("SUPER_ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.errors").isEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(post("/api/v1/holidays/import").param("year", "2026"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOtherRoles() throws Exception {
        for (String role : new String[]{"ORDER_MANAGER", "PRODUCTION_MANAGER", "INQUIRY_MANAGER"}) {
            mvc.perform(post("/api/v1/holidays/import").param("year", "2026")
                            .with(user("직원").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
}
