package com.ivanliu.ragproject.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 循环的上下文压缩器：把"已被早前轮次消费过的"工具结果替换为轻量存根，
 * 抑制多轮循环中检索结果全文被反复重发导致的 token 膨胀。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li>只压缩<b>最后一条 assistant 消息之前</b>的 tool 消息——尾部的 tool 结果是模型即将阅读的
 *       最新一轮观察，保留全文；更早轮次的全文已经在被消费的那一轮影响过模型输出。</li>
 *   <li>存根保留全局来源编号与短摘录，引用体系不受影响；模型确需原文时可调 fetch_chunk 取回，
 *       这个"逃生舱"让压缩可以激进而不丢失能力。</li>
 *   <li>纯函数、无状态、确定性——不引入额外 LLM 调用，压缩成本为零。</li>
 * </ul>
 */
public final class ReActContextCompactor {

    /** 存根前缀，兼作幂等标记：已压缩的消息不会被二次处理 */
    static final String STUB_PREFIX = "[已压缩]";

    /** 低于该长度的工具结果不值得压缩（存根自身也有开销） */
    static final int MIN_COMPACT_CHARS = 300;

    /** 存根中保留的原文摘录长度 */
    static final int SNIPPET_CHARS = 120;

    /** 存根中最多罗列的来源编号个数，防止极端情况下存根本身变长 */
    private static final int MAX_LISTED_REFS = 20;

    private static final Pattern REF_NUMBER_PATTERN = Pattern.compile("\\[(\\d{1,4})]");

    private ReActContextCompactor() {
    }

    /**
     * 就地压缩已消费的工具消息。
     *
     * @param messages ReAct 累积的消息列表（会被原地修改）
     * @return 本次被压缩的消息条数
     */
    public static int compactConsumedToolMessages(List<Map<String, Object>> messages) {
        int lastAssistantIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).get("role"))) {
                lastAssistantIndex = i;
                break;
            }
        }
        if (lastAssistantIndex < 0) {
            return 0;
        }

        int compacted = 0;
        for (int i = 0; i < lastAssistantIndex; i++) {
            Map<String, Object> message = messages.get(i);
            if (!"tool".equals(message.get("role"))) {
                continue;
            }
            if (!(message.get("content") instanceof String content)) {
                continue;
            }
            if (content.startsWith(STUB_PREFIX) || content.length() <= MIN_COMPACT_CHARS) {
                continue;
            }
            Map<String, Object> replacement = new LinkedHashMap<>(message);
            replacement.put("content", buildStub(content));
            messages.set(i, replacement);
            compacted++;
        }
        return compacted;
    }

    /**
     * 生成存根：保留来源编号（引用体系依赖）+ 短摘录 + 取回原文的指引。
     */
    static String buildStub(String content) {
        StringBuilder stub = new StringBuilder(STUB_PREFIX)
                .append(" 此工具结果已在早前轮次阅读，原文 ").append(content.length()).append(" 字符已省略。");

        String refs = extractReferenceNumbers(content);
        if (!refs.isEmpty()) {
            stub.append("其中的来源编号 ").append(refs).append(" 仍然有效，可直接用于引用标注。");
        }
        stub.append("确需某片段完整原文时，调用 fetch_chunk 并传入该来源编号。摘录：")
                .append(content, 0, Math.min(SNIPPET_CHARS, content.length()))
                .append("…");
        return stub.toString();
    }

    private static String extractReferenceNumbers(String content) {
        TreeSet<Integer> numbers = new TreeSet<>();
        Matcher matcher = REF_NUMBER_PATTERN.matcher(content);
        while (matcher.find() && numbers.size() < MAX_LISTED_REFS) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        if (numbers.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Integer number : numbers) {
            joined.append('[').append(number).append(']');
        }
        return joined.toString();
    }
}
