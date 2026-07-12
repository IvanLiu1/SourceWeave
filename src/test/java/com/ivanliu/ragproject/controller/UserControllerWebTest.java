package com.ivanliu.ragproject.controller;

import com.ivanliu.ragproject.exception.CustomException;
import com.ivanliu.ragproject.exception.RateLimitExceededException;
import com.ivanliu.ragproject.repository.UserRepository;
import com.ivanliu.ragproject.service.RateLimitService;
import com.ivanliu.ragproject.service.UsageQuotaService;
import com.ivanliu.ragproject.service.UserService;
import com.ivanliu.ragproject.service.UserTokenService;
import com.ivanliu.ragproject.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 特征化测试——锁定重构前的线上 JSON 格式,重构后必须原样通过。
 *
 * <p>覆盖 {@link UserController} 的 /register、/login、/logout 接口，
 * 通过 standalone MockMvc 走真实的 Jackson 序列化，逐字段钉死：
 * HTTP 状态码、顶层字段集合大小、每个字段的精确取值（含中文消息）。
 * 特别锁定限流路径（429 + retryAfterSeconds）与兜底异常路径（500 + "Internal server error"）。
 */
class UserControllerWebTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private UsageQuotaService usageQuotaService;

    @Mock
    private UserTokenService userTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "userService", userService);
        ReflectionTestUtils.setField(userController, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(userController, "userRepository", userRepository);
        ReflectionTestUtils.setField(userController, "rateLimitService", rateLimitService);
        ReflectionTestUtils.setField(userController, "usageQuotaService", usageQuotaService);
        ReflectionTestUtils.setField(userController, "userTokenService", userTokenService);
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    // ---------------------------------------------------------------
    // POST /api/v1/users/register
    // ---------------------------------------------------------------

    @Test
    void register_success_returns200WithExactEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(userService).registerUser("alice", "pass-123456");
    }

    @Test
    void register_emptyParams_returns400WithExactEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Username and password cannot be empty"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(userService, never()).registerUser(anyString(), anyString());
    }

    @Test
    void register_customException_realServiceUsage_returns400WithExactEnvelope() throws Exception {
        // 真实业务用法：UserService.registerUser 在用户名重复时抛出
        // new CustomException("Username already exists", HttpStatus.BAD_REQUEST)
        doThrow(new CustomException("Username already exists", HttpStatus.BAD_REQUEST))
                .when(userService).registerUser("alice", "pass-123456");

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Username already exists"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void register_customException_statusIsPassedThroughVerbatim() throws Exception {
        // 特征：controller 将 CustomException 携带的任意 HttpStatus 原样回显为
        // HTTP 状态码与 body 中的 code 字段
        doThrow(new CustomException("注册冲突", HttpStatus.CONFLICT))
                .when(userService).registerUser("bob", "pass-123456");

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("注册冲突"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void register_unexpectedException_returns500WithLiteralInternalServerError() throws Exception {
        doThrow(new RuntimeException("db connection refused"))
                .when(userService).registerUser("alice", "pass-123456");

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void register_rateLimited_returns429WithRetryAfterSecondsField() throws Exception {
        // 真实业务用法：RateLimitService.checkRegisterByIp 超限时抛出
        // new RateLimitExceededException("注册请求过于频繁", retryAfterSeconds)
        doThrow(new RateLimitExceededException("注册请求过于频繁", 42L))
                .when(rateLimitService).checkRegisterByIp(anyString());

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("注册请求过于频繁"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(42))
                .andExpect(jsonPath("$.length()").value(3));

        verify(userService, never()).registerUser(anyString(), anyString());
    }

    // ---------------------------------------------------------------
    // POST /api/v1/users/login
    // ---------------------------------------------------------------

    @Test
    void login_success_returns200WithTokenAndRefreshTokenInData() throws Exception {
        when(userService.authenticateUser("alice", "pass-123456")).thenReturn("alice");
        when(jwtUtils.generateToken("alice")).thenReturn("access-token-abc");
        when(jwtUtils.generateRefreshToken("alice")).thenReturn("refresh-token-xyz");

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.token").value("access-token-abc"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-xyz"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void login_emptyParams_returns400WithExactEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Username and password cannot be empty"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(userService, never()).authenticateUser(anyString(), anyString());
    }

    @Test
    void login_invalidCredentials_returns401WithExactEnvelope_noDataField() throws Exception {
        when(userService.authenticateUser("alice", "wrong-pass")).thenReturn(null);

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void login_rateLimited_returns429WithRetryAfterSecondsField() throws Exception {
        // 真实业务用法：RateLimitService.checkLoginByIp 超限时抛出
        // new RateLimitExceededException("登录请求过于频繁", retryAfterSeconds)
        doThrow(new RateLimitExceededException("登录请求过于频繁", 17L))
                .when(rateLimitService).checkLoginByIp(anyString());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("登录请求过于频繁"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(17))
                .andExpect(jsonPath("$.length()").value(3));

        verify(userService, never()).authenticateUser(anyString(), anyString());
    }

    @Test
    void login_unexpectedException_returns500WithLiteralInternalServerError() throws Exception {
        when(userService.authenticateUser("alice", "pass-123456"))
                .thenThrow(new RuntimeException("redis timeout"));

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass-123456\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/users/logout
    // ---------------------------------------------------------------

    @Test
    void logout_success_returns200WithExactEnvelope() throws Exception {
        when(jwtUtils.extractUsernameFromToken("jwt-abc")).thenReturn("alice");

        mockMvc.perform(post("/api/v1/users/logout")
                        .header("Authorization", "Bearer jwt-abc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Logout successful"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(jwtUtils).invalidateToken("jwt-abc");
    }

    @Test
    void logout_tokenWithoutBearerPrefix_returns400WithExactEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/users/logout")
                        .header("Authorization", "jwt-abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid token format"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(jwtUtils, never()).invalidateToken(anyString());
    }

    @Test
    void logout_usernameNotExtractable_returns401WithExactEnvelope() throws Exception {
        when(jwtUtils.extractUsernameFromToken("jwt-abc")).thenReturn(null);

        mockMvc.perform(post("/api/v1/users/logout")
                        .header("Authorization", "Bearer jwt-abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid token"))
                .andExpect(jsonPath("$.length()").value(2));

        verify(jwtUtils, never()).invalidateToken(anyString());
    }
}
