package com.ruoyi.system.service.impl;

import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.common.User;
import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.config.Document;
import com.onlyoffice.model.documenteditor.config.EditorConfig;
import com.onlyoffice.model.documenteditor.config.document.DocumentType;
import com.onlyoffice.model.documenteditor.config.document.Info;
import com.onlyoffice.model.documenteditor.config.document.Permissions;
import com.onlyoffice.model.documenteditor.config.document.Type;
import com.onlyoffice.model.documenteditor.config.document.permissions.CommentGroups;
import com.onlyoffice.model.documenteditor.config.editorconfig.Customization;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;
import com.onlyoffice.model.documenteditor.config.editorconfig.customization.Goback;
import com.ruoyi.system.service.ConfigService;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Author: Li
 * Date: 6/15/2025 23:12
 * Description: 获取配置信息，该配置决定了页面的按钮及菜单的显示
 * 文档地址：https://api.onlyoffice.com/zh-CN/docs/docs-api/usage-api/config/editor
 */
@Component
public class ConfigServiceImpl implements ConfigService {
    private static final Logger log = LoggerFactory.getLogger(ConfigServiceImpl.class);
//    @Resource
//    private JwtManager jwtManager;
    @Value("${docservice.security.key}")
    private String secretKey;
    @Value("${docservice.security.enable}")
    private Boolean securityEnable;
    @Value("${docservice.callback}")
    private String callback;
    @Value("${minio.bucketName}")
    private  String bucketName;
    @Resource
    private MinioClient minioClient;
    @Value("${minio.endpoint}")
    private String endpoint;

    public Config createConfig(final String fileUrl, final Mode mode, final Type pageType) throws UnsupportedEncodingException {
        return createConfig(fileUrl, mode, pageType, null);
    }

    @Override
    public Config createConfig(final String fileUrl, final Mode mode, final Type pageType, final String documentKey) throws UnsupportedEncodingException {
        // @TODO fileUrl可以是fileId, 在这里可以根据fileId查询数据库中具体的文件信息进行填充
        DocumentType documentType = this.getDocumentType(fileUrl);
        // 文档配置
        Document document = this.getDocument(fileUrl, pageType, documentKey);
        // 编辑器配置
        EditorConfig editorConfig = this.getEditorConfig(fileUrl, mode, pageType);
        Config config = Config.builder()
                .width("100%")
                .height("100%")
                .type(pageType)
                .documentType(documentType)
                .document(document)
                .editorConfig(editorConfig)
                .build();
//        // 是否开启jwt签名秘钥
//        if (Boolean.TRUE.equals(this.securityEnable)) {
//            String token = this.jwtManager.createToken(config, secretKey);
//            config.setToken(token);
//        }
        return config;
    }

    /**
     * 编辑器配置，设置文档转换服务回调地址
     */
    public EditorConfig getEditorConfig(String fileUrl, Mode mode, Type type) throws UnsupportedEncodingException {
        Permissions permissions = this.getPermissions();
        EditorConfig editorConfig = EditorConfig.builder()
                // 设置文档创建的接口地址，这里禁止在编辑器中创建文档，设置为null，文档编辑器将隐藏创建文档的按钮
                .createUrl(null)
                .lang("zh") // zh | en
                .mode(mode)
                .user(this.getUser())
                .recent(null)
                .templates(null)
                .customization(this.getCustomization())
                .plugins(null)
                .build();
        if (permissions != null && (Boolean.TRUE.equals(permissions.getEdit()) || Boolean.TRUE.equals(permissions.getFillForms()) || Boolean.TRUE.equals(permissions.getComment()) || Boolean.TRUE.equals(permissions.getReview())) && mode.equals(Mode.EDIT)) {
            //！！！编辑时的回调地址，文档转换服务将会调用该接口，完成文档保存更新
            String callbackUrl = String.format("%s?fileUrl=%s", this.callback, URLEncoder.encode(fileUrl, "UTF-8"));
            editorConfig.setCallbackUrl(callbackUrl);
        }
        return editorConfig;
    }

    /**
     * 获取文档相关的配置
     */
    public Document getDocument(String fileUrl, Type type)  {
        return getDocument(fileUrl, type, null);
    }

