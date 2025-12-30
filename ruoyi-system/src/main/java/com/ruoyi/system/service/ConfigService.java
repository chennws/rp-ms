package com.ruoyi.system.service;

import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.config.document.Type;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;

import java.io.UnsupportedEncodingException;

public interface ConfigService {
    Config createConfig(String fileId, Mode mode, Type type) throws UnsupportedEncodingException;

    /**
     * 创建配置，支持传入自定义的documentKey
     * @param fileId 文件URL
     * @param mode 编辑模式
     * @param type 页面类型
     * @param documentKey 自定义的文档key（如果为空则自动生成）
     * @return Config配置对象
     * @throws UnsupportedEncodingException
     */
    Config createConfig(String fileId, Mode mode, Type type, String documentKey) throws UnsupportedEncodingException;
}
