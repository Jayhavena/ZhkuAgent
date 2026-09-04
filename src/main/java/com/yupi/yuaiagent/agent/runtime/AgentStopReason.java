package com.yupi.yuaiagent.agent.runtime;

/**
 * 表示一次运行为什么收到停止请求。
 *
 * <p>停止原因与运行状态分开建模：
 * 状态回答“运行进行到哪里”，原因回答“为什么要求停止”。</p>
 */
public enum AgentStopReason {

    /**
     * 当前没有收到停止请求。
     */
    NONE,

    /**
     * 用户主动请求取消任务。
     */
    USER_REQUEST,

    /**
     * SSE 连接超过了服务端设定的超时时间。
     */
    SSE_TIMEOUT,

    /**
     * 客户端关闭页面、取消请求或发生网络断开。
     */
    CLIENT_DISCONNECTED,

    /**
     * 服务端向 SSE 连接发送数据时失败。
     */
    SEND_FAILURE
}