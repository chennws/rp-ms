package com.ruoyi.system.domain.enums;

/**
 * 实验报告触发器枚举（事件）
 */
public enum ReportTrigger {
    /**
     * 提交报告
     */
    SUBMIT("提交"),

    /**
     * 开始批阅
     */
    START_REVIEW("开始批阅"),

    /**
     * 批阅通过
     */
    APPROVE("批阅通过"),

    /**
     * 打回报告
     */
    REJECT("打回"),

    /**
     * 重新提交
     */
    RESUBMIT("重新提交"),

    /**
     * 归档
     */
    ARCHIVE("归档");

    private final String description;

    ReportTrigger(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
