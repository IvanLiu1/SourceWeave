package com.ivanliu.ragproject.controller;

import com.ivanliu.ragproject.model.FileUpload;
import com.ivanliu.ragproject.repository.FileUploadRepository;
import com.ivanliu.ragproject.repository.OrganizationTagRepository;
import com.ivanliu.ragproject.service.ChatHandler;
import com.ivanliu.ragproject.service.DocumentService;
import com.ivanliu.ragproject.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 特征化测试——锁定重构前的线上 JSON 格式,重构后必须原样通过.
 *
 * <p>覆盖 DELETE /api/v1/documents/{fileMd5}（DocumentController#deleteDocument）的三个分支：
 * 404 文档不存在、403 无权限、200 删除成功。使用 standaloneSetup 走真实的
 * Jackson 序列化，逐字段钉死响应体的键集合与取值（含中文提示文案）。</p>
 */
class DocumentControllerWebTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ChatHandler chatHandler;

    private DocumentController documentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        documentController = new DocumentController();
        ReflectionTestUtils.setField(documentController, "documentService", documentService);
        ReflectionTestUtils.setField(documentController, "fileUploadRepository", fileUploadRepository);
        ReflectionTestUtils.setField(documentController, "organizationTagRepository", organizationTagRepository);
        ReflectionTestUtils.setField(documentController, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(documentController, "chatHandler", chatHandler);
        mockMvc = MockMvcBuilders.standaloneSetup(documentController).build();
    }

    /**
     * 分支 (a)：文档不存在 → 404，响应体只有 code + message 两个键，无 data 键。
     */
    @Test
    void deleteDocumentReturns404WhenFileNotFound() throws Exception {
        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc("md5-missing", "1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/documents/md5-missing")
                        .requestAttr("userId", "1")
                        .requestAttr("role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("文档不存在"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentService, never()).deleteDocument(anyString(), anyString());
    }

    /**
     * 分支 (b)：非文件所有者且角色不是 ADMIN → 403，响应体只有 code + message 两个键，无 data 键。
     */
    @Test
    void deleteDocumentReturns403WhenNotOwnerAndNotAdmin() throws Exception {
        FileUpload file = new FileUpload();
        file.setFileMd5("md5-owned-by-other");
        file.setFileName("other.pdf");
        file.setUserId("2");

        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc("md5-owned-by-other", "1"))
                .thenReturn(Optional.of(file));

        mockMvc.perform(delete("/api/v1/documents/md5-owned-by-other")
                        .requestAttr("userId", "1")
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("没有权限删除此文档"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentService, never()).deleteDocument(anyString(), anyString());
    }

    /**
     * 分支 (c)：文件所有者删除成功 → 200，响应体恰好为 {code:200, message:"文档删除成功"}，无 data 键。
     */
    @Test
    void deleteDocumentReturns200WhenOwnerDeletes() throws Exception {
        FileUpload file = new FileUpload();
        file.setFileMd5("md5-mine");
        file.setFileName("mine.pdf");
        file.setUserId("1");

        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc("md5-mine", "1"))
                .thenReturn(Optional.of(file));

        mockMvc.perform(delete("/api/v1/documents/md5-mine")
                        .requestAttr("userId", "1")
                        .requestAttr("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文档删除成功"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentService).deleteDocument("md5-mine", "1");
    }

    /**
     * 分支 (c) 变体：非所有者但角色为 ADMIN 也可删除 → 200，且传给服务层的是请求者的 userId（当前行为，特征化锁定）。
     */
    @Test
    void deleteDocumentReturns200WhenAdminDeletesOthersFile() throws Exception {
        FileUpload file = new FileUpload();
        file.setFileMd5("md5-owned-by-other");
        file.setFileName("other.pdf");
        file.setUserId("2");

        when(fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc("md5-owned-by-other", "9"))
                .thenReturn(Optional.of(file));

        mockMvc.perform(delete("/api/v1/documents/md5-owned-by-other")
                        .requestAttr("userId", "9")
                        .requestAttr("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文档删除成功"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentService).deleteDocument("md5-owned-by-other", "9");
    }
}
