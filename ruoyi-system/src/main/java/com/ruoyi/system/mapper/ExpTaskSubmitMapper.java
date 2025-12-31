package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ExpTaskSubmit;

/**
 * 学生提交记录Mapper接口
 *
 * @author ruoyi
 * @date 2024-12-30
 */
public interface ExpTaskSubmitMapper
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
     * 从sys_user表查询该部门下的所有学生，左连接exp_task_submit表
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
    public ExpTaskSubmit selectExpTaskSubmitByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

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
     * 删除学生提交记录
     *
     * @param submitId 学生提交记录主键
     * @return 结果
     */
    public int deleteExpTaskSubmitBySubmitId(Long submitId);

    /**
     * 批量删除学生提交记录
     *
     * @param submitIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteExpTaskSubmitBySubmitIds(Long[] submitIds);
}
