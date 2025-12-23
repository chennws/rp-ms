package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.ExpTask;

/**
 * 实验任务Service接口
 * 
 * @author ruoyi
 * @date 2024-03-01
 */
public interface IExpTaskService 
{
    /**
     * 查询实验任务
     * 
     * @param taskId 实验任务主键
     * @return 实验任务
     */
    public ExpTask selectExpTaskByTaskId(Long taskId);

    /**
     * 查询实验任务列表
     * 
     * @param expTask 实验任务
     * @return 实验任务集合
     */
    public List<ExpTask> selectExpTaskList(ExpTask expTask);

    /**
     * 新增实验任务
     * 
     * @param expTask 实验任务
     * @return 结果
     */
    public int insertExpTask(ExpTask expTask);

    /**
     * 修改实验任务
     * 
     * @param expTask 实验任务
     * @return 结果
     */
    public int updateExpTask(ExpTask expTask);

    /**
     * 批量删除实验任务
     * 
     * @param taskIds 需要删除的实验任务主键集合
     * @return 结果
     */
    public int deleteExpTaskByTaskIds(Long[] taskIds);

    /**
     * 删除实验任务信息
     * 
     * @param taskId 实验任务主键
     * @return 结果
     */
    public int deleteExpTaskByTaskId(Long taskId);
}


