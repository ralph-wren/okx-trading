package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API服务类
 * 用于调用DeepSeek API生成交易策略代码
 */
@Slf4j
@Service
public class DeepSeekApiService {

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    private final OkHttpClient httpClient;

    public DeepSeekApiService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private String strategyCodeTemplate = "{\n" +
            "  \"strategyName\": \"双均线RSI组合策略\",\n" +
            "  \"strategyId\": \"DUAL_MA_RSI_STRATEGY_b6bf3c73-496a-4053-85da-fb5845f3daf4\",\n" +
            "  \"description\": \"结合双均线交叉和RSI超买超卖信号的组合策略，使用8日和21日EMA交叉，配合RSI过滤\",\n" +
            "  \"comments\": \"【使用场景】趋势跟踪与动量确认。【优点】双重信号确认，减少假突破。【缺点】信号产生较慢。【历史表现】夏普比率0.8-1.2，最大回撤15-25%。【特色】趋势+动量双重过滤。\",\n" +
            "  \"category\": \"趋势策略\",\n" +
            "  \"defaultParams\": {\"shortPeriod\": 8, \"longPeriod\": 21, \"rsiPeriod\": 14, \"rsiOverbought\": 70, \"rsiOversold\": 30},\n" +
            "  \"paramsDesc\": {\"shortPeriod\": \"短期均线周期\", \"longPeriod\": \"长期均线周期\", \"rsiPeriod\": \"RSI周期\", \"rsiOverbought\": \"RSI超买阈值\", \"rsiOversold\": \"RSI超卖阈值\"},\n" +
            "  \"strategyCode\": \"" +
            "public class CreateDualMaRsiStrategy {\\n" +
            "    public static Strategy createDualMaRsiStrategy(BarSeries series) {\\n" +
            "        int shortPeriod = 8;\\n" +
            "        int longPeriod = 21;\\n" +
            "        int rsiPeriod = 14;\\n" +
            "        int rsiOverbought = 70;\\n" +
            "        int rsiOversold = 30;\\n" +
            "\\n" +
            "        if (series.getBarCount() <= longPeriod) {\\n" +
            "            throw new IllegalArgumentException(\\\"数据点不足以计算指标\\\");\\n" +
            "        }\\n" +
            "\\n" +
            "        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);\\n" +
            "        EMAIndicator shortEma = new EMAIndicator(closePrice, shortPeriod);\\n" +
            "        EMAIndicator longEma = new EMAIndicator(closePrice, longPeriod);\\n" +
            "        RSIIndicator rsi = new RSIIndicator(closePrice, rsiPeriod);\\n" +
            "\\n" +
            "        Rule entryRule = new CrossedUpIndicatorRule(shortEma, longEma).and(new UnderIndicatorRule(rsi, rsiOverbought));\\n" +
            "        Rule exitRule = new CrossedDownIndicatorRule(shortEma, longEma).or(new OverIndicatorRule(rsi, rsiOverbought));\\n" +
            "\\n" +
            "        return new BaseStrategy(entryRule, exitRule);\\n" +
            "    }\\n" +
            "}\"\n" +
            "}\n";

