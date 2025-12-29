package com.ruoyi.system.service;

import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.config.document.Type;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;

import java.io.UnsupportedEncodingException;

public interface ConfigService {
    Config createConfig(String fileId, Mode mode, Type type) throws UnsupportedEncodingException;
}
