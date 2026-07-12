package com.ivanliu.ragproject.common;

/**
 * 跨类共享的 Redis key 构造器。
 * 仅收录被 ≥2 个类使用的 key 模式;单一所有者的前缀(jwt:*、user:token:* 等)留在各自服务内。
 * key 格式不可变更:Redis 中已有存量数据按此格式存储。
 */
public final class RedisKeys {

    /** 会话消息历史,值为 JSON 数组(ChatHandler / RedisRepository 共用) */
    public static String conversation(String conversationId) {
        return "conversation:" + conversationId;
    }

    /** 用户当前活跃会话 ID(ChatHandler / RedisRepository / ConversationService 共用) */
    public static String currentConversation(String userId) {
        return "user:" + userId + ":current_conversation";
    }

    /** 用户反馈 Hash(ChatHandler / AgentToolRegistry 共用) */
    public static String feedback(String userId) {
        return "feedback:" + userId;
    }

    private RedisKeys() {
    }
}
