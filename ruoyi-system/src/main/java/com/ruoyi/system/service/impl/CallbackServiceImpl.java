package com.ruoyi.system.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.callback.Action;
import com.onlyoffice.model.documenteditor.callback.action.Type;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import com.ruoyi.system.domain.ExpTaskSubmit;
import com.ruoyi.system.service.IExpTaskSubmitService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
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
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @Resource
    private IExpTaskSubmitService expTaskSubmitService;
    @Resource
    private com.ruoyi.system.service.ReportStateMachineService reportStateMachineService;
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
        // ✅ 移除URL参数（如果有），只保留路径部分
        String fileUrlWithoutParams = fileUrl.split("\\?")[0];
        String objectName = fileUrlWithoutParams.replace(this.endpoint + "/" + this.bucketName + "/", "");
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
        // 先保存文件
        this.handlerSave(callback, fileUrl);

        // 保存成功后，更新版本号（无论是学生提交还是教师批改）
        try {
            // 从fileUrl中解析taskId和userId
            // 文件URL格式: http://endpoint/bucketName/year/month/day/submit_taskId_userId_timestamp.extension
            // ✅ 移除URL参数（如果有），只保留路径部分
            String fileUrlWithoutParams = fileUrl.split("\\?")[0];
            String objectName = fileUrlWithoutParams.replace(this.endpoint + "/" + this.bucketName + "/", "");

            // 使用正则表达式提取taskId和userId
            // 匹配模式：submit_数字_数字_数字.扩展名
            Pattern pattern = Pattern.compile("submit_(\\d+)_(\\d+)_\\d+\\.\\w+");
            Matcher matcher = pattern.matcher(objectName);

            if (matcher.find()) {
                Long taskId = Long.parseLong(matcher.group(1));
                Long userId = Long.parseLong(matcher.group(2));

                log.info("从文件名解析出 taskId: {}, userId: {}", taskId, userId);

                // 查询提交记录
                ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
                if (submit != null) {
                    // ✅ 重要：无论什么情况，只要文件保存了，就更新版本号（解决教师批改后版本冲突问题）
                    Integer currentVersion = submit.getDocumentVersion() != null ? submit.getDocumentVersion() : 1;
                    Integer newVersion = currentVersion + 1;
                    String newDocumentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + newVersion);

                    // 检查是否是学生提交（submitPending=1）
                    boolean isStudentSubmit = submit.getSubmitPending() != null && submit.getSubmitPending() == 1;

                    if (isStudentSubmit) {
                        // 学生提交：先更新版本号和提交时间
                        submit.setDocumentVersion(newVersion);
                        submit.setDocumentKey(newDocumentKey);
                        submit.setSubmitTime(new Date());
                        submit.setSubmitPending(0);

                        // ✅ 先更新数据库（版本号、提交时间、submitPending）
                        expTaskSubmitService.updateExpTaskSubmit(submit);

                        log.info("学生提交成功，已更新submit_time和版本号, taskId: {}, userId: {}, version: {} -> {}, newKey: {}",
                            taskId, userId, currentVersion, newVersion, newDocumentKey);

                        // ✅ 然后触发状态机转换（状态机会单独更新status字段）
                        String currentStatus = submit.getStatus();
                        try {
                            if ("0".equals(currentStatus)) {
                                // 草稿 -> 已提交
                                reportStateMachineService.submitReport(submit.getSubmitId());
                                log.info("状态机触发成功：草稿 -> 已提交, submitId: {}", submit.getSubmitId());
                            } else if ("4".equals(currentStatus)) {
                                // 已打回 -> 重新提交
                                reportStateMachineService.resubmit(submit.getSubmitId());
                                log.info("状态机触发成功：已打回 -> 重新提交, submitId: {}", submit.getSubmitId());
                            } else {
                                log.warn("当前状态{}不允许提交，submitId: {}", currentStatus, submit.getSubmitId());
                            }
                        } catch (Exception e) {
                            log.error("状态机触发失败", e);
                            // 不影响主流程，继续
                        }
                    } else {
                        // 教师批改或其他情况：只更新版本号
                        submit.setDocumentVersion(newVersion);
                        submit.setDocumentKey(newDocumentKey);

                        // 更新数据库
                        expTaskSubmitService.updateExpTaskSubmit(submit);

                        log.info("文档已保存（教师批改或编辑），已更新版本号, taskId: {}, userId: {}, version: {} -> {}, newKey: {}",
                            taskId, userId, currentVersion, newVersion, newDocumentKey);
                    }
                } else {
                    log.warn("未找到提交记录，taskId: {}, userId: {}", taskId, userId);
                }
            } else {
                log.debug("文件名不符合提交副本格式，跳过版本号更新: {}", objectName);
            }
        } catch (Exception e) {
            log.error("更新版本号失败", e);
            // 不抛出异常，避免影响文件保存
        }
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
