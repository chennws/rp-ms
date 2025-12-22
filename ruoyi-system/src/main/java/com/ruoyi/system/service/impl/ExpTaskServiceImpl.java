package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.system.domain.ExpTask;
import com.ruoyi.system.mapper.ExpTaskMapper;
import com.ruoyi.system.service.IExpTaskService;

/**
 * 实验任务Service业务层处理
 * 
 * @author ruoyi
 * @date 2024-03-01
 */
@Service
public class ExpTaskServiceImpl implements IExpTaskService 
{
    @Autowired
    private ExpTaskMapper expTaskMapper;

    /**
     * 查询实验任务
     * 
     * @param taskId 实验任务主键
     * @return 实验任务
     */
    @Override
    public ExpTask selectExpTaskByTaskId(Long taskId)
    {
        return expTaskMapper.selectExpTaskByTaskId(taskId);
    }

    /**
     * 查询实验任务列表
     * 
     * @param expTask 实验任务
     * @return 实验任务
     */
    @Override
    public List<ExpTask> selectExpTaskList(ExpTask expTask)
    {
        List<ExpTask> list = expTaskMapper.selectExpTaskList(expTask);
        // 更新任务状态
        Date now = new Date();
        for (ExpTask task : list)
        {
            updateTaskStatus(task, now);
        }
        return list;
    }
    
    /**
     * 更新任务状态
     * 
     * @param task 任务对象
     * @param now 当前时间
     */
    private void updateTaskStatus(ExpTask task, Date now)
    {
        if (task.getDeadline() != null && task.getCreateTime() != null)
        {
            if (now.before(task.getCreateTime()))
            {
                task.setStatus("0"); // 未开始
            }
            else if (now.after(task.getDeadline()))
            {
                task.setStatus("2"); // 已结束
            }
            else
            {
                task.setStatus("1"); // 进行中
            }
        }
    }

    /**
     * 新增实验任务
     * 
     * @param expTask 实验任务
     * @return 结果
     */
    @Override
    public int insertExpTask(ExpTask expTask)
    {
        // 发布时间默认为当前时间
        expTask.setCreateTime(DateUtils.getNowDate());
        // 默认状态为未开始
        if (expTask.getStatus() == null || expTask.getStatus().isEmpty())
        {
            expTask.setStatus("0");
        }
        return expTaskMapper.insertExpTask(expTask);
    }

    /**
     * 修改实验任务
     * 
     * @param expTask 实验任务
     * @return 结果
     */
    @Override
    public int updateExpTask(ExpTask expTask)
    {
        // 更新任务状态
        Date now = new Date();
        updateTaskStatus(expTask, now);
        expTask.setUpdateTime(DateUtils.getNowDate());
        return expTaskMapper.updateExpTask(expTask);
    }

    /**
     * 批量删除实验任务
     * 
     * @param taskIds 需要删除的实验任务主键
     * @return 结果
     */
    @Override
    public int deleteExpTaskByTaskIds(Long[] taskIds)
    {
        return expTaskMapper.deleteExpTaskByTaskIds(taskIds);
    }

    /**
     * 删除实验任务信息
     * 
     * @param taskId 实验任务主键
     * @return 结果
     */
    @Override
    public int deleteExpTaskByTaskId(Long taskId)
    {
        return expTaskMapper.deleteExpTaskByTaskId(taskId);
    }
}

