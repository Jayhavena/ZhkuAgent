package com.zhku.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 只记录固定生命周期标记；请求、回答和异常可能携带正文或凭证，不能作为日志参数。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
		log.info("AI_CALL_STARTED");
		try {
			ChatClientResponse response = chain.nextCall(chatClientRequest);
			log.info("AI_CALL_COMPLETED");
			return response;
		} catch (RuntimeException failure) {
			log.info("AI_CALL_FAILED");
			throw failure;
		}
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
		log.info("AI_STREAM_STARTED");
		try {
			// 日志不需要聚合正文；只观察信号，保持分块对象、订阅次数和上游取消传播。
			return chain.nextStream(chatClientRequest)
					.doOnComplete(() -> log.info("AI_STREAM_COMPLETED"))
					.doOnError(failure -> log.info("AI_STREAM_FAILED"))
					.doOnCancel(() -> log.info("AI_STREAM_CANCELLED"));
		} catch (RuntimeException failure) {
			log.info("AI_STREAM_FAILED");
			throw failure;
		}
	}
}
