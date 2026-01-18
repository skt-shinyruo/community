package com.nowcoder.community.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.api.dto.LoginRequest;
import com.nowcoder.community.auth.service.AuthService;
import com.nowcoder.community.auth.service.CaptchaService;
import com.nowcoder.community.auth.service.PasswordResetService;
import com.nowcoder.community.auth.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthService authService;

    @MockBean
    RegistrationService registrationService;

    @MockBean
    CaptchaService captchaService;

    @MockBean
    PasswordResetService passwordResetService;

    @Test
    void loginShouldSetRefreshCookieAndReturnAccessToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("u");
        req.setPassword("p");

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "rt")
                .httpOnly(true)
                .path("/api/auth")
                .build();

        when(authService.login(eq("u"), eq("p"), isNull(), isNull(), any()))
                .thenReturn(new AuthService.LoginResult("at", refreshCookie));

        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resp).contains("\"accessToken\":\"at\"");
    }

    @Test
    void captchaShouldReturnNoCacheHeaders() throws Exception {
        when(captchaService.issue()).thenReturn(new CaptchaService.IssuedCaptcha("cid", "img", 60));

        String resp = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resp).contains("\"captchaId\":\"cid\"");
    }
}

