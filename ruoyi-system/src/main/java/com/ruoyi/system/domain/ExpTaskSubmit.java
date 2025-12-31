package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 学生提交记录对象 exp_task_submit
 *
 * @author ruoyi
 * @date 2024-12-30
 */
public class ExpTaskSubmit extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 提交记录ID */
    private Long submitId;

    /** 任务ID */
    @Excel(name = "任务ID")
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /** 学生ID */
    @Excel(name = "学生ID")
    @NotNull(message = "学生ID不能为空")
    private Long userId;

    /** 学生姓名 */
    @Excel(name = "学生姓名")
    private String userName;

    /** 提交的文件URL */
    @Excel(name = "提交的文件URL")
    private String fileUrl;

    /** OnlyOffice文档唯一标识key */
    private String documentKey;

    /** 是否正在提交中(0否 1是) */
    private Integer submitPending;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "提交时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 状态（0待批阅 1已批阅） */
    @Excel(name = "状态", readConverterExp = "0=待批阅,1=已批阅")
    private String status;

    /** 分数 */
    @Excel(name = "分数")
    private BigDecimal score;

    /** 教师评语 */
    @Excel(name = "教师评语")
    private String teacherRemark;

    /** 打回原因 */
    @Excel(name = "打回原因")
    private String rejectReason;

    /** 文档版本号（用于解决OnlyOffice版本冲突） */
    private Integer documentVersion;

    /** 部门ID（查询条件，不存储到表） */
    private Long deptId;

    /** 学生真实姓名（从sys_user表获取nick_name） */
    @Excel(name = "姓名")
    private String nickName;

    public void setSubmitId(Long submitId)
    {
        this.submitId = submitId;
    }

    public Long getSubmitId()
    {
        return submitId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setFileUrl(String fileUrl)
    {
        this.fileUrl = fileUrl;
    }

    public String getFileUrl()
    {
        return fileUrl;
    }

    public void setDocumentKey(String documentKey)
    {
        this.documentKey = documentKey;
    }

    public String getDocumentKey()
    {
        return documentKey;
    }

    public void setSubmitPending(Integer submitPending)
    {
        this.submitPending = submitPending;
    }

    public Integer getSubmitPending()
    {
        return submitPending;
    }

    public void setSubmitTime(Date submitTime)
    {
        this.submitTime = submitTime;
    }

    public Date getSubmitTime()
    {
        return submitTime;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setScore(BigDecimal score)
    {
        this.score = score;
    }

    public BigDecimal getScore()
    {
        return score;
    }

    public void setTeacherRemark(String teacherRemark)
    {
        this.teacherRemark = teacherRemark;
    }

    public String getTeacherRemark()
    {
        return teacherRemark;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setDocumentVersion(Integer documentVersion)
    {
        this.documentVersion = documentVersion;
    }

    public Integer getDocumentVersion()
    {
        return documentVersion;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getNickName()
    {
        return nickName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("submitId", getSubmitId())
            .append("taskId", getTaskId())
            .append("userId", getUserId())
            .append("userName", getUserName())
            .append("fileUrl", getFileUrl())
            .append("documentKey", getDocumentKey())
            .append("submitPending", getSubmitPending())
            .append("submitTime", getSubmitTime())
            .append("status", getStatus())
            .append("score", getScore())
            .append("teacherRemark", getTeacherRemark())
            .append("rejectReason", getRejectReason())
            .append("documentVersion", getDocumentVersion())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
