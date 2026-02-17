package org.noear.solon.ai.codecli.portal;

import org.noear.solon.ai.agent.react.ReActChunk;
import java.util.Map;

/**
 * 机器人通道接口
 * 100% 对齐 OpenClaw：支持消息推送、异步更新及元数据透传
 */
public interface BotChannel {
    String getChannelId();

    /**
     * 启动通道 (如建立 WebSocket, 登录账户)
     */
    void start();

    /**
     * 推送 AI 状态块
     * @return 返回消息 ID (用于后续更新状态)
     */
    String push(String targetId, ReActChunk chunk, Map<String, Object> metadata);

    /**
     * 更新已有消息 (实现“⚙️ 运行中”变为“✅ 已完成”的平滑过渡)
     */
    void update(String targetId, String messageId, String newContent);

    /**
     * 推送纯文本 (用于系统提示、报错或 HITL 请求)
     */
    void pushText(String targetId, String text, Map<String, Object> metadata);

    void stop();
}