    private String codeGenerateStrategy = ""
            + "   - strategyName: 策略名称（中文，简洁明了）\n"
            + "   - strategyId: 前面是大写英文名称，用下划线区分单词，后面用uuid标记防止重复\n"
            + "   - description: 对策略逻辑的简单介绍，比如使用什么计算方式，使用的参数周期等信息\n"
            + "   - comments: 策略使用介绍，比如优缺点，适用场景，胜率，回测情况，短线还是长线使用等信息\n"
            + "   - category: 策略分类（如：趋势策略、震荡策略、成交量策略等）\n"
            + "   - defaultParams: 默认参数（JSON对象格式）\n"
            + "   - paramsDesc: 参数描述（JSON对象格式，key为参数名，value为中文描述）\n"
            + "   - strategyCode: Ta4j策略Java类代码\n"
            + "2. strategyCode【强制格式要求】：\n"
            + "   - 【必须】生成包含静态方法的Java类：public class CreateXxxStrategy\n"
            + "   - 【必须】包含静态方法：public static Strategy createXxxStrategy(BarSeries series)\n"
            + "   - 【绝对禁止】任何extends BaseStrategy的继承格式\n"
            + "   - 【绝对禁止】任何构造函数super()调用\n"
            + "   - 【绝对禁止】任何内部类、匿名类或自定义指标类\n"
            + "   - 【绝对禁止】使用 series.numOf() 包装数值或指标\n"
            + "   - 【绝对禁止】使用 new AndRule() 或 new OrRule() 构造函数\n"
            + "   - 类名格式：Create + 策略英文名 + Strategy（如：CreateDualMaRsiStrategy）\n"
            + "   - 方法名格式：create + 策略英文名 + Strategy（如：createDualMaRsiStrategy）\n"
            + "   - 使用Ta4j库现有的指标和规则\n"
            + "   - 包含买入和卖出规则\n"
            + "   - 代码要简洁且可编译\n"
            + "   - 在方法开头进行数据点检查：if (series.getBarCount() <= period) throw new IllegalArgumentException(\"数据点不足以计算指标\")\n"
            + "   - 最后返回：return new BaseStrategy(entryRule, exitRule)\n"
            + "   - 【关键约束】只能使用 Ta4j 0.18 中真实存在的指标类\n"
            + "   - 【关键约束】Rule 组合使用链式调用：rule1.and(rule2) 或 rule1.or(rule2)\n"
            + "   - 【关键约束】Rule 类直接接受数值，不需要 series.numOf() 包装\n"
            + "   - 【关键约束】不需要 import 语句，直接生成类代码\n"
            + "   - 常用指标：SMAIndicator、EMAIndicator、RSIIndicator、MACDIndicator、VolumeIndicator、ClosePriceIndicator\n"
            + "   - 常用规则：CrossedUpIndicatorRule、CrossedDownIndicatorRule、OverIndicatorRule、UnderIndicatorRule\n"
            + "   - 【重要】所有策略都必须严格按照以下模板格式编写：\n"
            + "   ```java\n"
            + "   public class CreateDualMaRsiStrategy {\n"
            + "       public static Strategy createDualMaRsiStrategy(BarSeries series) {\n"
            + "           int shortPeriod = 8;\n"
            + "           int longPeriod = 21;\n"
            + "           int rsiPeriod = 14;\n"
            + "           int rsiOverbought = 70;\n"
            + "           \n"
            + "           if (series.getBarCount() <= longPeriod) {\n"
            + "               throw new IllegalArgumentException(\"数据点不足以计算指标\");\n"
            + "           }\n"
            + "           \n"
            + "           ClosePriceIndicator closePrice = new ClosePriceIndicator(series);\n"
            + "           EMAIndicator shortEma = new EMAIndicator(closePrice, shortPeriod);\n"
            + "           EMAIndicator longEma = new EMAIndicator(closePrice, longPeriod);\n"
            + "           RSIIndicator rsi = new RSIIndicator(closePrice, rsiPeriod);\n"
            + "           \n"
            + "           Rule entryRule = new CrossedUpIndicatorRule(shortEma, longEma).and(new UnderIndicatorRule(rsi, rsiOverbought));\n"
            + "           Rule exitRule = new CrossedDownIndicatorRule(shortEma, longEma).or(new OverIndicatorRule(rsi, rsiOverbought));\n"
            + "           \n"
            + "           return new BaseStrategy(entryRule, exitRule);\n"
            + "       }\n"
            + "   }\n"
            + "   ```\n";

