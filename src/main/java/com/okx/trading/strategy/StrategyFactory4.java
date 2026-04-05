package com.okx.trading.strategy;

import com.okx.trading.util.Ta4jNumUtil;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.*;

import java.math.BigDecimal;
import java.util.*;

import static com.okx.trading.strategy.StrategyRegisterCenter.addExtraStopRule;

/**
 * 策略工厂4 - 高级策略集合
 * 包含40个高级策略（91-130），涵盖机器学习、量化因子、高频、期权、宏观、创新和风险管理策略
 */
public class StrategyFactory4 {

    // ==================== 机器学习启发策略 (91-100) ====================

    /**
     * 创建多指标组合决策策略（原神经网络策略）- 使用多指标组合进行决策
     */
    public static Strategy createMultiIndicatorVotingStrategy(BarSeries series) {
        if (series.getBarCount() <= 30) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 输入层：多个技术指标（模拟神经网络输入）
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 10);
        SMAIndicator avgVolume = new SMAIndicator(volume, 10);

        // 隐藏层：权重组合（模拟神经网络权重）
        // 节点1：趋势信号 (权重35%)
        Rule trendSignal = new OverIndicatorRule(sma10, sma20)
                .and(new OverIndicatorRule(closePrice, sma20));

        // 节点2：动量信号 (权重30%)
        Rule momentumSignal = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(45))
                .and(new OverIndicatorRule(macd, Ta4jNumUtil.valueOf(0)));

        // 节点3：成交量信号 (权重20%)
        Rule volumeSignal = new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.1)));

        // 节点4：波动率信号 (权重15%)
        Rule volatilitySignal = new UnderIndicatorRule(volatility, Ta4jNumUtil.valueOf(1.5));

        // 输出层：激活函数（模拟神经网络输出）
        // 买入：至少3个信号激活
        Rule entryRule = trendSignal.and(momentumSignal).and(volumeSignal)
                .or(trendSignal.and(momentumSignal).and(volatilitySignal))
                .or(trendSignal.and(volumeSignal).and(volatilitySignal))
                .or(momentumSignal.and(volumeSignal).and(volatilitySignal));

        // 卖出：趋势反转或RSI超买
        Rule exitRule = new UnderIndicatorRule(sma10, sma20)
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(75)))
                .or(new UnderIndicatorRule(macd, Ta4jNumUtil.valueOf(0)));

        return new BaseStrategy("神经网络策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 遗传算法策略（修改版）- 放宽条件使其更容易触发交易
     */
    public static Strategy createGeneticAlgorithmStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 技术指标组合
        SMAIndicator shortSMA = new SMAIndicator(closePrice, 10);
        SMAIndicator longSMA = new SMAIndicator(closePrice, 30);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        SMAIndicator volumeSMA = new SMAIndicator(volume, 20);

        // 买入条件1：黄金交叉
        Rule condition1 = new CrossedUpIndicatorRule(shortSMA, longSMA);

        // 买入条件2：RSI低位回升
        Rule condition2 = new CrossedUpIndicatorRule(rsi, Ta4jNumUtil.valueOf(40)); // 降低RSI阈值为40（原为45）

        // 买入条件3：MACD上穿0轴
        Rule condition3 = new CrossedUpIndicatorRule(macd, Ta4jNumUtil.valueOf(0));

        // 买入条件4：成交量放大
        Rule condition4 = new OverIndicatorRule(volume, TransformIndicator.multiply(volumeSMA, BigDecimal.valueOf(1.1))); // 降低成交量要求为1.1倍（原为1.2倍）

        // 买入规则：满足至少2个条件（降低要求，原为3个条件）
        Rule entryRule = condition1.and(condition2)
                .or(condition1.and(condition3))
                .or(condition1.and(condition4))
                .or(condition2.and(condition3))
                .or(condition2.and(condition4))
                .or(condition3.and(condition4));

        // 卖出条件1：死亡交叉
        Rule exitCondition1 = new CrossedDownIndicatorRule(shortSMA, longSMA);

        // 卖出条件2：RSI超买
        Rule exitCondition2 = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(65)); // 降低RSI卖出阈值为65（原为70）

        // 卖出条件3：MACD下穿0轴
        Rule exitCondition3 = new CrossedDownIndicatorRule(macd, Ta4jNumUtil.valueOf(0));

        // 止盈条件
        Indicator<Num> profitTarget = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                if (index == 0) return Ta4jNumUtil.valueOf(0);
                return closePrice.getValue(index - 1).multipliedBy(Ta4jNumUtil.valueOf(1.08)); // 8%止盈条件
            }
        };

        // 卖出规则：满足任一卖出条件或达到止盈
        Rule exitRule = exitCondition1
                .or(exitCondition2)
                .or(exitCondition3)
                .or(new OverIndicatorRule(closePrice, profitTarget));

        return new BaseStrategy("遗传算法策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 创建随机森林策略（修复版）- 简化为多决策树投票
     */
    public static Strategy createRandomForestStrategy(BarSeries series) {
        if (series.getBarCount() <= 30) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 决策树1：趋势树
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);
        Rule tree1 = new OverIndicatorRule(sma10, sma20)
                .and(new OverIndicatorRule(closePrice, sma20));

        // 决策树2：动量树
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        Rule tree2 = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(50))
                .and(new OverIndicatorRule(macd, Ta4jNumUtil.valueOf(0)));

        // 决策树3：成交量树
        SMAIndicator avgVolume = new SMAIndicator(volume, 15);
        Indicator<Num> avgVolume13 = TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.3));
        Rule tree3 = new OverIndicatorRule(volume, avgVolume13);

        // 决策树4：波动率树
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 10);
        ATRIndicator atr = new ATRIndicator(series, 14);
        Rule tree4 = new UnderIndicatorRule(volatility, Ta4jNumUtil.valueOf(2.0))
                .and(new OverIndicatorRule(atr, Ta4jNumUtil.valueOf(0.01)));

        // 决策树5：价格位置树
        HighestValueIndicator highest15 = new HighestValueIndicator(closePrice, 15);
        LowestValueIndicator lowest15 = new LowestValueIndicator(closePrice, 15);
        Indicator<Num> range15_2 = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return highest15.getValue(index).minus(lowest15.getValue(index));
            }
        };
        Indicator<Num> rangeMultiplied15 = TransformIndicator.multiply(range15_2, BigDecimal.valueOf(0.4));

        // 创建一个CachedIndicator来正确处理两个Indicator的相加
        Indicator<Num> threshold15 = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return lowest15.getValue(index).plus(rangeMultiplied15.getValue(index));
            }
        };
        Rule tree5 = new OverIndicatorRule(closePrice, threshold15);

        // 随机森林投票：至少3棵树支持（多数投票）
        Rule entryRule = tree1.and(tree2).and(tree3)
                .or(tree1.and(tree2).and(tree4))
                .or(tree1.and(tree2).and(tree5))
                .or(tree1.and(tree3).and(tree4))
                .or(tree1.and(tree3).and(tree5))
                .or(tree1.and(tree4).and(tree5))
                .or(tree2.and(tree3).and(tree4))
                .or(tree2.and(tree3).and(tree5))
                .or(tree2.and(tree4).and(tree5))
                .or(tree3.and(tree4).and(tree5));

        // 卖出：多数树反对
        Rule exitRule = new UnderIndicatorRule(sma10, sma20)
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(75)))
                .or(new UnderIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(0.8))));

        return new BaseStrategy("随机森林策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 创建SVM策略（修复版）- 简化为支持向量分类
     */
    public static Strategy createSVMStrategy(BarSeries series) {
        if (series.getBarCount() <= 30) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 特征向量：多维技术指标
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        SMAIndicator sma = new SMAIndicator(closePrice, 20);
        SMAIndicator avgVolume = new SMAIndicator(volume, 20);
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 14);

        // 支持向量：定义分类边界（更宽松条件）
        Rule boundary1 = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(35))
                .and(new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(75))); // 放宽RSI范围
        Rule boundary2 = new OverIndicatorRule(macd, Ta4jNumUtil.valueOf(0));
        Rule boundary3 = new OverIndicatorRule(closePrice, sma);
        Rule boundary4 = new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.1)));
        Rule boundary5 = new UnderIndicatorRule(volatility, Ta4jNumUtil.valueOf(2.0));

        // SVM分类：至少3个支持向量支持（多数决策）
        Rule entryRule = boundary1.and(boundary2).and(boundary3)
                .or(boundary1.and(boundary2).and(boundary4))
                .or(boundary1.and(boundary2).and(boundary5))
                .or(boundary1.and(boundary3).and(boundary4))
                .or(boundary1.and(boundary3).and(boundary5))
                .or(boundary1.and(boundary4).and(boundary5))
                .or(boundary2.and(boundary3).and(boundary4))
                .or(boundary2.and(boundary3).and(boundary5))
                .or(boundary2.and(boundary4).and(boundary5))
                .or(boundary3.and(boundary4).and(boundary5));

        // SVM卖出：支持向量反转
        Rule exitRule = new UnderIndicatorRule(closePrice, sma)
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(80)))
                .or(new UnderIndicatorRule(macd, Ta4jNumUtil.valueOf(0)));

        return new BaseStrategy("SVM策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 创建LSTM策略（修复版）- 简化为时间序列记忆策略
     */
    public static Strategy createLSTMStrategy(BarSeries series) {
        if (series.getBarCount() <= 50) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 短期记忆（类似LSTM的短期状态）
        SMAIndicator shortMemory = new SMAIndicator(closePrice, 5);
        RSIIndicator shortRSI = new RSIIndicator(closePrice, 7);

        // 长期记忆（类似LSTM的长期状态）
        SMAIndicator longMemory = new SMAIndicator(closePrice, 30);
        SMAIndicator longVolumeMemory = new SMAIndicator(volume, 30);

        // 遗忘门：决定是否忘记旧信息（降低阈值）
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 10);
        SMAIndicator avgVolatility = new SMAIndicator(volatility, 5);
        Rule forgetGate = new UnderIndicatorRule(volatility, TransformIndicator.multiply(avgVolatility, 1.2)); // 使用相对波动率

        // 输入门：决定是否接受新信息（降低成交量要求）
        Rule inputGate = new OverIndicatorRule(volume, TransformIndicator.multiply(longVolumeMemory, BigDecimal.valueOf(1.05))); // 降低成交量要求

        // 输出门：决定输出什么信息（放宽条件）
        Rule outputGate = new OverIndicatorRule(shortMemory, longMemory) // 短期>长期
                .and(new OverIndicatorRule(shortRSI, Ta4jNumUtil.valueOf(40))) // 降低RSI阈值
                .and(new UnderIndicatorRule(shortRSI, Ta4jNumUtil.valueOf(80))); // 提高RSI上限

        // LSTM输出：综合所有门的决策（改为或逻辑，降低门槛）
        Rule entryRule = forgetGate.and(inputGate)
                .or(forgetGate.and(outputGate))
                .or(inputGate.and(outputGate));

        // LSTM卖出：记忆衰减或趋势反转
        Rule exitRule = new UnderIndicatorRule(shortMemory, longMemory)
                .or(new OverIndicatorRule(shortRSI, Ta4jNumUtil.valueOf(80)))
                .or(new OverIndicatorRule(volatility, Ta4jNumUtil.valueOf(3.0)));

        return new BaseStrategy("LSTM策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 创建KNN策略（修复版）- 基于历史相似模式预测
     */
    public static Strategy createKNNStrategy(BarSeries series) {
        if (series.getBarCount() <= 30) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 特征提取：多维特征向量
        ROCIndicator priceChange = new ROCIndicator(closePrice, 1);
        RSIIndicator momentum = new RSIIndicator(closePrice, 14);
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 5);
        SMAIndicator sma = new SMAIndicator(closePrice, 20);
        SMAIndicator avgVolume = new SMAIndicator(volume, 10);

        // K=3最近邻：寻找3个相似模式
        // 邻居1：价格上涨 + 动量良好
        Rule neighbor1 = new OverIndicatorRule(priceChange, Ta4jNumUtil.valueOf(0.005)) // 降低阈值
                .and(new OverIndicatorRule(momentum, Ta4jNumUtil.valueOf(45)))
                .and(new OverIndicatorRule(closePrice, sma));

        // 邻居2：成交量确认 + 低波动
        Rule neighbor2 = new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.1)))
                .and(new UnderIndicatorRule(volatility, Ta4jNumUtil.valueOf(1.8)))
                .and(new OverIndicatorRule(momentum, Ta4jNumUtil.valueOf(40)));

        // 邻居3：趋势延续模式
        Rule neighbor3 = new OverIndicatorRule(momentum, Ta4jNumUtil.valueOf(35))
                .and(new UnderIndicatorRule(momentum, Ta4jNumUtil.valueOf(75)))
                .and(new OverIndicatorRule(priceChange, Ta4jNumUtil.valueOf(0)));

        // KNN投票：至少2个邻居支持
        Rule knnBuy = neighbor1.and(neighbor2)
                .or(neighbor1.and(neighbor3))
                .or(neighbor2.and(neighbor3));

        // KNN卖出：邻居模式反转
        Rule knnSell = new UnderIndicatorRule(momentum, Ta4jNumUtil.valueOf(30))
                .or(new OverIndicatorRule(momentum, Ta4jNumUtil.valueOf(80)))
                .or(new UnderIndicatorRule(closePrice, sma));

        return new BaseStrategy("KNN策略", knnBuy, addExtraStopRule(knnSell, series));
    }

    /**
     * 创建朴素贝叶斯策略（修复版）- 基于贝叶斯概率预测
     */
    public static Strategy createNaiveBayesStrategy(BarSeries series) {
        if (series.getBarCount() <= 30) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 特征独立性假设：各指标独立计算概率
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        ROCIndicator roc = new ROCIndicator(closePrice, 10);
        SMAIndicator sma = new SMAIndicator(closePrice, 20);
        SMAIndicator avgVolume = new SMAIndicator(volume, 15);

        // 先验概率P(买入)：基于历史统计（放宽条件）
        Rule prior1 = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(35))
                .and(new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(75))); // 扩大RSI范围
        Rule prior2 = new OverIndicatorRule(macd, Ta4jNumUtil.valueOf(0));
        Rule prior3 = new OverIndicatorRule(roc, Ta4jNumUtil.valueOf(-0.005)); // 允许小幅下跌
        Rule prior4 = new OverIndicatorRule(closePrice, sma);
        Rule prior5 = new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.05))); // 降低成交量要求

        // 后验概率P(买入|特征)：贝叶斯更新（至少4个条件满足）
        Rule bayesBuy = prior1.and(prior2).and(prior3).and(prior4)
                .or(prior1.and(prior2).and(prior3).and(prior5))
                .or(prior1.and(prior2).and(prior4).and(prior5))
                .or(prior1.and(prior3).and(prior4).and(prior5))
                .or(prior2.and(prior3).and(prior4).and(prior5));

        // 后验概率P(卖出|特征)：反向贝叶斯更新
        Rule bayesSell = new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(25))
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(80)))
                .or(new UnderIndicatorRule(closePrice, sma))
                .or(new UnderIndicatorRule(macd, Ta4jNumUtil.valueOf(0)));

        return new BaseStrategy("朴素贝叶斯策略", bayesBuy, addExtraStopRule(bayesSell, series));
    }

    /**
     * 创建决策树策略（修复版）- 基于规则化决策树
     */
    public static Strategy createDecisionTreeStrategy(BarSeries series) {
        if (series.getBarCount() <= 30) {
            throw new IllegalArgumentException("数据点不足以计算指标");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 决策树节点特征
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        SMAIndicator sma = new SMAIndicator(closePrice, 20);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        SMAIndicator avgVolume = new SMAIndicator(volume, 15);
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 10);

        // 根节点：RSI阈值判断（降低门槛）
        Rule rootNode = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(45)); // 降低阈值（原来50）

        // 左分支：RSI > 45时的决策路径
        Rule leftBranch = rootNode
                .and(new OverIndicatorRule(closePrice, sma))
                .and(new OverIndicatorRule(macd, Ta4jNumUtil.valueOf(0)))
                .and(new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.1))));

        // 右分支：RSI <= 45时的决策路径（逆向策略）
        Rule rightBranch = new NotRule(rootNode)
                .and(new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(35))) // 超卖
                .and(new UnderIndicatorRule(volatility, Ta4jNumUtil.valueOf(2.0))) // 低波动
                .and(new OverIndicatorRule(volume, avgVolume)); // 成交量确认

        // 决策树买入：任一分支满足
        Rule treeBuy = leftBranch.or(rightBranch);

        // 决策树卖出：条件反转
        Rule treeSell = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(75))
                .or(new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(25)))
                .or(new UnderIndicatorRule(closePrice, sma))
                .or(new OverIndicatorRule(volatility, Ta4jNumUtil.valueOf(3.0)));

        return new BaseStrategy("决策树策略", treeBuy, addExtraStopRule(treeSell, series));
    }

    /**
     * 集成学习策略（修改版）- 放宽条件使其更容易触发交易
     */
    public static Strategy createEnsembleStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 模型1：趋势模型
        SMAIndicator shortSMA = new SMAIndicator(closePrice, 10);
        SMAIndicator longSMA = new SMAIndicator(closePrice, 30);
        Rule trendModel = new OverIndicatorRule(shortSMA, longSMA);

        // 模型2：动量模型
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        Rule momentumModel = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(45))  // 降低RSI阈值为45（原为50）
                .and(new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(70)));

        // 模型3：波动率模型 - 使用自定义指标而非布林带
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, 20);

        // 自定义上轨指标
        Indicator<Num> upperBand = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return sma20.getValue(index).plus(stdDev.getValue(index).multipliedBy(Ta4jNumUtil.valueOf(1.8))); // 1.8倍标准差
            }
        };

        // 自定义下轨指标
        Indicator<Num> lowerBand = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return sma20.getValue(index).minus(stdDev.getValue(index).multipliedBy(Ta4jNumUtil.valueOf(1.8))); // 1.8倍标准差
            }
        };

        Rule volatilityModel = new UnderIndicatorRule(closePrice, upperBand)
                .and(new OverIndicatorRule(closePrice, lowerBand));

        // 模型4：突破模型 - 使用自定义最高价指标
        Indicator<Num> highestHigh = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final HighPriceIndicator highPrice = new HighPriceIndicator(series);
            private final int period = 20;

            @Override
            protected Num calculate(int index) {
                int startIndex = Math.max(0, index - period + 1);
                Num highest = highPrice.getValue(startIndex);

                for (int i = startIndex + 1; i <= index; i++) {
                    Num current = highPrice.getValue(i);
                    if (current.isGreaterThan(highest)) {
                        highest = current;
                    }
                }

                return highest;
            }
        };

        Rule breakoutModel = new CrossedUpIndicatorRule(closePrice, highestHigh);

        // 模型投票：允许趋势模型单独触发，或者其他模型中的两个同时触发（降低要求，原为三个模型同时触发）
        Rule entryRule = trendModel
                .or(momentumModel.and(volatilityModel))
                .or(momentumModel.and(breakoutModel))
                .or(volatilityModel.and(breakoutModel));

        // 卖出条件：任一模型发出卖出信号
        Rule exitTrendModel = new UnderIndicatorRule(shortSMA, longSMA);
        Rule exitMomentumModel = new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(40)) // 降低RSI卖出阈值为40（原为45）
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(75))); // 降低RSI超买阈值为75（原为80）
        Rule exitVolatilityModel = new OverIndicatorRule(closePrice, upperBand)
                .or(new UnderIndicatorRule(closePrice, lowerBand));

        // 止盈条件
        Indicator<Num> profitTarget = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                if (index == 0) return Ta4jNumUtil.valueOf(0);
                return closePrice.getValue(index - 1).multipliedBy(Ta4jNumUtil.valueOf(1.09)); // 9%止盈条件
            }
        };

        // 卖出规则：任一模型发出卖出信号或达到止盈
        Rule exitRule = exitTrendModel
                .or(exitMomentumModel)
                .or(exitVolatilityModel)
                .or(new OverIndicatorRule(closePrice, profitTarget));

        return new BaseStrategy("集成学习策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 100. 强化学习策略 - 基于Q学习的自适应策略
     */
    public static Strategy createReinforcementLearningStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 状态空间：市场状态
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 20);

        // 动作空间：买入、卖出、持有（降低阈值）
        SMAIndicator sma = new SMAIndicator(closePrice, 20);
        VolumeIndicator volume = new VolumeIndicator(series);
        SMAIndicator avgVolume = new SMAIndicator(volume, 20);

        Rule action1 = new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(35)) // 降低RSI阈值
                .and(new OverIndicatorRule(macd, Ta4jNumUtil.valueOf(-0.001))); // 允许MACD轻微为负
        Rule action2 = new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(65)) // 降低RSI阈值
                .and(new UnderIndicatorRule(macd, Ta4jNumUtil.valueOf(0.001))); // 允许MACD轻微为正

        // 奖励函数：基于收益的奖励（降低阈值）
        ROCIndicator reward = new ROCIndicator(closePrice, 1);
        Rule positiveReward = new OverIndicatorRule(reward, Ta4jNumUtil.valueOf(0.005)); // 降低收益阈值

        // 探索vs利用（使用相对波动率）
        SMAIndicator avgVolatility = new SMAIndicator(volatility, 10);
        Rule exploration = new OverIndicatorRule(volatility, TransformIndicator.multiply(avgVolatility, 1.2));
        Rule exploitation = new UnderIndicatorRule(volatility, avgVolatility);

        // 简化强化学习逻辑
        Rule entryRule = action1.or(new OverIndicatorRule(closePrice, sma)); // 增加均线突破条件
        Rule exitRule = action2.or(new UnderIndicatorRule(closePrice, sma)); // 增加均线跌破条件

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    // ==================== 量化因子策略 (101-105) ====================

    /**
     * 101. 动量因子策略
     */
    public static Strategy createMomentumFactorStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 短期动量
        ROCIndicator shortMomentum = new ROCIndicator(closePrice, 20);
        // 长期动量
        ROCIndicator longMomentum = new ROCIndicator(closePrice, 60);

        // 动量信号
        Rule entryRule = new OverIndicatorRule(shortMomentum, 0.02)
                .and(new OverIndicatorRule(longMomentum, 0.05));
        Rule exitRule = new UnderIndicatorRule(shortMomentum, -0.01);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 102. 价值因子策略
     */
    public static Strategy createValueFactorStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 价值回归：价格偏离短期均值（进一步降低要求）
        SMAIndicator shortAvg = new SMAIndicator(closePrice, 20);
        SMAIndicator mediumAvg = new SMAIndicator(closePrice, 50);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // 价值信号：价格低于中期均线且RSI超卖（更宽松条件）
        Rule entryRule = new UnderIndicatorRule(closePrice, mediumAvg)
                .and(new UnderIndicatorRule(rsi, Ta4jNumUtil.valueOf(45))); // 放宽到45
        Rule exitRule = new OverIndicatorRule(closePrice, shortAvg)
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(65))); // 提前退出

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 103. 质量因子策略
     */
    public static Strategy createQualityFactorStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 质量指标：稳定性和趋势
        StandardDeviationIndicator stability = new StandardDeviationIndicator(closePrice, 30);
        SMAIndicator shortTrend = new SMAIndicator(closePrice, 10);
        SMAIndicator longTrend = new SMAIndicator(closePrice, 30);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // 高质量信号：相对低波动率 + 上升趋势（使用相对指标）
        SMAIndicator avgStability = new SMAIndicator(stability, 20);

        Rule entryRule = new UnderIndicatorRule(stability, TransformIndicator.multiply(avgStability, 1.2)) // 相对低波动
                .and(new OverIndicatorRule(shortTrend, longTrend)) // 短期均线>长期均线
                .or(new OverIndicatorRule(rsi, Ta4jNumUtil.valueOf(40))); // 降低RSI阈值或单独RSI条件

        Rule exitRule = new OverIndicatorRule(stability, TransformIndicator.multiply(avgStability, 1.8))
                .or(new UnderIndicatorRule(shortTrend, longTrend));

        return new BaseStrategy("质量因子策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 104. 规模因子策略
     */
    public static Strategy createSizeFactorStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 规模效应：小盘股溢价
        SMAIndicator avgPrice = new SMAIndicator(closePrice, 252);
        SMAIndicator avgVolume = new SMAIndicator(volume, 252);

        // 小规模信号
        Rule entryRule = new UnderIndicatorRule(closePrice, avgPrice)
                .and(new UnderIndicatorRule(volume, avgVolume));
        Rule exitRule = new OverIndicatorRule(closePrice, avgPrice);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 105. 低波动因子策略
     */
    public static Strategy createLowVolatilityFactorStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 相对波动率策略（更实用的方法）
        StandardDeviationIndicator shortVol = new StandardDeviationIndicator(closePrice, 10);
        StandardDeviationIndicator longVol = new StandardDeviationIndicator(closePrice, 30);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        SMAIndicator sma = new SMAIndicator(closePrice, 20);

        // 低波动信号：短期波动低于长期波动且有趋势
        Rule entryRule = new UnderIndicatorRule(shortVol, longVol)
                .and(new OverIndicatorRule(closePrice, sma)); // 上涨趋势
        Rule exitRule = new OverIndicatorRule(shortVol, longVol)
                .or(new UnderIndicatorRule(closePrice, sma));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    // ==================== 高频和微观结构策略 (106-110) ====================

    /**
     * 106. 微观结构不平衡策略
     */
    public static Strategy createMicrostructureImbalanceStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 订单流失衡模拟
        ROCIndicator priceChange = new ROCIndicator(closePrice, 1);
        ROCIndicator volumeChange = new ROCIndicator(volume, 1);

        // 失衡信号
        Rule entryRule = new OverIndicatorRule(priceChange, Ta4jNumUtil.valueOf(0.005))
                .and(new OverIndicatorRule(volumeChange, Ta4jNumUtil.valueOf(0.5)));
        Rule exitRule = new UnderIndicatorRule(priceChange, Ta4jNumUtil.valueOf(-0.002));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 107. 日内均值回归策略
     */
    public static Strategy createMeanReversionIntradayStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 日内均值回归
        SMAIndicator intraAvg = new SMAIndicator(closePrice, 60); // 60分钟均值
        StandardDeviationIndicator intraStd = new StandardDeviationIndicator(closePrice, 60);

        // 均值回归信号
        Rule entryRule = new UnderIndicatorRule(closePrice, intraAvg)
                .and(new OverIndicatorRule(intraStd, Ta4jNumUtil.valueOf(1.0)));
        Rule exitRule = new OverIndicatorRule(closePrice, intraAvg);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 108. 日内动量策略
     */
    public static Strategy createMomentumIntradayStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 日内动量
        ROCIndicator shortMomentum = new ROCIndicator(closePrice, 15); // 15分钟动量
        SMAIndicator avgMomentum = new SMAIndicator(shortMomentum, 30);

        // 动量信号
        Rule entryRule = new OverIndicatorRule(shortMomentum, Ta4jNumUtil.valueOf(0.005))
                .and(new OverIndicatorRule(shortMomentum, avgMomentum));
        Rule exitRule = new UnderIndicatorRule(shortMomentum, Ta4jNumUtil.valueOf(0));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 109. 统计套利策略
     */
    public static Strategy createArbitrageStatisticalStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 统计套利：价格偏离统计规律
        SMAIndicator avgPrice = new SMAIndicator(closePrice, 60);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, 60);

        // 创建自定义下轨指标
        class LowerBandIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final SMAIndicator avgPrice;
            private final StandardDeviationIndicator stdDev;
            private final Num multiplier;

            public LowerBandIndicator(SMAIndicator avgPrice, StandardDeviationIndicator stdDev, double multiplier, BarSeries series) {
                super(series);
                this.avgPrice = avgPrice;
                this.stdDev = stdDev;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return avgPrice.getValue(index).minus(stdDev.getValue(index).multipliedBy(multiplier));
            }
        }

        LowerBandIndicator lowerBand = new LowerBandIndicator(avgPrice, stdDev, 2.0, series);
        LowerBandIndicator exitBand = new LowerBandIndicator(avgPrice, stdDev, 0.5, series);

        // 套利信号
        Rule entryRule = new UnderIndicatorRule(closePrice, lowerBand);
        Rule exitRule = new OverIndicatorRule(closePrice, exitBand);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 110. 配对交易策略
     */
    public static Strategy createPairsTradingStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 配对交易：价差回归
        SMAIndicator priceMean = new SMAIndicator(closePrice, 60);
        StandardDeviationIndicator priceStd = new StandardDeviationIndicator(closePrice, 60);

        // 创建自定义指标
        class PairsLowerBandIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final SMAIndicator priceMean;
            private final StandardDeviationIndicator priceStd;
            private final Num multiplier;

            public PairsLowerBandIndicator(SMAIndicator priceMean, StandardDeviationIndicator priceStd, double multiplier, BarSeries series) {
                super(series);
                this.priceMean = priceMean;
                this.priceStd = priceStd;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return priceMean.getValue(index).minus(priceStd.getValue(index).multipliedBy(multiplier));
            }
        }

        PairsLowerBandIndicator entryBand = new PairsLowerBandIndicator(priceMean, priceStd, 2.0, series);
        PairsLowerBandIndicator exitBand = new PairsLowerBandIndicator(priceMean, priceStd, 0.5, series);

        // 价差信号
        Rule entryRule = new UnderIndicatorRule(closePrice, entryBand);
        Rule exitRule = new OverIndicatorRule(closePrice, exitBand);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    // ==================== 期权和波动率策略 (111-115) ====================

    /**
     * 111. 波动率曲面策略
     */
    public static Strategy createVolatilitySurfaceStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 波动率曲面分析
        StandardDeviationIndicator shortVol = new StandardDeviationIndicator(closePrice, 10);
        StandardDeviationIndicator longVol = new StandardDeviationIndicator(closePrice, 30);

        // 创建自定义波动率比较指标
        class VolatilityThresholdIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final StandardDeviationIndicator longVol;
            private final Num multiplier;

            public VolatilityThresholdIndicator(StandardDeviationIndicator longVol, double multiplier, BarSeries series) {
                super(series);
                this.longVol = longVol;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return longVol.getValue(index).multipliedBy(multiplier);
            }
        }

        VolatilityThresholdIndicator volThreshold = new VolatilityThresholdIndicator(longVol, 1.2, series);

        // 波动率结构信号
        Rule entryRule = new OverIndicatorRule(shortVol, volThreshold);
        Rule exitRule = new UnderIndicatorRule(shortVol, longVol);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 112. Gamma剥头皮策略
     */
    public static Strategy createGammaScalpingStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // Gamma交易模拟 - 使用更合适的参数
        ROCIndicator priceChange = new ROCIndicator(closePrice, 1);
        StandardDeviationIndicator gamma = new StandardDeviationIndicator(priceChange, 20); // 降低周期
        SMAIndicator avgGamma = new SMAIndicator(gamma, 10); // 添加均值参考

        // Gamma信号 - 降低入场门槛，增加相对比较
        Rule entryRule = new OverIndicatorRule(gamma, avgGamma)
                .or(new OverIndicatorRule(priceChange, Ta4jNumUtil.valueOf(0.005))); // 添加价格变化率条件
        Rule exitRule = new UnderIndicatorRule(gamma, avgGamma)
                .or(new UnderIndicatorRule(priceChange, Ta4jNumUtil.valueOf(-0.005)));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 113. 波动率均值回归策略
     */
    public static Strategy createVolatilityMeanReversionStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 波动率均值回归
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 20);
        SMAIndicator avgVolatility = new SMAIndicator(volatility, 60);

        // 创建自定义波动率阈值指标
        class VolatilityMultiplierIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final SMAIndicator avgVolatility;
            private final Num multiplier;

            public VolatilityMultiplierIndicator(SMAIndicator avgVolatility, double multiplier, BarSeries series) {
                super(series);
                this.avgVolatility = avgVolatility;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return avgVolatility.getValue(index).multipliedBy(multiplier);
            }
        }

        VolatilityMultiplierIndicator volThreshold = new VolatilityMultiplierIndicator(avgVolatility, 2.0, series);

        // 波动率回归信号
        Rule entryRule = new OverIndicatorRule(volatility, volThreshold);
        Rule exitRule = new UnderIndicatorRule(volatility, avgVolatility);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 114. 波动率动量策略
     */
    public static Strategy createVolatilityMomentumStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 波动率动量
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 10);
        ROCIndicator volMomentum = new ROCIndicator(volatility, 5);

        // 波动率动量信号
        Rule entryRule = new OverIndicatorRule(volMomentum, Ta4jNumUtil.valueOf(0.01));
        Rule exitRule = new UnderIndicatorRule(volMomentum, Ta4jNumUtil.valueOf(-0.005));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 115. 隐含波动率排名策略
     */
    public static Strategy createImpliedVolatilityRankStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 隐含波动率排名模拟
        StandardDeviationIndicator currentVol = new StandardDeviationIndicator(closePrice, 20);
        StandardDeviationIndicator historicalVol = new StandardDeviationIndicator(closePrice, 252);

        // 创建自定义波动率比较指标
        class HistoricalVolatilityIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final StandardDeviationIndicator historicalVol;
            private final Num multiplier;

            public HistoricalVolatilityIndicator(StandardDeviationIndicator historicalVol, double multiplier, BarSeries series) {
                super(series);
                this.historicalVol = historicalVol;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return historicalVol.getValue(index).multipliedBy(multiplier);
            }
        }

        HistoricalVolatilityIndicator lowThreshold = new HistoricalVolatilityIndicator(historicalVol, 0.8, series);
        HistoricalVolatilityIndicator highThreshold = new HistoricalVolatilityIndicator(historicalVol, 1.2, series);

        // 相对波动率信号
        Rule entryRule = new UnderIndicatorRule(currentVol, lowThreshold);
        Rule exitRule = new OverIndicatorRule(currentVol, highThreshold);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    // ==================== 宏观和基本面策略 (116-120) ====================

    /**
     * 116. 利差交易策略
     */
    public static Strategy createCarryTradeStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 利差模拟 - 使用更短的周期
        SMAIndicator shortTerm = new SMAIndicator(closePrice, 5); // 短期均线改为5天
        SMAIndicator longTerm = new SMAIndicator(closePrice, 30);  // 长期均线改为30天

        // 创建利差指标
        class CarryIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final SMAIndicator shortTerm;
            private final SMAIndicator longTerm;

            public CarryIndicator(SMAIndicator shortTerm, SMAIndicator longTerm, BarSeries series) {
                super(series);
                this.shortTerm = shortTerm;
                this.longTerm = longTerm;
            }

            @Override
            protected Num calculate(int index) {
                return shortTerm.getValue(index).minus(longTerm.getValue(index));
            }
        }

        CarryIndicator carry = new CarryIndicator(shortTerm, longTerm, series);

        // 利差信号 - 降低条件严格性
        Rule entryRule = new OverIndicatorRule(carry, Ta4jNumUtil.valueOf(0)) // 只要短期均线高于长期均线即可
                .or(new OverIndicatorRule(closePrice, shortTerm)); // 增加一个入场条件
        Rule exitRule = new UnderIndicatorRule(closePrice, shortTerm); // 价格低于短期均线时退出

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 117. 基本面评分策略
     */
    public static Strategy createFundamentalScoreStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 基本面评分模拟
        ROCIndicator growth = new ROCIndicator(closePrice, 252);
        StandardDeviationIndicator stability = new StandardDeviationIndicator(closePrice, 252);
        SMAIndicator avgVolume = new SMAIndicator(volume, 252);

        // 综合评分信号（降低阈值）
        SMAIndicator avgGrowth = new SMAIndicator(growth, 20);
        SMAIndicator avgStability = new SMAIndicator(stability, 20);

        Rule entryRule = new OverIndicatorRule(growth, avgGrowth) // 使用相对增长率
                .and(new UnderIndicatorRule(stability, TransformIndicator.multiply(avgStability, 1.2))) // 相对稳定性
                .and(new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, 0.8))); // 降低成交量要求

        Rule exitRule = new UnderIndicatorRule(growth, TransformIndicator.multiply(avgGrowth, 0.8));

        return new BaseStrategy("基本面评分策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 118. 宏观动量策略
     */
    public static Strategy createMacroMomentumStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 宏观动量模拟
        ROCIndicator longTermMomentum = new ROCIndicator(closePrice, 252);
        ROCIndicator mediumTermMomentum = new ROCIndicator(closePrice, 60);

        // 宏观信号
        Rule entryRule = new OverIndicatorRule(longTermMomentum, Ta4jNumUtil.valueOf(0.15))
                .and(new OverIndicatorRule(mediumTermMomentum, Ta4jNumUtil.valueOf(0.05)));
        Rule exitRule = new UnderIndicatorRule(longTermMomentum, Ta4jNumUtil.valueOf(0.05));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 119. 季节性策略
     */
    public static Strategy createSeasonalityStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 季节性效应模拟
        SMAIndicator monthlyAvg = new SMAIndicator(closePrice, 21); // 月度均值
        SMAIndicator quarterlyAvg = new SMAIndicator(closePrice, 63); // 季度均值

        // 季节性信号
        Rule entryRule = new OverIndicatorRule(monthlyAvg, quarterlyAvg)
                .and(new OverIndicatorRule(closePrice, monthlyAvg));
        Rule exitRule = new UnderIndicatorRule(monthlyAvg, quarterlyAvg);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 120. 日历价差策略
     */
    public static Strategy createCalendarSpreadStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 日历价差模拟
        SMAIndicator nearTerm = new SMAIndicator(closePrice, 30);
        SMAIndicator farTerm = new SMAIndicator(closePrice, 90);

        // 创建自定义价差指标
        class CalendarSpreadIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final SMAIndicator farTerm;
            private final Num multiplier;

            public CalendarSpreadIndicator(SMAIndicator farTerm, double multiplier, BarSeries series) {
                super(series);
                this.farTerm = farTerm;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return farTerm.getValue(index).multipliedBy(multiplier);
            }
        }

        CalendarSpreadIndicator lowerThreshold = new CalendarSpreadIndicator(farTerm, 0.95, series);
        CalendarSpreadIndicator upperThreshold = new CalendarSpreadIndicator(farTerm, 1.05, series);

        // 日历价差信号
        Rule entryRule = new UnderIndicatorRule(nearTerm, lowerThreshold);
        Rule exitRule = new OverIndicatorRule(nearTerm, upperThreshold);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    // ==================== 创新和实验性策略 (121-125) ====================

    /**
     * 121. 情绪分析策略
     */
    public static Strategy createSentimentAnalysisStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 情绪指标模拟
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 20);
        ROCIndicator priceChange = new ROCIndicator(closePrice, 1);
        SMAIndicator avgVolume = new SMAIndicator(volume, 20);

        // 市场情绪信号 - 修正逻辑错误
        // 创建成交量阈值指标
        TransformIndicator volumeThreshold = TransformIndicator.multiply(avgVolume, 1.2);

        Rule bullishSentiment = new OverIndicatorRule(priceChange, Ta4jNumUtil.valueOf(0.005))  // 价格上涨
                .or(new OverIndicatorRule(volume, volumeThreshold)); // 成交量放大

        Rule bearishSentiment = new UnderIndicatorRule(priceChange, Ta4jNumUtil.valueOf(-0.005))  // 价格下跌
                .or(new OverIndicatorRule(volatility, Ta4jNumUtil.valueOf(1.5)));  // 波动率升高

        Rule entryRule = bullishSentiment;
        Rule exitRule = bearishSentiment;

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 122. 网络分析策略
     */
    public static Strategy createNetworkAnalysisStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 网络效应模拟 - 改进指标选择
        ROCIndicator centrality = new ROCIndicator(closePrice, 10); // 减少周期
        StandardDeviationIndicator connectivity = new StandardDeviationIndicator(closePrice, 10); // 减少周期
        SMAIndicator avgConnectivity = new SMAIndicator(connectivity, 5); // 添加参考

        // 网络信号 - 降低条件严格性
        Rule entryRule = new OverIndicatorRule(centrality, Ta4jNumUtil.valueOf(0.01)) // 降低阈值
                .or(new OverIndicatorRule(connectivity, avgConnectivity)); // 使用相对比较
        Rule exitRule = new UnderIndicatorRule(centrality, Ta4jNumUtil.valueOf(-0.01)) // 添加负值条件
                .and(new UnderIndicatorRule(connectivity, avgConnectivity));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 123. 分形几何策略
     */
    public static Strategy createFractalGeometryStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 分形结构模拟 - 降低条件严格性
        StandardDeviationIndicator fractalDim = new StandardDeviationIndicator(closePrice, 10); // 减少周期
        ROCIndicator hurst = new ROCIndicator(closePrice, 20); // 减少周期
        SMAIndicator avgFractal = new SMAIndicator(fractalDim, 5); // 添加平均值参考

        // 分形信号 - 使用相对比较
        Rule entryRule = new OverIndicatorRule(fractalDim, avgFractal) // 相对比较
                .and(new OverIndicatorRule(hurst, Ta4jNumUtil.valueOf(0.01))); // 降低阈值
        Rule exitRule = new UnderIndicatorRule(fractalDim, avgFractal) // 相对比较
                .or(new UnderIndicatorRule(hurst, Ta4jNumUtil.valueOf(0)));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 124. 混沌理论策略
     */
    public static Strategy createChaosTheoryStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 混沌系统模拟 - 使用更短周期
        StandardDeviationIndicator lyapunov = new StandardDeviationIndicator(closePrice, 10); // 从20降到10
        ROCIndicator attractor = new ROCIndicator(closePrice, 5); // 从10降到5
        SMAIndicator avgLyapunov = new SMAIndicator(lyapunov, 5); // 添加均值参考

        // 混沌信号 - 降低入场门槛
        Rule entryRule = new OverIndicatorRule(lyapunov, avgLyapunov) // 相对比较而非绝对值
                .or(new OverIndicatorRule(attractor, Ta4jNumUtil.valueOf(0.01))); // 从0.02降到0.01
        Rule exitRule = new UnderIndicatorRule(attractor, Ta4jNumUtil.valueOf(0))
                .and(new UnderIndicatorRule(lyapunov, avgLyapunov));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 量子启发策略（修改版）- 基于量子概念的多概率状态策略，放宽条件使其更容易触发交易
     */
    public static Strategy createQuantumInspiredStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 量子态1：价格动量（使用ROC指标）
        ROCIndicator roc = new ROCIndicator(closePrice, 10);

        // 量子态2：波动率（使用标准差）
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, 14);

        // 量子态3：成交量变化
        SMAIndicator avgVolume = new SMAIndicator(volume, 20);

        // 量子纠缠：价格与成交量的相关性
        // 使用简化的相关性计算

        // 量子叠加：多个指标的组合判断
        // 买入条件：价格动量为正且大于0.5%（降低阈值），或者价格突破上轨
        Rule quantumBuy = new OverIndicatorRule(roc, Ta4jNumUtil.valueOf(0.005)) // 降低ROC阈值为0.5%（原为1%）
                .or(new OverIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(1.1)))); // 降低成交量要求（原为1.3）

        // 卖出条件：价格动量为负且小于-0.5%（降低阈值），或者价格跌破下轨
        Rule quantumSell = new UnderIndicatorRule(roc, Ta4jNumUtil.valueOf(-0.005)) // 降低ROC阈值为-0.5%（原为-1%）
                .or(new UnderIndicatorRule(volume, TransformIndicator.multiply(avgVolume, BigDecimal.valueOf(0.9)))); // 提高成交量阈值（原为0.7）

        // 增加止盈条件
        Indicator<Num> profitTarget = new CachedIndicator<Num>(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                if (index == 0) return Ta4jNumUtil.valueOf(0);
                return closePrice.getValue(index - 1).multipliedBy(Ta4jNumUtil.valueOf(1.05)); // 5%止盈条件
            }
        };

        // 修改后的卖出条件，增加止盈
        Rule exitRule = quantumSell.or(new OverIndicatorRule(closePrice, profitTarget));

        return new BaseStrategy("量子启发策略", quantumBuy, addExtraStopRule(exitRule, series));
    }

    // ==================== 风险管理策略 (126-130) ====================

    /**
     * 126. 凯利公式策略
     */
    public static Strategy createKellyCriterionStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 计算收益和风险
        ROCIndicator returns = new ROCIndicator(closePrice, 1);
        StandardDeviationIndicator risk = new StandardDeviationIndicator(closePrice, 14);  // 减少回看周期
        SMAIndicator avgReturns = new SMAIndicator(returns, 14);
        SMAIndicator avgRisk = new SMAIndicator(risk, 14);

        // 凯利比率模拟 - 降低入场条件严格性
        Rule entryRule = new OverIndicatorRule(returns, Ta4jNumUtil.valueOf(0.5).multipliedBy(avgReturns.getValue(series.getEndIndex())))  // 使用相对比较
                .and(new UnderIndicatorRule(risk, Ta4jNumUtil.valueOf(1.2).multipliedBy(avgRisk.getValue(series.getEndIndex()))));

        Rule exitRule = new UnderIndicatorRule(returns, Ta4jNumUtil.valueOf(0))
                .or(new OverIndicatorRule(risk, Ta4jNumUtil.valueOf(1.5).multipliedBy(avgRisk.getValue(series.getEndIndex()))));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 127. VaR风险管理策略
     */
    public static Strategy createVarRiskManagementStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // VaR计算 - 使用更短的周期和更合理的阈值
        ROCIndicator returns = new ROCIndicator(closePrice, 1);
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(returns, 20); // 减少周期
        SMAIndicator avgVolatility = new SMAIndicator(volatility, 10);

        // 风险控制信号 - 基于相对波动率而非绝对值
        Rule entryRule = new UnderIndicatorRule(volatility, avgVolatility); // 波动率低于平均时买入
        Rule exitRule = new OverIndicatorRule(volatility, TransformIndicator.multiply(avgVolatility, 1.5)); // 波动率高于平均1.5倍时卖出

        return new BaseStrategy("VaR风险管理策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 128. 最大回撤控制策略
     */
    public static Strategy createMaximumDrawdownControlStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 回撤控制
        HighestValueIndicator highestPrice = new HighestValueIndicator(closePrice, 60);

        // 创建自定义回撤阈值指标
        class DrawdownThresholdIndicator extends CachedIndicator<Num> {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            private final HighestValueIndicator highestPrice;
            private final Num multiplier;

            public DrawdownThresholdIndicator(HighestValueIndicator highestPrice, double multiplier, BarSeries series) {
                super(series);
                this.highestPrice = highestPrice;
                this.multiplier = Ta4jNumUtil.valueOf(multiplier);
            }

            @Override
            protected Num calculate(int index) {
                return highestPrice.getValue(index).multipliedBy(multiplier);
            }
        }

        DrawdownThresholdIndicator entryThreshold = new DrawdownThresholdIndicator(highestPrice, 0.95, series);
        DrawdownThresholdIndicator exitThreshold = new DrawdownThresholdIndicator(highestPrice, 0.9, series);

        // 回撤计算
        Rule entryRule = new OverIndicatorRule(closePrice, entryThreshold);
        Rule exitRule = new UnderIndicatorRule(closePrice, exitThreshold);

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 129. 头寸规模策略
     */
    public static Strategy createPositionSizingStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 头寸规模管理
        StandardDeviationIndicator volatility = new StandardDeviationIndicator(closePrice, 20);
        ROCIndicator returns = new ROCIndicator(closePrice, 1);

        // 风险调整头寸（使用相对指标）
        SMAIndicator avgVolatility = new SMAIndicator(volatility, 20);
        SMAIndicator avgReturns = new SMAIndicator(returns, 20);

        Rule entryRule = new UnderIndicatorRule(volatility, TransformIndicator.multiply(avgVolatility, 1.2)) // 相对低波动
                .and(new OverIndicatorRule(returns, TransformIndicator.multiply(avgReturns, 0.5))); // 相对正收益

        Rule exitRule = new OverIndicatorRule(volatility, TransformIndicator.multiply(avgVolatility, 1.8)); // 相对高波动

        return new BaseStrategy("头寸规模策略", entryRule, addExtraStopRule(exitRule, series));
    }

    /**
     * 130. 相关性过滤策略
     */
    public static Strategy createCorrelationFilterStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        // 相关性分析
        ROCIndicator priceChange = new ROCIndicator(closePrice, 1);
        ROCIndicator volumeChange = new ROCIndicator(volume, 1);

        // 低相关性信号
        Rule entryRule = new OverIndicatorRule(priceChange, Ta4jNumUtil.valueOf(0.01))
                .and(new UnderIndicatorRule(volumeChange, Ta4jNumUtil.valueOf(0.5)));
        Rule exitRule = new OverIndicatorRule(volumeChange, Ta4jNumUtil.valueOf(1.0));

        return new BaseStrategy(entryRule, addExtraStopRule(exitRule, series));
    }
}
