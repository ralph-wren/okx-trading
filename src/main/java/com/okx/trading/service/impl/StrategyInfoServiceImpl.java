package com.okx.trading.service.impl;

import com.okx.trading.model.entity.StrategyInfoEntity;
import com.okx.trading.repository.StrategyInfoRepository;
import com.okx.trading.service.StrategyInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 策略信息服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyInfoServiceImpl implements StrategyInfoService {

    private final StrategyInfoRepository strategyInfoRepository;

    @Override
    public List<StrategyInfoEntity> getAllStrategies() {
        return strategyInfoRepository.findAllByOrderByStrategyCodeAsc();
    }

    @Override
    public Optional<StrategyInfoEntity> getStrategyByCode(String strategyCode) {
        return strategyInfoRepository.findByStrategyCode(strategyCode);
    }

    @Override
    public Optional<StrategyInfoEntity> getStrategyById(Long id) {
        return strategyInfoRepository.findById(id);
    }

    @Override
    public List<StrategyInfoEntity> getStrategiesByCategory(String category) {
        return strategyInfoRepository.findByCategoryOrderByStrategyNameAsc(category);
    }

    @Override
    public StrategyInfoEntity saveStrategy(StrategyInfoEntity strategyInfo) {
        return strategyInfoRepository.save(strategyInfo);
    }

    @Override
    public List<StrategyInfoEntity> saveAllStrategies(List<StrategyInfoEntity> strategyInfoList) {
        return strategyInfoRepository.saveAll(strategyInfoList);
    }

    @Override
    public void deleteStrategy(Long id) {
        strategyInfoRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteStrategyByCode(String strategyCode) {
        strategyInfoRepository.deleteByStrategyCode(strategyCode);
    }

    @Override
    public String getDefaultParams(String strategyCode) {
        return strategyInfoRepository.findByStrategyCode(strategyCode)
                .map(StrategyInfoEntity::getDefaultParams)
                .orElse("");
    }

    @Override
    public Map<String, Map<String, Object>> getStrategiesInfo() {
        List<StrategyInfoEntity> strategies = getAllStrategies();
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (StrategyInfoEntity strategy : strategies) {
            // 有 load_error 的策略也返回，由前端根据 available /「隐藏不可用」控制是否展示
            Map<String, Object> strategyInfo = new HashMap<>();
            strategyInfo.put("id", String.valueOf(strategy.getId()));
            strategyInfo.put("name", strategy.getStrategyName());
            strategyInfo.put("description", strategy.getDescription());
            strategyInfo.put("comments", strategy.getComments());
            strategyInfo.put("default_params", strategy.getDefaultParams());
            strategyInfo.put("category", strategy.getCategory());
            strategyInfo.put("params", strategy.getParamsDesc());
            strategyInfo.put("strategy_code", strategy.getStrategyCode());
            strategyInfo.put("load_error", strategy.getLoadError());
            strategyInfo.put("update_time", String.valueOf(strategy.getUpdateTime()));
            strategyInfo.put("source_code",strategy.getSourceCode());
            result.put(strategy.getStrategyCode(), strategyInfo);
        }

        return result;
    }

    @Override
    public boolean existsByStrategyCode(String strategyCode) {
        return strategyInfoRepository.existsByStrategyCode(strategyCode);
    }
}