    private String codeGeneratePromotion = "\n\n【严格代码规范 - 必须严格遵守】\n"
            + "1. 【绝对禁止】以下类型的代码结构：\n"
            + "   - 任何继承BaseStrategy的类：public class XxxStrategy extends BaseStrategy\n"
            + "   - 任何构造函数：public XxxStrategy(BarSeries series)\n"
            + "   - 任何super()调用：super(entryRule, exitRule)\n"
            + "   - 任何内部类、匿名类或自定义指标类\n"
            + "   - 任何复杂的数学运算和类型转换\n"
            + "   - 禁止手写逐 K 线遍历并用 getValue(index) 拼交易逻辑；应使用 Indicator + Rule\n"
            + "   - 任何除了静态方法之外的其他方法\n"
            + "2. 【必须遵循】代码结构：\n"
            + "   - 只能有一个静态方法：public static Strategy createXxxStrategy(BarSeries series)\n"
            + "   - 方法中最后必须返回：return new BaseStrategy(entryRule, exitRule)\n"
            + "   - 买卖规则必须在方法中直接创建\n"
            + "   - 只能使用现有指标类和规则类\n"
            + "3. 【禁止格式示例 - 绝对不能生成】：\n"
            + "   public class VolumeStrategy extends BaseStrategy {\n"
            + "       public VolumeStrategy(BarSeries series) {\n"
            + "           super(entryRule, exitRule);\n"
            + "       }\n"
            + "   }\n"
            + "   class CustomIndicator extends CachedIndicator<Num> // 禁止自定义指标类\n"
            + "   public createXxxStrategy(BarSeries series, int period) // 禁止多参数方法\n"
            + "   Num value = indicator.getValue(series.getEndIndex()) // 禁止动态值获取\n"
            + "4. 【强制格式示例 - 必须严格按照此格式】：\n"
            + "   public class CreateVolumeStrategy {\n"
            + "       public static Strategy createVolumeStrategy(BarSeries series) {\n"
            + "           int period = 20;\n"
            + "           if (series.getBarCount() <= period) {\n"
            + "               throw new IllegalArgumentException(\"数据点不足以计算指标\");\n"
            + "           }\n"
            + "           VolumeIndicator volume = new VolumeIndicator(series);\n"
            + "           SMAIndicator sma = new SMAIndicator(volume, period);\n"
            + "           Rule entryRule = new OverIndicatorRule(volume, sma);\n"
            + "           Rule exitRule = new UnderIndicatorRule(volume, sma);\n"
            + "           return new BaseStrategy(entryRule, exitRule);\n"
            + "       }\n"
            + "   }\n"
            + "5. 【编译错误修复】如果涉及复杂指标，改用简单的SMA/EMA交叉策略\n"
            + "6. 【再次强调】绝对不能生成继承BaseStrategy的格式，只能生成包含静态方法的格式！\n";

