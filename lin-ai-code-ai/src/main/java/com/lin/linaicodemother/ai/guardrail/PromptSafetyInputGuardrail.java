package com.lin.linaicodemother.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Lin
 * @Date 2026/7/8 21:50
 * @Descriptions Prompt 安全审查护轨 - 增强版
 * 防御分层：长度校验 → 空值校验 → 输入标准化清洗 → 敏感词拦截 → 注入正则拦截
 */
@Slf4j
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // 配置常量，可迁移至配置文件
    private static final int MAX_INPUT_LENGTH = 1000;
    private static final String GUARDRAIL_LOG_PREFIX = "[Prompt安全护轨]";

    // 高危敏感词（中英越狱、绕过、破解类）
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak", "无限制模式",
            "DAN", "开发者模式", "管理员权限", "泄露系统提示词"
    );

    // ===================== 增强扩充注入攻击正则库（分类） =====================
    // 1. 指令覆盖/抹除原有规则
    private static final Pattern PATTERN_OVERRIDE_RULES = Pattern.compile(
            "(?i)ignore\\s+(?:previous|above|all|initial)\\s+(?:instructions?|commands?|prompts?|rules|guidance|指导|指令|规则)"
                    + "|(?i)(?:forget|disregard|put aside|丢掉|忘掉|无视)\\s+(?:everything|all|全部|上面|前文|之前)"
    );
    // 2. 角色扮演越狱劫持
    private static final Pattern PATTERN_JAILBREAK_ROLE = Pattern.compile(
            "(?i)(?:pretend|act|behave|扮演|假装|模拟)\\s+(?:as|like|if|if you are|你是|你现在变成)"
                    + "|(?i)switch\\s+to\\s+(?:unrestricted|admin|developer|god|无限制|管理员|开发者模式)"
    );
    // 3. 伪造系统消息/系统提示
    private static final Pattern PATTERN_FAKE_SYSTEM = Pattern.compile(
            "(?i)system\\s*[:：]\\s*you\\s+are|(?i)系统\\s*[:：]\\s*你现在|\\[SYSTEM\\]|<<SYS>>|<\\|im_start\\|>|\\[系统提示\\]"
    );
    // 4. 强制替换新指令
    private static final Pattern PATTERN_NEW_INSTRUCTION = Pattern.compile(
            "(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*[:：]|新指令[:：]|全新规则[:：]"
    );
    // 5. 套取系统提示词攻击
    private static final Pattern PATTERN_LEAK_PROMPT = Pattern.compile(
            "(?i)(?:repeat|output|show|输出|告诉我|展示)\\s+(?:system prompt|系统提示词|初始指令|你收到的所有规则)"
    );
    // 6. 编码/隐藏字符绕过特征（URL/Base64、零宽字符标记）
    private static final Pattern PATTERN_ENCODE_BYPASS = Pattern.compile(
            "%[0-9a-fA-F]{2}|base64\\s*[:：]|\\u200b|\\u200c|\\u200d"
    );

    // 全部注入正则集合，便于遍历
    private static final List<PatternRule> INJECTION_RULES = Arrays.asList(
            new PatternRule("抹除原有系统指令", PATTERN_OVERRIDE_RULES),
            new PatternRule("角色扮演越狱劫持", PATTERN_JAILBREAK_ROLE),
            new PatternRule("伪造系统消息注入", PATTERN_FAKE_SYSTEM),
            new PatternRule("强制替换全新指令", PATTERN_NEW_INSTRUCTION),
            new PatternRule("套取系统提示词攻击", PATTERN_LEAK_PROMPT),
            new PatternRule("编码/零宽字符绕过特征", PATTERN_ENCODE_BYPASS)
    );

    /** 正则规则实体：记录规则名称+正则，用于日志审计 */
    private static record PatternRule(String ruleName, Pattern pattern) {}

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String rawInput = userMessage.singleText();
        // 1. 基础长度校验
        if (rawInput.length() > MAX_INPUT_LENGTH) {
            log.warn("{}输入超长拦截，长度：{}", GUARDRAIL_LOG_PREFIX, rawInput.length());
            return fatal("输入内容过长，不能超过 " + MAX_INPUT_LENGTH + " 字");
        }
        // 2. 空输入校验
        String trimInput = rawInput.trim();
        if (!StringUtils.hasText(trimInput)) {
            log.warn("{}空输入拦截", GUARDRAIL_LOG_PREFIX);
            return fatal("输入内容不能为空");
        }
        // 3. 输入标准化预处理：清理隐藏字符、统一空白
        String cleanInput = normalizeInput(rawInput);

        // 4. 敏感词检测
        String lowerCleanInput = cleanInput.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            String lowerWord = word.toLowerCase();
            if (lowerCleanInput.contains(lowerWord)) {
                log.warn("{}命中敏感词拦截，敏感词：{}，输入片段：{}", GUARDRAIL_LOG_PREFIX, word, getSafeSubStr(cleanInput, 100));
                return fatal("输入包含不当内容，请修改后重试");
            }
        }

        // 5. 遍历所有注入正则，检测恶意模式
        for (PatternRule rule : INJECTION_RULES) {
            Matcher matcher = rule.pattern().matcher(cleanInput);
            if (matcher.find()) {
                log.warn("{}检测到恶意注入攻击，规则类型：{}，匹配内容：{}，输入片段：{}",
                        GUARDRAIL_LOG_PREFIX, rule.ruleName(), matcher.group(), getSafeSubStr(cleanInput, 100));
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }

        // 全部校验通过
        log.info("{}输入安全校验放行", GUARDRAIL_LOG_PREFIX);
        return success();
    }

    /**
     * 输入标准化清洗：防御零宽字符、多余空白、换行绕过
     */
    private String normalizeInput(String input) {
        // 移除零宽空白字符（攻击者常用绕过手段）
        input = input.replaceAll("[\\u200b\\u200c\\u200d\\u200e\\u200f]", "");
        // 统一所有换行、多空格为单个空格
        input = input.replaceAll("\\s+", " ");
        return input.trim();
    }

    /**
     * 安全截取日志片段，避免超长日志
     */
    private String getSafeSubStr(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}