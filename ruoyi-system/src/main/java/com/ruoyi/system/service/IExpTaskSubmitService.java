package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.ExpTaskSubmit;

/**
 * 学生提交记录Service接口
 *
 * @author ruoyi
 * @date 2024-12-30
 */
public interface IExpTaskSubmitService
{
    /**
     * 查询学生提交记录
     *
     * @param submitId 学生提交记录主键
     * @return 学生提交记录
     */
    public ExpTaskSubmit selectExpTaskSubmitBySubmitId(Long submitId);

    /**
     * 查询学生提交记录列表
     *
     * @param expTaskSubmit 学生提交记录
     * @return 学生提交记录集合
     */
    public List<ExpTaskSubmit> selectExpTaskSubmitList(ExpTaskSubmit expTaskSubmit);

    /**
     * 查询学生提交记录列表（包括所有学生，未提交的也显示）
     * 用于教师批改列表
     *
     * @param expTaskSubmit 学生提交记录（必须包含taskId和deptId）
     * @return 学生提交记录集合
     */
    public List<ExpTaskSubmit> selectExpTaskSubmitListWithAllStudents(ExpTaskSubmit expTaskSubmit);

    /**
     * 根据任务ID和用户ID查询提交记录
     *
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 学生提交记录
     */
    public ExpTaskSubmit selectExpTaskSubmitByTaskIdAndUserId(Long taskId, Long userId);

    /**
     * 新增学生提交记录
     *
     * @param expTaskSubmit 学生提交记录
     * @return 结果
     */
    public int insertExpTaskSubmit(ExpTaskSubmit expTaskSubmit);

    /**
     * 修改学生提交记录
     *
     * @param expTaskSubmit 学生提交记录
     * @return 结果
     */
    public int updateExpTaskSubmit(ExpTaskSubmit expTaskSubmit);

    /**
     * 批量删除学生提交记录
     *
     * @param submitIds 需要删除的学生提交记录主键集合
     * @return 结果
     */
    public int deleteExpTaskSubmitBySubmitIds(Long[] submitIds);

    /**
     * 删除学生提交记录信息
     *
     * @param submitId 学生提交记录主键
     * @return 结果
     */
    public int deleteExpTaskSubmitBySubmitId(Long submitId);

    /**
     * 学生提交任务
     *
     * @param taskId 任务ID
     * @param fileUrl 文件URL
     * @param userId 用户ID
     * @param userName 用户名
     * @return 结果
     */
    public int submitTask(Long taskId, String fileUrl, Long userId, String userName);
}
