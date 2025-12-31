package com.ruoyi.system.service;

import com.github.oxo42.stateless4j .StateMachine;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.ExpTaskSubmit;
import com.ruoyi.system.domain.enums.ReportState;
import com.ruoyi.system.domain.enums.ReportTrigger;
import com.ruoyi.system.service.statemachine.ReportStateMachineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实验报告状态机服务
 */
@Service
public class ReportStateMachineService {

    private static final Logger logger = LoggerFactory.getLogger(ReportStateMachineService.class);

    @Autowired
    private ReportStateMachineConfig stateMachineConfig;

    @Autowired
    private IExpTaskSubmitService submitService;

    /**
     * 触发状态转换
     *
     * @param submitId 提交记录ID
     * @param trigger  触发器
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean fire(Long submitId, ReportTrigger trigger) {
        try {
            // 获取当前提交记录
            ExpTaskSubmit submit = submitService.selectExpTaskSubmitBySubmitId(submitId);
            if (submit == null) {
                logger.error("提交记录不存在: {}", submitId);
                return false;
            }

            // 获取当前状态
            ReportState currentState = ReportState.fromCode(submit.getStatus());
            logger.info("当前状态: {}, 触发器: {}", currentState, trigger);

            // 创建状态机
            StateMachine<ReportState, ReportTrigger> stateMachine =
                stateMachineConfig.createStateMachine(currentState);

            // 检查是否允许触发
            if (!stateMachine.canFire(trigger)) {
                logger.warn("不允许的状态转换: {} -> {}", currentState, trigger);
                throw new ServiceException("当前状态不允许此操作");
            }

            // 触发状态转换
            stateMachine.fire(trigger);

            // 获取新状态
            ReportState newState = stateMachine.getState();
            logger.info("新状态: {}", newState);

            // 更新数据库
            submit.setStatus(newState.getCode());
            submitService.updateExpTaskSubmit(submit);

            return true;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("状态转换失败", e);
            throw new ServiceException("状态转换失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否允许触发
     *
     * @param submitId 提交记录ID
     * @param trigger  触发器
     * @return 是否允许
     */
    public boolean canFire(Long submitId, ReportTrigger trigger) {
        ExpTaskSubmit submit = submitService.selectExpTaskSubmitBySubmitId(submitId);
        if (submit == null) {
            return false;
        }

        ReportState currentState = ReportState.fromCode(submit.getStatus());
        return stateMachineConfig.canFire(currentState, trigger);
    }

    /**
     * 获取允许的操作列表
     *
     * @param submitId 提交记录ID
     * @return 允许的触发器列表
     */
    public Iterable<ReportTrigger> getPermittedTriggers(Long submitId) {
        ExpTaskSubmit submit = submitService.selectExpTaskSubmitBySubmitId(submitId);
        if (submit == null) {
            return null;
        }

        ReportState currentState = ReportState.fromCode(submit.getStatus());
        return stateMachineConfig.getPermittedTriggers(currentState);
    }

    /**
     * 提交报告
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean submitReport(Long submitId) {
        return fire(submitId, ReportTrigger.SUBMIT);
    }

    /**
     * 开始批阅
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean startReview(Long submitId) {
        return fire(submitId, ReportTrigger.START_REVIEW);
    }

    /**
     * 批阅通过
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long submitId) {
        return fire(submitId, ReportTrigger.APPROVE);
    }

    /**
     * 打回报告
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long submitId, String reason) {
        ExpTaskSubmit submit = submitService.selectExpTaskSubmitBySubmitId(submitId);
        if (submit != null) {
            submit.setRejectReason(reason);
            submitService.updateExpTaskSubmit(submit);
        }
        return fire(submitId, ReportTrigger.REJECT);
    }

    /**
     * 重新提交
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resubmit(Long submitId) {
        return fire(submitId, ReportTrigger.RESUBMIT);
    }

    /**
     * 归档
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean archive(Long submitId) {
        return fire(submitId, ReportTrigger.ARCHIVE);
    }
}
