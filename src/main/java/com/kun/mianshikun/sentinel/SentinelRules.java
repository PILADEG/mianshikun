package com.kun.mianshikun.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;

@Component
@Profile("local-rule")
public class SentinelRules {
    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }
    // 限流规则
    public void initFlowRules() {
        // 单 IP 查看题目列表限流规则
        ParamFlowRule rule = new ParamFlowRule(SentinelConstant.QUESTION_PAGE_NAME)
                .setParamIdx(0) // 对第 0 个参数限流，即 IP 地址
                .setCount(10) // 每分钟最多 60 次
                .setDurationInSec(60); // 规则的统计周期为 60 秒
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));

        FlowRule rule2 = new FlowRule(SentinelConstant.QUESTION_BANK_PAGE_NAME)
                .setCount(1) // 每秒最多 10 次
                .setGrade(RuleConstant.FLOW_GRADE_QPS); // 秒级限流
        FlowRuleManager.loadRules(Collections.singletonList(rule2));
    }
    // 降级规则
    public void initDegradeRules() {
        // 单 IP 查看题目列表熔断规则
        DegradeRule slowCallRule = new DegradeRule(SentinelConstant.QUESTION_PAGE_NAME)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                        .setCount(0.2) // 慢调用比例大于 20%
                        .setTimeWindow(60) // 熔断持续时间 60 秒
                        .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                        .setMinRequestAmount(10) // 最小请求数
                        .setSlowRatioThreshold(3); // 响应时间超过 3 秒
        DegradeRule errorRateRule = new DegradeRule(SentinelConstant.QUESTION_PAGE_NAME)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1) // 异常率大于 10%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10); // 最小请求数
        // 加载规则
        DegradeRule slowCallRule2 = new DegradeRule(SentinelConstant.QUESTION_BANK_PAGE_NAME)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 慢调用比例大于 20%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10) // 最小请求数
                .setSlowRatioThreshold(3); // 响应时间超过 3 秒
        DegradeRule errorRateRule2 = new DegradeRule(SentinelConstant.QUESTION_BANK_PAGE_NAME)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1) // 异常率大于 10%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10); // 最小请求数
        // 加载规则
        DegradeRuleManager.loadRules(Arrays.asList(slowCallRule, errorRateRule,
                slowCallRule2, errorRateRule2));
    }
}
