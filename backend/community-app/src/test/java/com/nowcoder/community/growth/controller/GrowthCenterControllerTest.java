package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.CheckInService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthCenterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckInService checkInService;

    @MockBean
    private GrowthBusinessTimeService growthBusinessTimeService;

    @Test
    void checkInActionShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/growth/check-in"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkInActionShouldUseServerOwnedBusinessDate() throws Exception {
        when(growthBusinessTimeService.today()).thenReturn(LocalDate.of(2026, 3, 22));
        when(checkInService.checkIn(1, LocalDate.of(2026, 3, 22)))
                .thenReturn(new CheckInService.CheckInResult(true, true, 3, 3, 7));

        mockMvc.perform(post("/api/growth/check-in")
                        .param("date", "2026-03-30")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.newlyCheckedIn").value(true))
                .andExpect(jsonPath("$.data.checkedInToday").value(true))
                .andExpect(jsonPath("$.data.currentStreak").value(3))
                .andExpect(jsonPath("$.data.maxStreak").value(3))
                .andExpect(jsonPath("$.data.totalCheckInDays").value(7));
    }

    @Test
    void checkInStatusAndCalendarShouldExposeSignedDates() throws Exception {
        when(growthBusinessTimeService.today()).thenReturn(LocalDate.of(2026, 3, 22));
        when(checkInService.status(1, LocalDate.of(2026, 3, 22)))
                .thenReturn(new CheckInService.CheckInStatus(true, 3, 5, 8));
        when(checkInService.calendar(1, 2026, 3))
                .thenReturn(List.of(
                        LocalDate.of(2026, 3, 20),
                        LocalDate.of(2026, 3, 21),
                        LocalDate.of(2026, 3, 22)
                ));

        mockMvc.perform(get("/api/growth/check-in/status")
                        .param("date", "2026-03-22")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedInToday").value(true))
                .andExpect(jsonPath("$.data.currentStreak").value(3))
                .andExpect(jsonPath("$.data.maxStreak").value(5))
                .andExpect(jsonPath("$.data.totalCheckInDays").value(8));

        mockMvc.perform(get("/api/growth/check-in/calendar")
                        .param("year", "2026")
                        .param("month", "3")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(3))
                .andExpect(jsonPath("$.data.checkedInDates[0]").value("2026-03-20"))
                .andExpect(jsonPath("$.data.checkedInDates[2]").value("2026-03-22"));
    }
}
