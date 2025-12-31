package com.ruoyi.system.service.statemachine;

import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;
import com.ruoyi.system.domain.enums.ReportState;
import com.ruoyi.system.domain.enums.ReportTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 实验报告状态机配置
 */
@Component
public class ReportStateMachineConfig {

    private static final Logger logger = LoggerFactory.getLogger(ReportStateMachineConfig.class);

    /**
     * 配置状态机
     */
    public StateMachineConfig<ReportState, ReportTrigger> configure() {
        StateMachineConfig<ReportState, ReportTrigger> config = new StateMachineConfig<>();

        // 草稿 -> 已提交
        config.configure(ReportState.DRAFT)
                .permit(ReportTrigger.SUBMIT, ReportState.SUBMITTED);

        // 已提交 -> 批阅中
        config.configure(ReportState.SUBMITTED)
                .permit(ReportTrigger.START_REVIEW, ReportState.REVIEWING);

        // 批阅中 -> 已批阅 或 打回
        config.configure(ReportState.REVIEWING)
                .permit(ReportTrigger.APPROVE, ReportState.REVIEWED)
                .permit(ReportTrigger.REJECT, ReportState.REJECTED);

        // 打回 -> 重新提交
        config.configure(ReportState.REJECTED)
                .permit(ReportTrigger.RESUBMIT, ReportState.RESUBMITTED);

        // 重新提交 -> 批阅中
        config.configure(ReportState.RESUBMITTED)
                .permit(ReportTrigger.START_REVIEW, ReportState.REVIEWING);

        // 已批阅 -> 批阅中（允许重新批改） 或 已归档
        config.configure(ReportState.REVIEWED)
                .permit(ReportTrigger.START_REVIEW, ReportState.REVIEWING)
                .permit(ReportTrigger.ARCHIVE, ReportState.ARCHIVED);

        return config;
    }

    /**
     * 创建状态机实例
     */
    public StateMachine<ReportState, ReportTrigger> createStateMachine(ReportState initialState) {
        StateMachineConfig<ReportState, ReportTrigger> config = configure();
        StateMachine<ReportState, ReportTrigger> stateMachine = new StateMachine<>(initialState, config);

        // 日志记录（简化版本，不使用监听器）
        logger.info("创建状态机，初始状态: {}", initialState);

        return stateMachine;
    }

    /**
     * 检查是否允许触发
     */
    public boolean canFire(ReportState currentState, ReportTrigger trigger) {
        StateMachine<ReportState, ReportTrigger> stateMachine = createStateMachine(currentState);
        return stateMachine.canFire(trigger);
    }

    /**
     * 获取允许的触发器列表
     */
    public Iterable<ReportTrigger> getPermittedTriggers(ReportState currentState) {
        StateMachine<ReportState, ReportTrigger> stateMachine = createStateMachine(currentState);
        return stateMachine.getPermittedTriggers();
    }
}
