package com.ruoyi.web.controller.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Author: Li
 * Date: 8/8/2025 19:31
 * Description: SaveRequestParams
 */
@Data
@Accessors(chain = true)
public class SaveRequestParams {
    private final String c = "forcesave";
    private String key;
    private String userdata = "userdata";
}
