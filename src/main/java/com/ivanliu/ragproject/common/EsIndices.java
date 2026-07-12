package com.ivanliu.ragproject.common;

/**
 * Elasticsearch 索引名集中定义。
 * 修改索引名需同步考虑存量数据迁移(reindex),不能只改常量。
 */
public final class EsIndices {

    /** 知识库文档分块索引(向量 + 全文) */
    public static final String KNOWLEDGE_BASE = "knowledge_base";

    private EsIndices() {
    }
}