    /**
     * Ta4j 0.18 与项目动态编译环境说明，避免模型按旧版/错误包名生成不可编译代码。
     */
    private void appendTa4j018RuntimeSpec(StringBuilder sb) {
        sb.append("【Ta4j 版本与 API：必须与当前工程一致 — Maven 依赖 ta4j-core 0.18】\n");
        sb.append("- 仅使用包名 org.ta4j.core.*，禁止使用已废弃的 eu.verdelhan.ta4j。\n");
        sb.append("- SMAIndicator、EMAIndicator 等均线类在子包 indicators.averages；代码里写短类名即可（编译时会自动 import averages.*）。\n");
        sb.append("- VolumeIndicator 在 indicators.helpers；OnBalanceVolumeIndicator 在 indicators.volume。\n");
        sb.append("- 价格源：ClosePriceIndicator closePrice = new ClosePriceIndicator(series)。\n");
        sb.append("- RSI：new RSIIndicator(new ClosePriceIndicator(series), period)。\n");
        sb.append("- MACD：new MACDIndicator(new ClosePriceIndicator(series), shortPeriod, longPeriod)；【禁止】写成 new MACDIndicator(shortEma, longEma)。\n");
        sb.append("- 【重要】Rule 类可以直接接受数值和指标，不需要 series.numOf() 包装：\n");
        sb.append("  ✅ 正确：new UnderIndicatorRule(rsi, 30)\n");
        sb.append("  ✅ 正确：new UnderIndicatorRule(rsi, rsiOverbought)\n");
        sb.append("  ✅ 正确：new OverIndicatorRule(volume, volumeSma)\n");
        sb.append("  ❌ 错误：new UnderIndicatorRule(rsi, series.numOf(30))\n");
        sb.append("  ❌ 错误：new OverIndicatorRule(volume, series.numOf(volumeSma))\n");
        sb.append("- 【重要】Rule 组合使用链式调用，不使用 AndRule/OrRule 构造函数：\n");
        sb.append("  ✅ 正确：rule1.and(rule2).and(rule3)\n");
        sb.append("  ✅ 正确：rule1.or(rule2)\n");
        sb.append("  ❌ 错误：new AndRule(rule1, rule2, rule3)\n");
        sb.append("  ❌ 错误：new OrRule(rule1, rule2)\n");
        sb.append("- 策略对象：return new BaseStrategy(entryRule, exitRule)。\n");
        sb.append("- 【可用指标列表】（只使用以下指标，不要编造）：\n");
        sb.append("  * 均线：SMAIndicator, EMAIndicator, WMAIndicator\n");
        sb.append("  * 动量：RSIIndicator, MACDIndicator, StochasticOscillatorKIndicator\n");
        sb.append("  * 成交量：VolumeIndicator, OnBalanceVolumeIndicator\n");
        sb.append("  * 价格：ClosePriceIndicator, HighPriceIndicator, LowPriceIndicator\n");
        sb.append("  * 波动：StandardDeviationIndicator\n");
        sb.append("- 【可用规则列表】（只使用以下规则）：\n");
        sb.append("  * 交叉：CrossedUpIndicatorRule, CrossedDownIndicatorRule\n");
        sb.append("  * 比较：OverIndicatorRule, UnderIndicatorRule\n");
        sb.append("  * 停损：StopGainRule, StopLossRule\n");
        sb.append("- 【禁止使用】以下不存在或已废弃的类：\n");
        sb.append("  ❌ IchimokuTenkanSenIndicator, IchimokuKijunSenIndicator（不存在）\n");
        sb.append("  ❌ ParabolicSarIndicator, ChandelierExitIndicator（不存在）\n");
        sb.append("  ❌ UlcerIndexIndicator（不存在）\n");
        sb.append("  ❌ Decimal.valueOf(), DecimalNum.valueOf(), Num.valueOf()（不需要）\n");
        sb.append("- 不确定时只用 SMA/EMA 交叉 + 上文模板中的指标。\n\n");
    }

