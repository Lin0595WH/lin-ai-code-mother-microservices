package com.lin.linaicodemother.ai.guardrail;


import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * @Author Lin
 * @Date 2026/7/9 20:49
 * @Descriptions 重试输出护轨
 */
public class RetryOutputGuardrail implements OutputGuardrail {

    // ===================== 常量配置区，统一维护便于修改 =====================
    /** 最小有效输出长度阈值 */
    private static final int MIN_CONTENT_LENGTH = 10;

    /**
     * 敏感关键词黑名单，分类维护
     * 1. 账号密码类
     * 2. API/接口密钥类
     * 3. 授权凭证令牌类
     * 4. 公私钥/证书加密类
     * 5. 支付金融隐私类
     * 6. 数据库连接隐私类
     */
    private static final String[] SENSITIVE_WORDS = {
            // 1. 账号密码
            "密码", "pwd", "passwd", "password", "登录密码", "后台密码",
            // 2. API密钥相关（兼容空格/横杠/连写）
            "api key", "apikey", "api-key", "api_secret", "api secret", "api-secret",
            "access key", "accesskey", "access-key", "appkey", "app key", "app-key",
            "app secret", "appsecret", "app-secret",
            // 3. 令牌、授权凭证
            "token", "access_token", "refresh_token", "auth token", "authtoken", "auth-token",
            "authorization", "bearer", "cookie", "sessionid", "session id", "session-id",
            "credential", "credentials", "凭证", "授权码", "验证码", "verify code",
            // 4. 密钥、证书、加密相关
            "secret", "secretkey", "secret key", "secret-key", "私钥", "公钥",
            "rsa私钥", "rsa公钥", "证书", "cert", "certificate", "ssl证书",
            "加密密钥", "解密密钥", "密钥串", "encrypt key", "encryptkey",
            // 5. 支付、金融隐私
            "银行卡", "卡号", "银行卡号", "信用卡", "信用卡号", "cvv",
            "支付密码", "提现密码", "支付宝密钥", "微信支付密钥", "商户号密钥",
            // 6. 数据库、服务连接隐私
            "mysql密码", "数据库密码", "db password", "dbpass", "redis密码",
            "mongodb密钥", "数据库连接串", "jdbc密码", "jdbc pass"
    };

    // ===================== 对外校验入口 =====================
    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        // 入参非空校验
        if (responseFromLLM == null) {
            return buildRepromptResult("大模型返回消息对象为空", "请重新生成完整内容");
        }

        String rawText = responseFromLLM.text();
        String trimText = StrUtil.trim(rawText);

        // 1. 校验空内容
        if (StrUtil.isBlank(trimText)) {
            return buildRepromptResult("响应内容为空", "请重新生成完整的内容");
        }

        // 2. 校验内容长度不足
        if (trimText.length() < MIN_CONTENT_LENGTH) {
            return buildRepromptResult("响应内容过短", "请提供更详细、完整的内容");
        }

        // 3. 校验是否包含密钥隐私敏感信息
        if (containsSensitiveContent(rawText)) {
            return buildRepromptResult("输出包含密钥/凭证/支付隐私类敏感信息",
                    "请重新生成内容，禁止携带密码、私钥、token、api密钥、银行卡、数据库凭证等隐私信息");
        }

        // 全部校验通过
        return success();
    }

    // ===================== 内部工具方法 =====================

    /**
     * 统一构建重试提示返回结果，消除重复代码
     * @param errorMsg 校验失败原因
     * @param repromptTip 给LLM的重生成提示词
     * @return 护轨校验失败结果
     */
    private OutputGuardrailResult buildRepromptResult(String errorMsg, String repromptTip) {
        return reprompt(errorMsg, repromptTip);
    }

    /**
     * 敏感内容检测：大小写无关匹配敏感关键词
     * 兼容带空格、横杠、无分隔符的密钥关键词写法
     * @param rawText 原始模型输出文本
     * @return true=存在敏感词，false=安全
     */
    private boolean containsSensitiveContent(String rawText) {
        if (CharSequenceUtil.isBlank(rawText)) {
            return false;
        }
        String lowerText = rawText.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerText.contains(sensitiveWord)) {
                return true;
            }
        }
        // 后续可扩展正则规则：身份证、手机号、统一社会信用代码等
        return false;
    }

}