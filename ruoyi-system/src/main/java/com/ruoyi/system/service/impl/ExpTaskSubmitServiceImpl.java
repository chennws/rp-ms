package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.system.domain.ExpTaskSubmit;
import com.ruoyi.system.mapper.ExpTaskSubmitMapper;
import com.ruoyi.system.service.IExpTaskSubmitService;

/**
 * 学生提交记录Service业务层处理
 *
 * @author ruoyi
 * @date 2024-12-30
 */
@Service
public class ExpTaskSubmitServiceImpl implements IExpTaskSubmitService
{
    @Autowired
    private ExpTaskSubmitMapper expTaskSubmitMapper;

    /**
     * 查询学生提交记录
     *
     * @param submitId 学生提交记录主键
     * @return 学生提交记录
     */
    @Override
    public ExpTaskSubmit selectExpTaskSubmitBySubmitId(Long submitId)
    {
        return expTaskSubmitMapper.selectExpTaskSubmitBySubmitId(submitId);
    }

    /**
     * 查询学生提交记录列表
     *
     * @param expTaskSubmit 学生提交记录
     * @return 学生提交记录
     */
    @Override
    public List<ExpTaskSubmit> selectExpTaskSubmitList(ExpTaskSubmit expTaskSubmit)
    {
        return expTaskSubmitMapper.selectExpTaskSubmitList(expTaskSubmit);
    }

    /**
     * 查询学生提交记录列表（包括所有学生，未提交的也显示）
     * 用于教师批改列表
     *
     * @param expTaskSubmit 学生提交记录（必须包含taskId和deptId）
     * @return 学生提交记录
     */
    @Override
    public List<ExpTaskSubmit> selectExpTaskSubmitListWithAllStudents(ExpTaskSubmit expTaskSubmit)
    {
        return expTaskSubmitMapper.selectExpTaskSubmitListWithAllStudents(expTaskSubmit);
    }

    /**
     * 鏌ヨ瀛︾敓鎻愪氦璁板綍鎬绘暟锛堝寘鎷墍鏈夊鐢燂紝鏈彁浜ょ殑涔熸樉绀猴級
     *
     * @param expTaskSubmit 瀛︾敓鎻愪氦璁板綍锛堝繀椤诲寘鍚玹askId鍜宒eptId锛?
     * @return 瀛︾敓鎻愪氦璁板綍鎬绘暟
     */
    @Override
    public long selectExpTaskSubmitListWithAllStudentsCount(ExpTaskSubmit expTaskSubmit)
    {
        return expTaskSubmitMapper.selectExpTaskSubmitListWithAllStudentsCount(expTaskSubmit);
    }

    /**
     * Query submit statistics for all students in a task.
     *
     * @param expTaskSubmit query params (taskId, deptId, optional userName)
     * @return stats map
     */
    @Override
    public java.util.Map<String, Object> selectExpTaskSubmitStatsWithAllStudents(ExpTaskSubmit expTaskSubmit)
    {
        return expTaskSubmitMapper.selectExpTaskSubmitStatsWithAllStudents(expTaskSubmit);
    }

    /**
     * 根据任务ID和用户ID查询提交记录
     *
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 学生提交记录
     */
    @Override
    public ExpTaskSubmit selectExpTaskSubmitByTaskIdAndUserId(Long taskId, Long userId)
    {
        return expTaskSubmitMapper.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
    }

    /**
     * 新增学生提交记录
     *
     * @param expTaskSubmit 学生提交记录
     * @return 结果
     */
    @Override
    public int insertExpTaskSubmit(ExpTaskSubmit expTaskSubmit)
    {
        return expTaskSubmitMapper.insertExpTaskSubmit(expTaskSubmit);
    }

    /**
     * 修改学生提交记录
     *
     * @param expTaskSubmit 学生提交记录
     * @return 结果
     */
    @Override
    public int updateExpTaskSubmit(ExpTaskSubmit expTaskSubmit)
    {
        return expTaskSubmitMapper.updateExpTaskSubmit(expTaskSubmit);
    }

    /**
     * 批量删除学生提交记录
     *
     * @param submitIds 需要删除的学生提交记录主键
     * @return 结果
     */
    @Override
    public int deleteExpTaskSubmitBySubmitIds(Long[] submitIds)
    {
        return expTaskSubmitMapper.deleteExpTaskSubmitBySubmitIds(submitIds);
    }

    /**
     * 删除学生提交记录信息
     *
     * @param submitId 学生提交记录主键
     * @return 结果
     */
    @Override
    public int deleteExpTaskSubmitBySubmitId(Long submitId)
    {
        return expTaskSubmitMapper.deleteExpTaskSubmitBySubmitId(submitId);
    }

    /**
     * 学生提交任务
     *
     * @param taskId 任务ID
     * @param fileUrl 文件URL
     * @param userId 用户ID
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public int submitTask(Long taskId, String fileUrl, Long userId, String userName)
    {
        // 检查是否已经提交过
        ExpTaskSubmit existSubmit = expTaskSubmitMapper.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);

        ExpTaskSubmit submit = new ExpTaskSubmit();
        submit.setTaskId(taskId);
        submit.setUserId(userId);
        submit.setUserName(userName);
        submit.setFileUrl(fileUrl);
        submit.setSubmitTime(new Date());
        submit.setStatus("0"); // 0-待批阅

        if (existSubmit != null)
        {
            // 已经提交过，更新提交记录
            submit.setSubmitId(existSubmit.getSubmitId());
            return expTaskSubmitMapper.updateExpTaskSubmit(submit);
        }
        else
        {
            // 首次提交，插入新记录
            return expTaskSubmitMapper.insertExpTaskSubmit(submit);
        }
    }
}
