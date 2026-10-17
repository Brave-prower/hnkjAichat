package com.hnkj.qa.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnkj.qa.ai.RagQaService;
import com.hnkj.qa.ai.RetrievalTrace;
import com.hnkj.qa.ai.deepseek.DeepSeekClient;
import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.dto.request.AskRequest;
import com.hnkj.qa.dto.response.AskResponse;
import com.hnkj.qa.entity.ChatRecord;
import com.hnkj.qa.repository.ChatRecordRepository;
import com.hnkj.qa.service.QaService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 问答服务实现。
 *
 * <p><b>同步问答</b>：委托 {@link RagQaService}，检索 → 生成 → 一次性返回
 * （响应含检索快照 {@code trace} 与落库主键 {@code recordId}）。</p>
 * <p><b>流式问答</b>：SSE 事件协议为
 * {@code trace → sources → message* → done}（trace 可选，检索失败时省略；
 * done 携带落库主键 recordId 的 JSON）。检索后先推快照与引用来源，
 * 再通过 {@link DeepSeekClient#streamChat} 把模型增量输出逐段写进 {@code message} 事件。</p>
 * <p>每次问答（同步/流式）结束后均落库 {@code t_chat_record}，
 * 主键 recordId 随响应/done 事件下发，供后续回答反馈评价使用。</p>
 *
 * <p>流式实现选型说明：本工程是 Spring MVC（非 WebFlux），因此直接用
 * {@link SseEmitter} + 阻塞式 HTTP 流读取，不引入 reactor 依赖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaServiceImpl implements QaService {

    /** SSE 连接最长存活时间：一次回答通常几秒，给足 3 分钟余量 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final RagQaService ragQaService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ChatRecordRepository chatRecordRepository;

    /** 专供 SSE 使用的线程池，避免长时间占用 Tomcat 请求线程 */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "qa-sse");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        sseExecutor.shutdownNow();
    }

    // ============================================================
    // 同步问答
    // ============================================================

    @Override
    public AskResponse ask(AskRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BizException(ErrorCode.QUESTION_EMPTY);
        }
        String sessionId = resolveSessionId(request);
        String question = request.getQuestion().trim();

        long start = System.currentTimeMillis();
        RagQaService.RagResult result = ragQaService.answer(question);
        long elapsed = System.currentTimeMillis() - start;

        Long recordId = saveRecord(sessionId, question, result.answer(),
                result.sources(), (int) elapsed);

        log.info("问答完成：session={} 耗时={}ms 来源={}条 recordId={}",
                sessionId, elapsed, result.sources().size(), recordId);

        return AskResponse.builder()
                .sessionId(sessionId)
                .answer(result.answer())
                .sources(result.sources())
                .latencyMs((int) elapsed)
                .trace(result.trace())
                .recordId(recordId)
                .build();
    }

    // ============================================================
    // 流式问答（SSE）
    // ============================================================

    @Override
    public SseEmitter askStream(AskRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String question = request == null ? null : request.getQuestion();

        // 刻意不抛异常：SSE 场景下抛出去会被 GlobalExceptionHandler 按 JSON 写出，
        // 与客户端声明的 Accept: text/event-stream 冲突，最终报 406 而不是给出可读错误。
        if (question == null || question.isBlank()) {
            sendEvent(emitter, "error", ErrorCode.QUESTION_EMPTY.getMessage());
            finish(emitter, null);
            return emitter;
        }

        String sessionId = resolveSessionId(request);
        String trimmed = question.trim();
        sseExecutor.execute(() -> runStream(emitter, trimmed, sessionId));
        return emitter;
    }

    /**
     * SSE 工作线程主体：检索 → 推快照/来源 → 流式生成 → 落库 → 结束。
     * <p>本方法保证任何分支都以事件收尾，不会把异常抛回给容器。</p>
     */
    private void runStream(SseEmitter emitter, String question, String sessionId) {
        long start = System.currentTimeMillis();
        try {
            RagQaService.Prepared prepared = ragQaService.prepare(question);

            // ---------- 1. 检索快照（透明化面板数据源；检索失败时省略本事件） ----------
            if (prepared.trace() != null) {
                sendEvent(emitter, "trace", objectMapper.writeValueAsString(prepared.trace()));
            }

            // ---------- 2. 引用来源，前端可立即渲染来源卡片 ----------
            if (!prepared.sources().isEmpty()) {
                sendEvent(emitter, "sources", objectMapper.writeValueAsString(prepared.sources()));
            }

            // ---------- 3. 检索无命中：直接给标准应答（拒答也落库） ----------
            if (prepared.hits().isEmpty()) {
                sendEvent(emitter, "message", RagQaService.NO_REFERENCE_REPLY);
                Long recordId = saveRecord(sessionId, question, RagQaService.NO_REFERENCE_REPLY,
                        prepared.sources(), (int) (System.currentTimeMillis() - start));
                log.info("流式问答完成（未命中知识库）：session={} 耗时={}ms recordId={}",
                        sessionId, System.currentTimeMillis() - start, recordId);
                finish(emitter, recordId);
                return;
            }

            // ---------- 4. 流式生成（收集完整正文，结束后落库） ----------
            StringBuilder answer = new StringBuilder();
            AtomicInteger chunks = new AtomicInteger();
            try {
                deepSeekClient.streamChat(prepared.systemPrompt(), prepared.userPrompt(), delta -> {
                    sendEvent(emitter, "message", delta);
                    answer.append(delta);
                    chunks.incrementAndGet();
                });
                log.info("流式问答完成：session={} 增量片段={} 耗时={}ms",
                        sessionId, chunks.get(), System.currentTimeMillis() - start);
            } catch (BizException e) {
                // 生成失败不中断连接：改推"制度原文兜底"，让用户至少拿到检索到的原文与页码，
                // 同时把真实原因写清楚，避免再出现"智能问答接口尚未接通"这种含糊提示。
                log.error("流式生成失败，降级为制度原文兜底：session={} 原因={}", sessionId, e.getMessage());
                String fallback = ragQaService.fallbackAnswer(prepared.hits(), briefReason(e));
                sendEvent(emitter, "message", fallback);
                answer.setLength(0);
                answer.append(fallback);
            }

            Long recordId = saveRecord(sessionId, question, answer.toString(),
                    prepared.sources(), (int) (System.currentTimeMillis() - start));
            finish(emitter, recordId);
        } catch (BizException e) {
            log.warn("流式问答业务失败：session={} message={}", sessionId, e.getMessage());
            sendEvent(emitter, "error", e.getMessage());
            finish(emitter, null);
        } catch (Exception e) {
            log.error("流式问答异常：session={}", sessionId, e);
            sendEvent(emitter, "error", "生成回答时发生异常：" + e.getMessage());
            finish(emitter, null);
        }
    }

    // ============================================================
    // 问答记录落库
    // ============================================================

    /**
     * 保存一条问答记录，返回主键（供前端提交回答反馈评价）。
     * <p>落库失败只记日志不抛出——记录是辅助能力，不应影响问答主链路。</p>
     */
    private Long saveRecord(String sessionId, String question, String answer,
                            List<?> sources, Integer latencyMs) {
        try {
            ChatRecord record = ChatRecord.builder()
                    .sessionId(sessionId)
                    .question(question)
                    .answer(answer)
                    .sources(sources == null ? null : objectMapper.writeValueAsString(sources))
                    .latencyMs(latencyMs)
                    .build();
            return chatRecordRepository.save(record).getId();
        } catch (Exception e) {
            log.warn("问答记录落库失败（不影响问答主链路）：session={} 原因={}", sessionId, e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 历史记录
    // ============================================================

    @Override
    public List<AskResponse> history(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.QUESTION_EMPTY);
        }
        return chatRecordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(r -> AskResponse.builder()
                        .sessionId(r.getSessionId())
                        .answer(r.getAnswer())
                        .sources(parseSources(r.getSources()))
                        .latencyMs(r.getLatencyMs())
                        .recordId(r.getId())
                        .build())
                .toList();
    }

    /** 历史记录里的 sources 是 JSON 字符串，解析失败时返回空列表不阻断 */
    private List<com.hnkj.qa.dto.response.SourceRef> parseSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            com.hnkj.qa.dto.response.SourceRef.class));
        } catch (Exception e) {
            log.warn("历史记录 sources 解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private static String resolveSessionId(AskRequest request) {
        String sid = request == null ? null : request.getSessionId();
        return (sid == null || sid.isBlank()) ? UUID.randomUUID().toString() : sid;
    }

    /**
     * 写出一个 SSE 事件。
     *
     * <p>交给 {@link SseEmitter#event()} 分帧：Spring 会把 {@code data} 里的每一行
     * 单独写成一条 {@code data:} 行，因此模型输出中的换行不会破坏 SSE 分帧，
     * 前端把多条 data 行按 {@code \n} 拼回即可。</p>
     */
    private void sendEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        } catch (IOException | IllegalStateException e) {
            // 客户端提前断开是常态，不必报错
            log.debug("SSE 写出失败（客户端可能已断开）：event={} cause={}", event, e.getMessage());
        }
    }

    /**
     * 正常收尾：补一个 done 事件后关闭连接。
     *
     * <p>done 的 data 为 JSON：{@code {"recordId": 123}}（recordId 可为 null，
     * 落库失败/错误分支时），前端据此把回答与反馈评价关联起来。</p>
     */
    private void finish(SseEmitter emitter, Long recordId) {
        try {
            sendEvent(emitter, "done",
                    objectMapper.writeValueAsString(java.util.Map.of("recordId", recordId == null ? 0L : recordId)));
        } catch (Exception e) {
            sendEvent(emitter, "done", "[DONE]");
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("关闭 SSE 连接失败：{}", e.getMessage());
        }
    }

    /** 把底层异常翻译成用户能看懂的一句话原因 */
    private static String briefReason(BizException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("401") || msg.contains("UNAUTHORIZED")) {
            return "大模型 API Key 无效或已过期（401）";
        }
        if (msg.contains("402") || msg.contains("Insufficient Balance")) {
            return "大模型账户余额不足（402）";
        }
        if (msg.contains("429")) {
            return "大模型调用频率超限（429）";
        }
        if (msg.contains("未配置")) {
            return msg;
        }
        return msg.length() <= 80 ? msg : msg.substring(0, 80) + "…";
    }
}