    /**
     * 获取文档相关的配置（支持自定义documentKey）
     */
    public Document getDocument(String fileUrl, Type type, String customDocumentKey)  {
        // 文档标题
        String documentName = this.getDocumentName(fileUrl);
        /*
         * 定义服务用来识别文档的唯一文档标识符。如果发送了已知key，则将从缓存中获取文档。
         * 每次编辑和保存文档时，都必须重新生成key。文档 url 可以用作 key，但不能使用特殊字符，长度限制为 128 个符号。
         * :::请注意， 对于连接到同一文档服务器的所有独立服务，密钥必须是唯一的。
         * 否则，服务可能会从编辑器缓存中打开其他人的文件。如果多个第三方集成商连接到同一文档服务器，他们也必须提供唯一的密钥。
         * 可以使用的关键字符： 0-9, a-z, A-Z, -._=。 最大密钥长度为 128 个字符。 :::
         *
         * ！！！！如果传入了customDocumentKey则直接使用，否则从minio获取文件信息生成key
         * key的生成策略非常重要！！！，如果每次都生成新的key会造成文件的缓存失效，文档转换服务将重新下载文件，可能导致文件不一致
         */
        String key;
        if (StringUtils.isNotEmpty(customDocumentKey)) {
            // 使用传入的自定义key
            key = customDocumentKey;
            log.info("使用自定义documentKey: {}", key);
        } else {
            // 使用minio文件信息生成key（原有逻辑）
            try {
                String objectName = extractObjectName(fileUrl);
                if (StringUtils.isEmpty(objectName)) {
                    throw new RuntimeException("无法从URL中提取objectName: " + fileUrl);
                }
                StatObjectResponse stat = this.minioClient.statObject(StatObjectArgs.builder()
                        .bucket(this.bucketName)
                        .object(objectName)
                        .build());
                ZonedDateTime zonedDateTime = stat.lastModified();
                // 将 ZonedDateTime 转换为 Instant
                Instant instant = zonedDateTime.toInstant();
                // 获取时间戳（毫秒）
                long timestamp = instant.toEpochMilli();
                key = DigestUtils.sha256Hex(objectName + timestamp);
                log.debug("自动生成documentKey: {}", key);
            }catch (Exception e) {
                key = UUID.randomUUID().toString();
                log.error("获取文件信息失败: {}", e.getMessage(), e);
            }
        }
        String suffix = this.getExtension(fileUrl);
        return Document.builder()
                // docx | pptx | xlsx | pdf ， 获取文件后缀
                .fileType(suffix)
                .key(key)
                .title(documentName)
                // 文档下载地址，这个地址提供给文档转换服务，用于下载文档。
                .url(fileUrl)
                .info(this.getInfo())
                .permissions(this.getPermissions())
                .build();
    }

    public DocumentType getDocumentType(String fileUrl) {
        // 首先尝试从 URL 中提取扩展名
        String extension = extractExtensionFromUrl(fileUrl);
        
        // 如果 URL 中没有扩展名，尝试从 MinIO 获取文件信息
        if (StringUtils.isEmpty(extension)) {
            extension = getExtensionFromMinio(fileUrl);
        }
        
        if (StringUtils.isEmpty(extension)) {
            throw new RuntimeException("未知的文件类型，无法从URL或MinIO获取文件扩展名: " + fileUrl);
        }
        
        // 转换为小写进行比较
        extension = extension.toLowerCase();
        
        if ("docx".equals(extension) || "doc".equals(extension) || "pdf".equals(extension)) {
            // ！！！这里不是写错了，而是onlyoffice默认读取pdf为word，所以这里返回WORD
            return DocumentType.WORD;
        } else if ("pptx".equals(extension) || "ppt".equals(extension)) {
            return DocumentType.SLIDE;
        } else if ("xlsx".equals(extension) || "xls".equals(extension)) {
            return DocumentType.CELL;
        } else {
            throw new RuntimeException("不支持的文件类型: " + extension + "，仅支持 docx, pptx, xlsx, pdf");
        }
    }
    
    /**
     * 从 URL 中提取文件扩展名
     */
    private String extractExtensionFromUrl(String fileUrl) {
        if (StringUtils.isEmpty(fileUrl)) {
            return null;
        }
        // 移除查询参数
        String urlWithoutQuery = fileUrl.split("\\?")[0];
        int lastDotIndex = urlWithoutQuery.lastIndexOf(".");
        if (lastDotIndex > 0 && lastDotIndex < urlWithoutQuery.length() - 1) {
            return urlWithoutQuery.substring(lastDotIndex + 1);
        }
        return null;
    }
    
