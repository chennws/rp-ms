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
     * æŸ¥è¯¢å­¦ç”Ÿæäº¤è®°å½•æ€»æ•°ï¼ˆåŒ…æ‹¬æ‰€æœ‰å­¦ç”Ÿï¼Œæœªæäº¤çš„ä¹Ÿæ˜¾ç¤ºï¼‰
     *
     * @param expTaskSubmit å­¦ç”Ÿæäº¤è®°å½•ï¼ˆå¿…é¡»åŒ…å«taskIdå’ŒdeptIdï¼?
     * @return å­¦ç”Ÿæäº¤è®°å½•æ€»æ•°
     */
    public long selectExpTaskSubmitListWithAllStudentsCount(ExpTaskSubmit expTaskSubmit);

    /**
     * Query submit statistics for all students in a task.
     *
     * @param expTaskSubmit query params (taskId, deptId, optional userName)
     * @return stats map
     */
    public java.util.Map<String, Object> selectExpTaskSubmitStatsWithAllStudents(ExpTaskSubmit expTaskSubmit);

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
