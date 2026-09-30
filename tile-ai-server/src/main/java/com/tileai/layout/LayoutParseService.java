package com.tileai.layout;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.agent.AgentClient;
import com.tileai.common.api.ErrorItem;
import com.tileai.common.api.ValidationErrors;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.layout.model.LayoutModel;

/**
 * 户型录入:自然语言/表单 → 结构化户型(契约 {@code POST /layout/parse};PRD §5.3)。
 *
 * <p><b>两条路径,分得很清:</b>
 *
 * <ul>
 *   <li><b>{@code params}(结构化表单)就地处理</b> —— 表单本来就是结构化的,
 *       不需要模型。走模型等于花一次调用换一次"可能解析错"。校验交给
 *       {@code LayoutSpace} 上的 jakarta 注解(控制器层 {@code @Valid}),
 *       失败自动产出 10001 + 字段明细。</li>
 *   <li><b>{@code text}(自然语言)交给 Python</b> —— 这才是真正需要理解的地方
 *       (`/internal/agent/parse-layout`)。</li>
 * </ul>
 *
 * <p><b>{@code params} 优先于 {@code text}</b>(架构 §10.4 约定 3):两者都给时,
 * 结构化是用户明确填的,文本是模糊描述。
 *
 * <p><b>面积推导只在本服务做一次</b>:Python 侧刻意不派生 {@code areas}
 * (见 app/agent/layout_parse.py),因为派生公式的实现已经在 {@link LayoutAreaCalculator}。
 * 两条路径最终都经过它,避免出现两份可能不一致的实现。
 */
@Service
public class LayoutParseService {

    private final AgentClient agentClient;
    private final ObjectMapper objectMapper;

    public LayoutParseService(AgentClient agentClient, ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析户型。
     *
     * @param text   自然语言描述(可空)
     * @param params 结构化表单(可空;**非空时优先,且不调模型**)
     * @throws BusinessException 10001(输入问题,带逐项原因)或 90001(服务故障)
     */
    public LayoutModel parse(String text, LayoutModel params) {
        if (params != null) {
            // 表单路径:不调模型。校验已在控制器层由 @Valid 完成。
            return LayoutAreaCalculator.withDerivedAreas(params);
        }

        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "参数校验失败",
                    ValidationErrors.of(java.util.List.of(
                            ErrorItem.of("text", "请给出户型描述,或改用表单录入"))));
        }

        JsonNode data = agentClient.post("/internal/agent/parse-layout",
                java.util.Map.of("text", text.trim()));

        LayoutModel parsed = toModel(data);
        // 派生**只在这里**发生一次 —— 无论走哪条路径
        return LayoutAreaCalculator.withDerivedAreas(parsed);
    }

    private LayoutModel toModel(JsonNode data) {
        try {
            return objectMapper.treeToValue(data, LayoutModel.class);
        } catch (Exception error) {
            // 模型服务返回了结构对不上的东西 —— 属服务侧问题,不该说成"你的描述有问题"
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