    /**
     * 更新策略（带对话上下文）
     *
     * @param strategyDescription 策略描述
     * @param conversationContext 对话上下文
     * @return 包含策略名称、分类、默认参数、参数描述和策略代码的JSON对象
     */
    public JSONObject updateStrategyInfo(String strategyDescription, String currentStrategy, String conversationContext) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("DeepSeek API密钥未配置，请在application.yml中设置deepseek.api.key");
        }

        try {
            String prompt = buildCompleteStrategyPrompt(strategyDescription, currentStrategy, conversationContext);
            String response = callDeepSeekApi(prompt);
            return extractStrategyInfoFromResponse(response);
        } catch (Exception e) {
            log.error("调用DeepSeek API生成完整策略信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成完整策略信息失败: " + e.getMessage());
        }
    }

    /**
     * 批量生成完整的策略信息
     *
     * @param strategyDescriptions 策略描述列表
     * @return 包含多个策略信息的JSON数组
     */
    public JSONArray generateBatchCompleteStrategyInfo(String[] strategyDescriptions) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("DeepSeek API密钥未配置，请在application.yml中设置deepseek.api.key");
        }

        try {
            String prompt = buildBatchCompleteStrategyPrompt(strategyDescriptions);
            String response = callDeepSeekApi(prompt);
            return extractBatchStrategyInfoFromResponse(response);
        } catch (Exception e) {
            log.error("调用DeepSeek API批量生成策略信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量生成策略信息失败: " + e.getMessage());
        }
    }

    /**
     * 更新策略信息提示词（带对话上下文）
     */
    private String buildCompleteStrategyPrompt(String strategyDescription, String currentStrategy, String conversationContext) {
        StringBuilder promptBuilder = new StringBuilder();

        appendTa4j018RuntimeSpec(promptBuilder);

        // 强制开头声明格式要求
        promptBuilder.append("【强制格式要求 - 必须严格遵守】\n");
        promptBuilder.append("生成的策略代码必须是以下静态方法格式，绝对不能是继承格式：\n\n");
        promptBuilder.append("public class CreateXxxStrategy {\n");
        promptBuilder.append("    public static Strategy createXxxStrategy(BarSeries series) {\n");
        promptBuilder.append("        // 策略逻辑\n");
        promptBuilder.append("        return new BaseStrategy(entryRule, exitRule);\n");
        promptBuilder.append("    }\n");
        promptBuilder.append("}\n\n");
        promptBuilder.append("【禁止格式】绝对不能生成类似以下继承BaseStrategy的格式：\n");
        promptBuilder.append("public class XxxStrategy extends BaseStrategy {\n");
        promptBuilder.append("    public XxxStrategy(BarSeries series) {\n");
        promptBuilder.append("        super(entryRule, exitRule);\n");
        promptBuilder.append("    }\n");
        promptBuilder.append("}\n\n");

        promptBuilder.append("请根据以下描述生成一个完整的Ta4j交易策略信息，返回JSON格式。\n\n");

        // 添加对话上下文（如果存在）
        if (conversationContext != null && !conversationContext.trim().isEmpty()) {
            promptBuilder.append(conversationContext).append("\n\n");
            promptBuilder.append("基于以上历史对话记录，请按照请求继续优化策略。\n\n");
        }
        // 添加最新策略信息
        if (StringUtils.isNotBlank(currentStrategy)) {
            promptBuilder.append("\n\n=== 当前策略信息 ===\n");
            promptBuilder.append(currentStrategy).append("\n\n");
            promptBuilder.append("\n\n=== 当前策略信息结束 ===\n");
            promptBuilder.append("基于以上最新策略信息，请按照请求继续优化策略。\n\n");
        }

        // 处理ATR相关请求，统一转换为简单策略
        String processedDescription = strategyDescription;
        if (strategyDescription.contains("atr") || strategyDescription.contains("ATR")) {
            processedDescription = "生成一个简单的EMA双线交叉策略，作为ATR策略的替代方案";
        }

        promptBuilder.append("期望生成策略的描述：").append(processedDescription).append("\n\n");
        promptBuilder.append("要求：\n");
        promptBuilder.append("1. 返回JSON格式，包含以下字段：\n");
        promptBuilder.append(codeGenerateStrategy);
        promptBuilder.append("3. 【再次强调】策略代码必须是静态方法格式，绝对不能是继承BaseStrategy的类格式！\n");
        promptBuilder.append("4. 只返回JSON，不要其他解释\n\n");
        promptBuilder.append("示例格式（必须严格按照此格式）：\n");
        promptBuilder.append(strategyCodeTemplate);
        promptBuilder.append(codeGeneratePromotion);

        return promptBuilder.toString();
    }

    /**
     * 构建批量策略信息提示词
     */
    private String buildBatchCompleteStrategyPrompt(String[] strategyDescriptions) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请根据以下多个策略描述，一次性生成多个完整的Ta4j交易策略信息，返回JSON数组格式：\n\n");

        appendTa4j018RuntimeSpec(promptBuilder);

        promptBuilder.append("需要生成的策略描述列表：\n");
        for (int i = 0; i < strategyDescriptions.length; i++) {
            promptBuilder.append(String.format("%d. %s\n", i + 1, strategyDescriptions[i]));
        }
        promptBuilder.append("\n");

        promptBuilder.append("要求：\n");
        promptBuilder.append("1. 返回JSON数组格式，数组中每个元素包含以下字段：\n");
        promptBuilder.append(codeGenerateStrategy);
        promptBuilder.append("3. 【重要】策略代码必须是静态方法格式，不能是继承BaseStrategy的类格式！\n");
        promptBuilder.append("4. 确保每个策略都有不同的strategyId和类名\n");
        promptBuilder.append("5. 只返回JSON数组，不要其他解释\n\n");
        promptBuilder.append("示例格式：\n");
        promptBuilder.append("[\n");
        promptBuilder.append(strategyCodeTemplate);
        promptBuilder.append("]\n");
        promptBuilder.append(codeGeneratePromotion);
        return promptBuilder.toString();
    }

    /**
     * 调用DeepSeek API
     */
    private String callDeepSeekApi(String prompt) throws IOException {
        JSONObject requestBody = new JSONObject();
        // 修复：添加必需的model参数，这是导致422错误的原因
        requestBody.put("model", "deepseek-chat"); // deepseek-reasoner  deepseek-chat

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);

        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 8000);
        requestBody.put("temperature", 0.1);

        RequestBody body = RequestBody.create(
                requestBody.toJSONString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API调用失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            log.debug("DeepSeek API响应: {}", responseBody);
            return responseBody;
        }
    }

    /**
     * 从API响应中提取代码
     */
    private String extractCodeFromResponse(String response) {
        try {
            JSONObject jsonResponse = JSON.parseObject(response);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices != null && choices.size() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String content = message.getString("content");

                // 提取代码块
                if (content.contains("```")) {
                    int start = content.indexOf("```");
                    int end = content.lastIndexOf("```");
                    if (start != end && start != -1) {
                        String codeBlock = content.substring(start + 3, end).trim();
                        // 移除可能的语言标识符
                        if (codeBlock.startsWith("java")) {
                            codeBlock = codeBlock.substring(4).trim();
                        }
                        return codeBlock;
                    }
                }

                return content.trim();
            }
            throw new RuntimeException("API响应格式错误");
        } catch (Exception e) {
            log.error("解析API响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析API响应失败: " + e.getMessage());
        }
    }

    /**
     * 从API响应中提取完整策略信息
     */
    private JSONObject extractStrategyInfoFromResponse(String response) {
        try {
            JSONObject jsonResponse = JSON.parseObject(response);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices != null && choices.size() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String content = message.getString("content");

                // 提取JSON内容
                String jsonContent = content.trim();

                // 如果包含代码块标记，提取其中的JSON
                if (jsonContent.contains("```")) {
                    int start = jsonContent.indexOf("```");
                    int end = jsonContent.lastIndexOf("```");
                    if (start != end && start != -1) {
                        jsonContent = jsonContent.substring(start + 3, end).trim();
                        // 移除可能的语言标识符
                        if (jsonContent.startsWith("json")) {
                            jsonContent = jsonContent.substring(4).trim();
                        }
                    }
                }

                // 解析JSON
                JSONObject strategyInfo = JSON.parseObject(jsonContent);

                // 验证必要字段
                if (!strategyInfo.containsKey("strategyName") ||
                        !strategyInfo.containsKey("category") ||
                        !strategyInfo.containsKey("defaultParams") ||
                        !strategyInfo.containsKey("paramsDesc") ||
                        !strategyInfo.containsKey("strategyCode")) {
                    throw new RuntimeException("API返回的策略信息缺少必要字段");
                }

                return strategyInfo;
            }
            throw new RuntimeException("API响应格式错误");
        } catch (Exception e) {
            log.error("解析完整策略信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析完整策略信息失败: " + e.getMessage());
        }
    }

    /**
     * 从API响应中提取批量策略信息
     */
    private JSONArray extractBatchStrategyInfoFromResponse(String response) {
        try {
            JSONObject jsonResponse = JSON.parseObject(response);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices != null && choices.size() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String content = message.getString("content");

                // 提取JSON内容
                String jsonContent = content.trim();

                // 如果包含代码块标记，提取其中的JSON
                if (jsonContent.contains("```")) {
                    int start = jsonContent.indexOf("```");
                    int end = jsonContent.lastIndexOf("```");
                    if (start != end && start != -1) {
                        jsonContent = jsonContent.substring(start + 3, end).trim();
                        // 移除可能的语言标识符
                        if (jsonContent.startsWith("json")) {
                            jsonContent = jsonContent.substring(4).trim();
                        }
                    }
                }

                // 尝试解析为JSON数组
                return JSON.parseArray(jsonContent);
            }
            throw new RuntimeException("API响应格式错误");
        } catch (Exception e) {
            log.error("解析批量API响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析批量API响应失败: " + e.getMessage());
        }
    }
}
