package com.ivanliu.ragproject.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActContextCompactorTest {

    private static final String LONG_TOOL_CONTENT = "检索到 2 个知识库片段，来源编号 [3]-[4]。\n\n[3] a.pdf (fileMd5=x, chunkId=1)\n"
            + "内容A".repeat(200) + "\n\n[4] b.docx (fileMd5=y, chunkId=2)\n" + "内容B".repeat(200);

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        if ("tool".equals(role)) {
            msg.put("tool_call_id", "tc-1");
        }
        return msg;
    }

    /** 最后一条 assistant 之前的长工具消息被压缩，之后的（最新一轮观察）保留全文 */
    @Test
    void compactsConsumedToolMessagesOnly() {
        List<Map<String, Object>> messages = new ArrayList<>(List.of(
                message("system", "sys"),
                message("user", "问题"),
                message("assistant", "调工具"),
                message("tool", LONG_TOOL_CONTENT),
                message("assistant", "再调一次"),
                message("tool", LONG_TOOL_CONTENT)
        ));

        int compacted = ReActContextCompactor.compactConsumedToolMessages(messages);

        assertEquals(1, compacted);
        String consumed = (String) messages.get(3).get("content");
        assertTrue(consumed.startsWith(ReActContextCompactor.STUB_PREFIX));
        assertTrue(consumed.length() < LONG_TOOL_CONTENT.length(), "存根应显著短于原文");
        assertEquals(LONG_TOOL_CONTENT, messages.get(5).get("content"), "最新一轮工具结果必须保留全文");
        assertEquals("tc-1", messages.get(3).get("tool_call_id"), "压缩不能丢 tool_call_id");
    }

    /** 存根保留全局来源编号与 fetch_chunk 指引，引用体系不受影响 */
    @Test
    void stubKeepsReferenceNumbersAndEscapeHatch() {
        String stub = ReActContextCompactor.buildStub(LONG_TOOL_CONTENT);

        assertTrue(stub.contains("[3]"));
        assertTrue(stub.contains("[4]"));
        assertTrue(stub.contains("fetch_chunk"));
    }

    /** 幂等：已压缩的存根与短内容不再被处理 */
    @Test
    void skipsStubsAndShortContent() {
        List<Map<String, Object>> messages = new ArrayList<>(List.of(
                message("assistant", "调工具"),
                message("tool", ReActContextCompactor.STUB_PREFIX + " 已压缩过"),
                message("tool", "短结果"),
                message("assistant", "继续")
        ));

        assertEquals(0, ReActContextCompactor.compactConsumedToolMessages(messages));
        assertEquals("短结果", messages.get(2).get("content"));
    }

    /** 没有 assistant 消息（首轮）时不做任何事 */
    @Test
    void noopWhenNoAssistantMessage() {
        List<Map<String, Object>> messages = new ArrayList<>(List.of(
                message("system", "sys"),
                message("user", "问题")
        ));

        assertEquals(0, ReActContextCompactor.compactConsumedToolMessages(messages));
        assertFalse(((String) messages.get(1).get("content")).startsWith(ReActContextCompactor.STUB_PREFIX));
    }
}
