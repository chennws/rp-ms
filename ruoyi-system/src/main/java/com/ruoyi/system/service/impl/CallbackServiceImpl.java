package com.ruoyi.system.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Action;
import com.onlyoffice.model.documenteditor.callback.action.Type;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Author: Li
 * Date: 6/15/2025 23:12
 * Description: 处理文档转换服务回调业务
 */
@Slf4j
@Component
public class CallbackServiceImpl implements CallbackService {

    @Value("${docservice.security.enable}")
    private Boolean securityEnable;
    @Value("${docservice.security.key}")
    private String secretKey;
    @Resource
    private JwtManager jwtManager;
    @Value("${minio.bucketName}")
    private  String bucketName;
    @Value("${minio.endpoint}")
    private String endpoint;
    @Resource
    private MinioClient minioClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 校验回调数据
     * @param callback
     * @param authorization
     * @return
     * @throws JsonProcessingException
     */
    @Override
    public Callback verifyCallback(Callback callback, String authorization) throws JsonProcessingException {
        if (!Boolean.TRUE.equals(this.securityEnable)) {
            return callback;
        }
        String token = callback.getToken();
        boolean fromHeader = false;
        if (StringUtils.isEmpty(token) && StringUtils.isNotEmpty(authorization)) {
            token = authorization.replace("Bearer ", "");
            fromHeader = true;
        }
        if (StringUtils.isEmpty(token)) {
            throw new SecurityException("Not found authorization token");
        }
        String payload = this.jwtManager.verify(token);
        if (fromHeader) {
            JSONObject data = new JSONObject(payload);
            JSONObject callbackFromToken = data.getJSONObject("payload");
            return this.objectMapper.readValue(callbackFromToken.toString(), Callback.class);
        }else {
            return this.objectMapper.readValue(payload, Callback.class);
        }
    }

    @Override
    public void processCallback(Callback callback, String fileUrl) throws Exception {
        switch (callback.getStatus()) {
            case EDITING:
                this.handlerEditing(callback, fileUrl);
                break;
            case SAVE:
                this.handlerSave(callback, fileUrl);
                break;
            case SAVE_CORRUPTED:
                this.handlerSaveCorrupted(callback, fileUrl);
                break;
            case CLOSED:
                this.handlerClosed(callback, fileUrl);
                break;
            case FORCESAVE:
                this.handlerForcesave(callback, fileUrl);
                break;
            case FORCESAVE_CORRUPTED:
                this.handlerForcesaveCurrupted(callback, fileUrl);
                break;
            default:
                throw new RuntimeException("Callback has no status");
        }

    }

    private String getDocumentName(String fileUrl) {
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        if (StringUtils.isEmpty(fileName)) {
            throw new RuntimeException("文件名不能为空");
        }
        return fileName.split("\\?")[0];
    }

    @Override
    public void handlerEditing(final Callback callback, final String fileUrl) {
        Action action = callback.getActions().get(0);  // get the user ID who is editing the document
        if (Type.CONNECTED.equals(action.getType())) {  // if this value is not equal to the user ID
            String userId = action.getUserid();  // get user ID
            if (!callback.getUsers().contains(userId)) {  // if this user is not specified in the body

            }
        }
    }

    @Override
    public void handlerSave(final Callback callback, final String fileUrl)  {
        String downloadUri = callback.getUrl();
        if(StringUtils.isEmpty(downloadUri)) {
            return;
        }
//        String changesUri = callback.getChangesurl();
//        String key = callback.getKey();
//        String downloadExt = callback.getFiletype();
        String objectName = fileUrl.replace(this.endpoint + "/" + this.bucketName + "/", "");
        // 从文件转换服务中下载文件
        RestTemplate restTemplate = new RestTemplate();
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/octet-stream");
        ResponseEntity<byte[]> response = restTemplate.exchange(
                downloadUri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        // 将响应体转换为 InputStream
        try (InputStream inputStream = new ByteArrayInputStream(response.getBody())) {
            // 将新的文件上传到minio
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(this.bucketName)
                    .object(objectName)
                    .stream(inputStream, inputStream.available(), -1)
                    .contentType("application/octet-stream")
                    .build();
            this.minioClient.putObject(args);
            log.info("文档保存成功objectName:{},bucket:{}", objectName, this.bucketName);
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(objectName + "文档保存失败");
        }
    }

    @Override
    public void handlerForcesave(final Callback callback, final String fileUrl)  {
        this.handlerSave(callback, fileUrl);
    }


    @Override
    public void handlerForcesaveCurrupted(Callback callback, String fileUrl) throws Exception {
        this.handlerForcesave(callback, fileUrl);
    }

    @Override
    public void handlerSaveCorrupted(Callback callback, String fileUrl) throws Exception {
        this.handlerSave(callback, fileUrl);
    }

    @Override
    public void handlerClosed(final Callback callback, final String fileUrl)  {
    }
}
