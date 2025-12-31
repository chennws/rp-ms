package com.ruoyi.common.core.domain.pojo;

import lombok.Data;

/**
 * OnlyOffice 文档转换响应
 * 
 * @author ruoyi
 */
@Data
public class DownloadResponse {
    private String fileUrl;
    private String fileType;
    private String percent;
    private String endConvert;
    private Integer error;
}

