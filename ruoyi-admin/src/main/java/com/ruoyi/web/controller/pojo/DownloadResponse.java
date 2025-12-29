package com.ruoyi.web.controller.pojo;

import lombok.Data;

/**
 * Author: Li
 * Date: 10/25/2025 14:06
 * Description: DownloadResponse
 */
@Data
public class DownloadResponse {
    private String fileUrl;
    private String fileType;
    private String percent;
    private String endConvert;
    private Integer error;
}