    /**
     * 从 MinIO 获取文件扩展名
     */
    private String getExtensionFromMinio(String fileUrl) {
        try {
            // 提取 objectName
            String objectName = extractObjectName(fileUrl);
            if (StringUtils.isEmpty(objectName)) {
                return null;
            }
            
            // 从 objectName 中提取扩展名
            int lastDotIndex = objectName.lastIndexOf(".");
            if (lastDotIndex > 0 && lastDotIndex < objectName.length() - 1) {
                return objectName.substring(lastDotIndex + 1);
            }
        } catch (Exception e) {
            log.warn("从 MinIO 获取文件扩展名失败: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 从 fileUrl 中提取 MinIO objectName
     */
    private String extractObjectName(String fileUrl) {
        if (StringUtils.isEmpty(fileUrl)) {
            return null;
        }
        
        // 移除查询参数
        String urlWithoutQuery = fileUrl.split("\\?")[0];
        
        // 尝试从完整 URL 中提取 objectName
        // URL 格式: http://endpoint/bucketName/objectName
        if (urlWithoutQuery.contains(endpoint + "/" + bucketName + "/")) {
            return urlWithoutQuery.replace(endpoint + "/" + bucketName + "/", "");
        } else if (urlWithoutQuery.contains("/" + bucketName + "/")) {
            int index = urlWithoutQuery.indexOf("/" + bucketName + "/");
            return urlWithoutQuery.substring(index + bucketName.length() + 2);
        } else if (urlWithoutQuery.contains(bucketName + "/")) {
            int index = urlWithoutQuery.indexOf(bucketName + "/");
            return urlWithoutQuery.substring(index + bucketName.length() + 1);
        } else {
            // 如果找不到 bucketName，尝试提取最后一个斜杠后的内容
            int lastSlash = urlWithoutQuery.lastIndexOf("/");
            if (lastSlash > 0 && lastSlash < urlWithoutQuery.length() - 1) {
                return urlWithoutQuery.substring(lastSlash + 1);
            }
        }
        
        return null;
    }

    private String getDocumentName(String fileUrl) {
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        if (StringUtils.isEmpty(fileName)) {
            throw new RuntimeException("文件名不能为空");
        }
        return fileName.split("\\?")[0];
    }

    private String getExtension(String fileUrl) {
        // 首先尝试从 URL 中提取扩展名
        String extension = extractExtensionFromUrl(fileUrl);
        
        // 如果 URL 中没有扩展名，尝试从 MinIO 获取文件信息
        if (StringUtils.isEmpty(extension)) {
            extension = getExtensionFromMinio(fileUrl);
        }
        
        if (StringUtils.isEmpty(extension)) {
            throw new RuntimeException("无法读取文件扩展名: " + fileUrl);
        }
        
        extension = extension.toLowerCase();
        
        // 验证扩展名是否合法
        if (!Pattern.matches("docx|doc|pptx|ppt|xlsx|xls|pdf", extension)) {
            throw new RuntimeException("文件扩展名不合法: " + extension + "，仅支持 docx, pptx, xlsx, pdf");
        }
        
        // 统一返回标准格式
        if ("doc".equals(extension)) {
            return "docx";
        } else if ("ppt".equals(extension)) {
            return "pptx";
        } else if ("xls".equals(extension)) {
            return "xlsx";
        }
        
        return extension;
    }

    /**
     * 包含文档的附加参数（文档所有者、存储文档的文件夹、上传日期、共享设置）；
     */
    public Info getInfo() {
        // @TODO 查询数据库获取当前文档的信息填充
        return Info.builder()
                .owner("文档所有人")
                .favorite(false)
                // 文档上传时间
                .uploaded("20250607")
                .build();
    }

    /**
     * 获取当前用户对文件的权限
     */
    public Permissions getPermissions() {
        // @TODO 获取当前登录的用户，查询他对该文件的权限
        return Permissions.builder()
                .chat(false)
                .comment(true)
                .commentGroups(new CommentGroups())
                .copy(true)
                .download(true)
                .edit(true)
                .fillForms(true)
                .modifyContentControl(true)
                .modifyFilter(true)
                .print(true)
                .protect(false)
                .review(true)
                .reviewGroups(new ArrayList<>(0))
                .userInfoGroups(null)
                .build();
    }

    /**
     * 获取当前登录人的用户信息，作为文档编辑人显示
     */
    public User getUser() {
        // @TODO 获取当前登录人信息
        User user = User.builder()
                .id("1")
                .name("winter")
                .image("")
                .build();
        return user;
    }

    /**
     * 定义编辑器需要显示的按钮、菜单
     */
    public Customization getCustomization() {
        Goback goback = Goback.builder()
                .url("")
                .build();
        // 允许自定义编辑器界面，使其看起来像您的其他产品（如果有），并更改附加按钮、链接、更改徽标和编辑器所有者详细信息的显示或不显示
        Customization customization = Customization.builder()
                .autosave(true) // if the Autosave menu option is enabled or disabled
                .comments(true) // if the Comments menu button is displayed or hidden
                .compactHeader(false) /* if the additional action buttons are displayed
    in the upper part of the editor window header next to the logo (false) or in the toolbar (true) */
                .compactToolbar(false) // if the top toolbar type displayed is full (false) or compact (true)
                .forcesave(false)/* add the request for the forced file saving to the callback handler
    when saving the document within the document editing service */
                .help(false)  //  if the Help menu button is displayed or hidden
                .hideRightMenu(false) // if the right menu is displayed or hidden on first loading
                .hideRulers(false) // if the editor rulers are displayed or hidden
                .feedback(false)
                .goback(goback)
                .plugins(false)
                .build();

        return customization;
    }
}
