package com.ruoyi.system.domain;

import java.util.Date;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 实验任务对象 exp_task
 * 
 * @author ruoyi
 * @date 2024-03-01
 */
public class ExpTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 任务名称 */
    @Excel(name = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    @Size(min = 0, max = 100, message = "任务名称长度不能超过100个字符")
    private String taskName;

    /** 课程名称 */
    @Excel(name = "课程名称")
    @NotBlank(message = "课程名称不能为空")
    @Size(min = 0, max = 100, message = "课程名称长度不能超过100个字符")
    private String courseName;

    /** 部门ID */
    @Excel(name = "部门ID")
    private Long deptId;

    /** 部门名称 */
    @Excel(name = "部门名称")
    private String deptName;

    /** 截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "截止时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @NotNull(message = "截止时间不能为空")
    private Date deadline;

    /** 状态（0未开始 1进行中 2已结束） */
    @Excel(name = "状态", readConverterExp = "0=未开始,1=进行中,2=已结束")
    private String status;

    /** 实验报告文件URL */
    @Excel(name = "实验报告文件URL")
    private String reportFileUrl;

    /** 提交人数 */
    private Integer submitCount;

    /** 总人数 */
    private Integer totalCount;

    public void setTaskId(Long taskId) 
    {
        this.taskId = taskId;
    }

    public Long getTaskId() 
    {
        return taskId;
    }
    public void setTaskName(String taskName) 
    {
        this.taskName = taskName;
    }

    public String getTaskName() 
    {
        return taskName;
    }
    public void setCourseName(String courseName) 
    {
        this.courseName = courseName;
    }

    public String getCourseName() 
    {
        return courseName;
    }
    public void setDeptId(Long deptId) 
    {
        this.deptId = deptId;
    }

    public Long getDeptId() 
    {
        return deptId;
    }
    public void setDeptName(String deptName) 
    {
        this.deptName = deptName;
    }

    public String getDeptName() 
    {
        return deptName;
    }
    public void setDeadline(Date deadline) 
    {
        this.deadline = deadline;
    }

    public Date getDeadline() 
    {
        return deadline;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setReportFileUrl(String reportFileUrl) 
    {
        this.reportFileUrl = reportFileUrl;
    }

    public String getReportFileUrl() 
    {
        return reportFileUrl;
    }

    public Integer getSubmitCount() 
    {
        return submitCount;
    }

    public void setSubmitCount(Integer submitCount) 
    {
        this.submitCount = submitCount;
    }

    public Integer getTotalCount() 
    {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) 
    {
        this.totalCount = totalCount;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("taskId", getTaskId())
            .append("taskName", getTaskName())
            .append("courseName", getCourseName())
            .append("deptId", getDeptId())
            .append("deadline", getDeadline())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("reportFileUrl", getReportFileUrl())
            .toString();
    }
}

