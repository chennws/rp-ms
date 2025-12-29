package com.ruoyi.web.controller.enums;

import lombok.Getter;

/**
 * Author: Li
 * Date: 10/25/2025 15:01
 * Description: DownloadType
 */
@Getter
public enum DownloadType {
    docx("docx","docx"),
    xlsx("xlsx","xlsx"),
    pdf("pdf","pdf"),
    pptx("pptx","pptx");

    private final String type;
    private final String message;

    DownloadType(String type, String message) {
        this.message = message;
        this.type = type;
    }
}
