package com.ruoyi.system.domain.enums;

/**
 * 实验报告状态枚举
 */
public enum ReportState {
    /**
     * 草稿（未提交）
     */
    DRAFT("0", "草稿"),

    /**
     * 已提交（待批阅）
     */
    SUBMITTED("1", "已提交"),

    /**
     * 批阅中
     */
    REVIEWING("2", "批阅中"),

    /**
     * 已批阅（通过）
     */
    REVIEWED("3", "已批阅"),

    /**
     * 打回（需修改）
     */
    REJECTED("4", "已打回"),

    /**
     * 重新提交
     */
    RESUBMITTED("5", "重新提交"),

    /**
     * 已归档
     */
    ARCHIVED("6", "已归档");

    private final String code;
    private final String description;

    ReportState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取状态
     */
    public static ReportState fromCode(String code) {
        for (ReportState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的状态代码: " + code);
    }
}
