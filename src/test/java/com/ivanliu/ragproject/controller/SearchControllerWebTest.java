package com.ivanliu.ragproject.controller;

import com.ivanliu.ragproject.entity.SearchResult;
import com.ivanliu.ragproject.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 特征化测试——锁定重构前的线上 JSON 格式,重构后必须原样通过。
 *
 * <p>覆盖 GET /api/v1/search/hybrid 的三条关键路径:
 * <ol>
 *   <li>带 userId 的成功检索: HTTP 200, {code:200, message:"success", data:[...]},
 *       且 data 元素固定输出 SearchResult 全部 12 个字段(空值字段以显式 null 输出)。</li>
 *   <li>服务抛异常时的错误响应: HTTP 状态仍为 200(不是 500!),
 *       响应体为 {code:500, message:异常消息, data:[]} —— 这是最关键的兼容性行为。</li>
 *   <li>无 userId(匿名)分支: 调用 search() 而非 searchWithPermission(), topK 默认值为 10。</li>
 * </ol>
 */
class SearchControllerWebTest {

    @Mock
    private HybridSearchService hybridSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SearchController searchController = new SearchController();
        ReflectionTestUtils.setField(searchController, "hybridSearchService", hybridSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    void hybridSearchWithUserIdReturns200EnvelopeWithFullSearchResultFields() throws Exception {
        // 全字段结果: 锁定 12 个序列化属性名与取值
        SearchResult full = new SearchResult(
                "abc123", 1, "人工智能是未来科技发展的核心方向。", 0.92,
                "user123", "TECH_DEPT", true,
                "ai.pdf", 3, "anchor-1",
                "vector", "命中的分块原文");
        // 4 参构造: fileName/userId/orgTag 等为 null, 锁定 null 字段依然显式输出
        SearchResult sparse = new SearchResult("def456", 2, "第二条内容", 0.5);

        when(hybridSearchService.searchWithPermission("人工智能的发展", "user123", 5))
                .thenReturn(List.of(full, sparse));

        mockMvc.perform(get("/api/v1/search/hybrid")
                        .param("query", "人工智能的发展")
                        .param("topK", "5")
                        .requestAttr("userId", "user123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // 顶层字段集合恰好为 code/message/data 三个
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                // data[0]: 全字段取值
                .andExpect(jsonPath("$.data[0].length()").value(12))
                .andExpect(jsonPath("$.data[0].fileMd5").value("abc123"))
                .andExpect(jsonPath("$.data[0].chunkId").value(1))
                .andExpect(jsonPath("$.data[0].textContent").value("人工智能是未来科技发展的核心方向。"))
                .andExpect(jsonPath("$.data[0].score").value(0.92))
                .andExpect(jsonPath("$.data[0].fileName").value("ai.pdf"))
                .andExpect(jsonPath("$.data[0].userId").value("user123"))
                .andExpect(jsonPath("$.data[0].orgTag").value("TECH_DEPT"))
                .andExpect(jsonPath("$.data[0].isPublic").value(true))
                .andExpect(jsonPath("$.data[0].pageNumber").value(3))
                .andExpect(jsonPath("$.data[0].anchorText").value("anchor-1"))
                .andExpect(jsonPath("$.data[0].retrievalMode").value("vector"))
                .andExpect(jsonPath("$.data[0].matchedChunkText").value("命中的分块原文"))
                // data[1]: null 字段仍以显式 null 输出(字段数量不变)
                .andExpect(jsonPath("$.data[1].length()").value(12))
                .andExpect(jsonPath("$.data[1].fileMd5").value("def456"))
                .andExpect(jsonPath("$.data[1].isPublic").value(false))
                // matchedChunkText 为空时回落为 textContent
                .andExpect(jsonPath("$.data[1].matchedChunkText").value("第二条内容"))
                // fileName 为显式 null(jsonPath.exists() 对 null 会失败, 用字符串断言)
                .andExpect(content().string(containsString("\"fileName\":null")));

        verify(hybridSearchService).searchWithPermission("人工智能的发展", "user123", 5);
        verify(hybridSearchService, never()).search(anyString(), anyInt());
    }

    @Test
    void hybridSearchServiceFailureReturnsHttp200WithCode500Body() throws Exception {
        // 关键怪癖: 服务层异常时 HTTP 状态码依然是 200, 错误信息放在 body 的 code/message 中
        when(hybridSearchService.searchWithPermission("挂了的查询", "user123", 10))
                .thenThrow(new RuntimeException("es down"));

        mockMvc.perform(get("/api/v1/search/hybrid")
                        .param("query", "挂了的查询")
                        .requestAttr("userId", "user123"))
                .andExpect(status().isOk()) // 不是 500!
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("es down"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void hybridSearchWithoutUserIdUsesAnonymousSearchAndDefaultTopK() throws Exception {
        // 无 userId 请求属性: 走 search() 分支, topK 缺省为 10
        when(hybridSearchService.search("公开内容", 10)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/search/hybrid")
                        .param("query", "公开内容"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(hybridSearchService).search("公开内容", 10);
        verify(hybridSearchService, never()).searchWithPermission(anyString(), anyString(), anyInt());
    }

    @Test
    void hybridSearchMissingRequiredQueryParamReturns400() throws Exception {
        // query 为必填参数, 缺失时由框架返回 400
        mockMvc.perform(get("/api/v1/search/hybrid"))
                .andExpect(status().isBadRequest());

        verify(hybridSearchService, never()).search(anyString(), anyInt());
        verify(hybridSearchService, never()).searchWithPermission(anyString(), anyString(), anyInt());
    }
}
