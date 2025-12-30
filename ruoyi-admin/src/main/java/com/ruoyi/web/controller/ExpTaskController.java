package com.ruoyi.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.alibaba.fastjson.JSON;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.config.document.Type;


import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;
import com.onlyoffice.service.documenteditor.callback.CallbackService;
import com.ruoyi.system.service.ConfigService;
import com.ruoyi.system.service.impl.OfficeServiceImpl;
import com.ruoyi.web.controller.pojo.OfficeResponse;
import com.ruoyi.web.controller.pojo.Response;
import com.ruoyi.web.controller.pojo.SaveRequestParams;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.ExpTask;
import com.ruoyi.system.domain.ExpTaskSubmit;
import com.ruoyi.system.service.IExpTaskService;
import com.ruoyi.system.service.IExpTaskSubmitService;
import com.ruoyi.web.core.config.MinioConfig;
import com.ruoyi.web.utils.MinioUtil;

/**
 * 实验任务Controller
 * 
 * @author ruoyi
 * @date 2024-03-01
 */
@Slf4j
@RestController
@RequestMapping("/Task")
public class ExpTaskController extends BaseController
{
    @Resource
    private MinioClient minioClient;

    @Resource
    private OfficeServiceImpl officeService;

    @Autowired
    private IExpTaskService expTaskService;

    @Autowired
    private IExpTaskSubmitService expTaskSubmitService;

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private MinioConfig minioConfig;

    @Resource
    private ConfigService configService;

    @Resource
    private CallbackService callbackService;

    @Resource
    private JwtManager jwtManager;

    @Value("${docservice.url}")
    private String officeRequestUrl;

    @Value("${docservice.security.enable}")
    private Boolean securityEnable;

    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.bucket-name}")
    private String bucketName;
    /**
     * 查询实验任务列表
     */
    @PreAuthorize("@ss.hasPermi('task:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(ExpTask expTask)
    {
        // 如果不是管理员，则根据用户类型进行过滤
        LoginUser loginUser = getLoginUser();
        if (loginUser != null && loginUser.getUser() != null && !loginUser.getUser().isAdmin())
        {
            // 检查用户是否有新增任务权限，如果有则说明是教师
            boolean isTeacher = SecurityUtils.hasPermi(loginUser.getPermissions(), "task:task:add");
            
            if (isTeacher)
            {
                // 教师端：只查询自己发布的任务
                expTask.setCreateBy(getUsername());
            }
            else
            {
                // 学生端：根据当前用户的部门ID查询相关任务
                Long deptId = getDeptId();
                if (deptId != null)
                {
                    expTask.setDeptId(deptId);
                }
            }
        }
        startPage();
        List<ExpTask> list = expTaskService.selectExpTaskList(expTask);
        return getDataTable(list);
    }

    /**
     * 导出实验任务列表
     */
    @PreAuthorize("@ss.hasPermi('task:task:export')")
    @Log(title = "实验任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ExpTask expTask)
    {
        // 如果不是管理员，则根据用户类型进行过滤
        LoginUser loginUser = getLoginUser();
        if (loginUser != null && loginUser.getUser() != null && !loginUser.getUser().isAdmin())
        {
            // 检查用户是否有新增任务权限，如果有则说明是教师
            boolean isTeacher = SecurityUtils.hasPermi(loginUser.getPermissions(), "task:task:add");
            
            if (isTeacher)
            {
                // 教师端：只导出自己发布的任务
                expTask.setCreateBy(getUsername());
            }
            else
            {
                // 学生端：根据当前用户的部门ID查询相关任务
                Long deptId = getDeptId();
                if (deptId != null)
                {
                    expTask.setDeptId(deptId);
                }
            }
        }
        List<ExpTask> list = expTaskService.selectExpTaskList(expTask);
        ExcelUtil<ExpTask> util = new ExcelUtil<>(ExpTask.class);
        util.exportExcel(response, list, "实验任务数据");
    }

    /**
     * 下载实验报告
     */
    @PreAuthorize("@ss.hasPermi('task:task:list')")
    @GetMapping("/download")
    public void downloadReport(@RequestParam("url") String url, HttpServletResponse response)
    {
        try
        {
            if (com.ruoyi.common.utils.StringUtils.isBlank(url))
            {
                logger.error("下载文件URL为空");
                return;
            }
            // 从完整URL中提取objectName
            // URL格式可能是: http://endpoint/bucketName/objectName 或直接是 objectName
            String objectName = url;
            if (url.contains("http://") || url.contains("https://"))
            {
                // 如果是完整URL，提取objectName部分
                // 查找最后一个斜杠后的内容，或者查找bucketName后的内容
                String bucketName = minioConfig.getBucketName();
                if (url.contains("/" + bucketName + "/"))
                {
                    // 提取bucketName之后的部分作为objectName
                    int index = url.indexOf("/" + bucketName + "/");
                    objectName = url.substring(index + bucketName.length() + 2);
                }
                else if (url.contains(bucketName + "/"))
                {
                    // 处理没有前导斜杠的情况
                    objectName = url.substring(url.indexOf(bucketName + "/") + bucketName.length() + 1);
                }
                else
                {
                    // 如果找不到bucketName，尝试提取最后一个斜杠后的内容
                    int lastSlash = url.lastIndexOf("/");
                    if (lastSlash > 0)
                    {
                        objectName = url.substring(lastSlash + 1);
                    }
                }
            }
            minioUtil.download(objectName, response);
        }
        catch (Exception e)
        {
            logger.error("下载文件失败", e);
        }
    }

    /**
     * 获取实验任务详细信息
     */
    @PreAuthorize("@ss.hasPermi('task:task:query')")
    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable("taskId") Long taskId)
    {
        return success(expTaskService.selectExpTaskByTaskId(taskId));
    }

    /**
     * 新增实验任务
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @Log(title = "实验任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ExpTask expTask)
    {
        expTask.setCreateBy(getUsername());
        return toAjax(expTaskService.insertExpTask(expTask));
    }

    /**
     * 修改实验任务
     */
    @PreAuthorize("@ss.hasPermi('task:task:edit')")
    @Log(title = "实验任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ExpTask expTask)
    {
        expTask.setUpdateBy(getUsername());
        return toAjax(expTaskService.updateExpTask(expTask));
    }

    /**
     * 删除实验任务
     */
    @PreAuthorize("@ss.hasPermi('task:task:remove')")
    @Log(title = "实验任务", businessType = BusinessType.DELETE)
	@DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(expTaskService.deleteExpTaskByTaskIds(taskIds));
    }

    /**
     * 文件上传（实验报告）
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @PostMapping("/upload")
    public AjaxResult uploadFile(@RequestParam("file") MultipartFile file)
    {
        try
        {
            if (file == null || file.isEmpty())
            {
                return error("上传文件不能为空");
            }
            String objectName = minioUtil.upload(file);
            if (objectName != null)
            {
                // 构建完整的访问URL
                String url = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + objectName;
                AjaxResult ajax = AjaxResult.success("文件上传成功");
                // FileUpload组件使用fileName作为URL
                ajax.put("fileName", url);
                ajax.put("url", url);
                ajax.put("originalFilename", file.getOriginalFilename());
                return ajax;
            }
            return error("文件上传失败");
        }
        catch (Exception e)
        {
            logger.error("文件上传失败", e);
            return error("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 获取OnlyOffice编辑器配置（允许匿名访问）
     */
    @Anonymous
    @GetMapping("/config")
    public Response<Config> getConfig(@RequestParam String fileUrl, @RequestParam Mode mode) throws UnsupportedEncodingException {
        Config config = this.configService.createConfig(fileUrl, mode, Type.DESKTOP);
        return Response.success(config);
    }

    /**
     * onlyfoofice回调接口，开始编辑、保存文件的时候会触发（允许匿名访问）
     * @param request
     * @param fileUrl
     * @param body
     * @return
     */
    @Anonymous
    @PostMapping("/callback")
    public String callback(final HttpServletRequest request,  // track file changes
                           @RequestParam("fileUrl") final String fileUrl,
                           @RequestBody final Callback body) {
        log.debug("onlyoffice回调参数{}", JSON.toJSONString(body));
        try {
//            String authorization = request.getHeader("Authorization");
//            if (StringUtils.isEmpty(authorization)) {
//                return "{\"error\":1,\"message\":\"Request payload is empty\"}";
//            }
//            String token = authorization.replace("Bearer ", "");
//            Callback callback = this.callbackService.verifyCallback(body, token);
//            this.callbackService.processCallback(callback, fileUrl);

            this.callbackService.processCallback(body, fileUrl);
        } catch (Exception e) {
            log.error("", e);
            return "{\"error\":1,\"message\":\"Request payload is empty\"}";
        }
        return "{\"error\":\"0\"}";
    }


    /**
     * 触发onlyoffice服务保存文档
     *
     * @param key 文件key
     * @return
     */
    @PostMapping("/save")
    public Response<?> saveFile(@RequestParam("key") String key) {
        SaveRequestParams params = new SaveRequestParams();
        params.setKey(key);

        RestTemplate restTemplate = new RestTemplate();
        String requestUrl = this.officeRequestUrl + "coauthoring/CommandService.ashx";

        // 根据配置决定发送格式
        ResponseEntity<OfficeResponse> response;
        if (Boolean.TRUE.equals(this.securityEnable)) {
            // 安全模式启用：发送JWT token
            String token = this.jwtManager.createToken(params);
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            response = restTemplate.postForEntity(requestUrl, data, OfficeResponse.class);
        } else {
            // 安全模式禁用：直接发送命令参数
            response = restTemplate.postForEntity(requestUrl, params, OfficeResponse.class);
        }

        OfficeResponse result = response.getBody();
        log.debug("发送保存请求，响应结果{}", result);
        if (result == null) {
            log.error("消息发送失败，未接收到响应体");
            return Response.failed("消息发送失败，未接收到响应体");
        }
        Integer error = result.getError();
        if (error == 0 || error == 4) {
            // error = 4，文档没有做任何修改
            // 请求成功
            return Response.success("保存成功");
        } else {
            return Response.failed("保存失败");
        }
    }

    @GetMapping("/pdf/export")
    public void exportPdf(@RequestParam String fileUrl, HttpServletResponse response) throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException {
        this.officeService.coverToPdf(fileUrl, response);
    }

    /**
     * 学生打开任务时创建报告副本
     * 从模板文件复制一份，每个学生有独立的文件
     */
    @PreAuthorize("@ss.hasPermi('task:task:query')")
    @PostMapping("/createCopy")
    public AjaxResult createCopy(@RequestBody Map<String, Object> params)
    {
        try
        {
            Long taskId = Long.valueOf(params.get("taskId").toString());

            // 获取当前登录用户信息
            LoginUser loginUser = getLoginUser();
            Long userId = loginUser.getUserId();

            // 检查是否已经创建过副本
            ExpTaskSubmit existSubmit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
            if (existSubmit != null && com.ruoyi.common.utils.StringUtils.isNotEmpty(existSubmit.getFileUrl()))
            {
                // 已经创建过副本，直接返回
                logger.info("学生已有副本文件, taskId: {}, userId: {}, fileUrl: {}", taskId, userId, existSubmit.getFileUrl());
                return success(existSubmit.getFileUrl());
            }

            // 获取任务信息
            ExpTask task = expTaskService.selectExpTaskByTaskId(taskId);
            if (task == null)
            {
                return error("任务不存在");
            }

            String templateUrl = task.getReportFileUrl();
            if (com.ruoyi.common.utils.StringUtils.isEmpty(templateUrl))
            {
                return error("任务模板文件不存在");
            }

            logger.info("开始创建副本, templateUrl: {}", templateUrl);

            // 从模板URL中提取文件名和扩展名
            String templateObjectName = templateUrl.replace(minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/", "");
            String extension = templateObjectName.substring(templateObjectName.lastIndexOf("."));

            // 生成副本文件名：taskId_userId_timestamp.扩展名
            long timestamp = System.currentTimeMillis();
            String copyFileName = String.format("%d_%d_%d%s", taskId, userId, timestamp, extension);

            // 生成副本对象名（按日期分文件夹）
            LocalDateTime now = LocalDateTime.now();
            String copyObjectName = String.format("/%d/%d/%d/submit_%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), copyFileName);

            // 从MinIO下载模板文件
            InputStream templateStream = minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(templateObjectName)
                    .build()
            );

            // 上传副本到MinIO
            PutObjectArgs putArgs = PutObjectArgs.builder()
                .bucket(minioConfig.getBucketName())
                .object(copyObjectName)
                .stream(templateStream, -1, 10485760) // 最小分片大小10MB
                .contentType("application/octet-stream")
                .build();

            minioClient.putObject(putArgs);
            templateStream.close();

            // 生成副本URL
            String copyUrl = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + copyObjectName;

            logger.info("副本创建成功, copyUrl: {}", copyUrl);

            // 创建提交记录（初始状态，file_url为副本URL）
            ExpTaskSubmit submit = new ExpTaskSubmit();
            submit.setTaskId(taskId);
            submit.setUserId(userId);
            submit.setUserName(loginUser.getUsername());
            submit.setFileUrl(copyUrl);
            submit.setStatus("0"); // 待批阅
            expTaskSubmitService.insertExpTaskSubmit(submit);

            return success(copyUrl);
        }
        catch (Exception e)
        {
            logger.error("创建副本失败", e);
            return error("创建副本失败：" + e.getMessage());
        }
    }

    /**
     * 学生提交任务
     * 提交流程：
     * 1. 先触发OnlyOffice保存文档到MinIO
     * 2. 保存成功后更新提交记录的提交时间
     */
    @PreAuthorize("@ss.hasPermi('task:task:query')")
    @Log(title = "提交任务", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public AjaxResult submitTask(@RequestBody Map<String, Object> params)
    {
        try
        {
            // 获取参数
            Long taskId = Long.valueOf(params.get("taskId").toString());
            String documentKey = params.get("documentKey") != null ? params.get("documentKey").toString() : null;
            String fileUrl = params.get("fileUrl") != null ? params.get("fileUrl").toString() : null;

            if (documentKey == null || documentKey.isEmpty())
            {
                return error("文档标识不能为空");
            }

            if (fileUrl == null || fileUrl.isEmpty())
            {
                return error("文件URL不能为空");
            }

            // 获取当前登录用户信息
            LoginUser loginUser = getLoginUser();
            Long userId = loginUser.getUserId();

            logger.info("学生提交任务, taskId: {}, userId: {}, fileUrl: {}", taskId, userId, fileUrl);

            // 1. 先触发OnlyOffice保存文档
            logger.info("开始触发OnlyOffice保存文档, documentKey: {}", documentKey);
            SaveRequestParams saveParams = new SaveRequestParams();
            saveParams.setKey(documentKey);

            RestTemplate restTemplate = new RestTemplate();
            String requestUrl = this.officeRequestUrl + "coauthoring/CommandService.ashx";

            // 根据配置决定发送格式
            ResponseEntity<OfficeResponse> response;
            if (Boolean.TRUE.equals(this.securityEnable)) {
                // 安全模式启用：发送JWT token
                String token = this.jwtManager.createToken(saveParams);
                Map<String, String> requestData = new HashMap<>();
                requestData.put("token", token);
                response = restTemplate.postForEntity(requestUrl, requestData, OfficeResponse.class);
            } else {
                // 安全模式禁用：直接发送命令参数
                response = restTemplate.postForEntity(requestUrl, saveParams, OfficeResponse.class);
            }

            OfficeResponse result = response.getBody();
            logger.info("OnlyOffice保存响应: {}", result);

            if (result == null)
            {
                logger.error("OnlyOffice保存失败：未接收到响应体");
                return error("保存文档失败，请重试");
            }

            Integer errorCode = result.getError();
            if (errorCode != 0 && errorCode != 4)
            {
                // error = 0: 保存成功
                // error = 4: 文档没有做任何修改（也视为成功）
                logger.error("OnlyOffice保存失败, error code: {}", errorCode);
                return error("保存文档失败，错误码：" + errorCode);
            }

            // 2. 文档保存成功后，更新提交时间
            logger.info("文档保存成功，更新提交时间");
            ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
            if (submit == null)
            {
                return error("未找到提交记录，请先打开编辑器");
            }

            submit.setSubmitTime(new Date());
            int updateResult = expTaskSubmitService.updateExpTaskSubmit(submit);

            if (updateResult > 0)
            {
                logger.info("任务提交成功, taskId: {}, userId: {}", taskId, userId);
                return success("提交成功");
            }
            else
            {
                logger.error("更新提交时间失败");
                return error("提交失败");
            }
        }
        catch (Exception e)
        {
            logger.error("提交任务失败", e);
            return error("提交失败：" + e.getMessage());
        }
    }

    /**
     * 上传新文件（OnlyOffice使用）
     */
    @PostMapping("/upload/new")
    public Response<String> uploadNewFile(@RequestParam("file") MultipartFile file) {
        // 上传文件到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            String fileName = file.getOriginalFilename();
            if (StringUtils.isEmpty(fileName)) {
                return Response.failed("文件名不能为空");
            }
            String suffix = fileName.substring(fileName.lastIndexOf("."));
            if (StringUtils.isEmpty(suffix)) {
                return Response.failed("文件后缀不能为空");
            }
            LocalDateTime date = LocalDateTime.now();
            int year = date.getYear();
            int month = date.getMonthValue() + 1;
            int day = date.getDayOfMonth();
            String newName = UUID.randomUUID() + suffix;
            String objectName = String.format("/%s/%s/%s/%s", year, month, day, newName);
            String contentType;
            // 将文档设置成下载类型
            if (Pattern.matches(".*\\.(docx|xlsx|pdf|pptx)$", fileName)) {
                contentType = "application/octet-stream";
            } else {
                contentType = file.getContentType();
            }
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(this.bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build();
            this.minioClient.putObject(args);
            String url = String.format("%s/%s%s", this.endpoint, this.bucketName, objectName);
            return Response.success(url);
        } catch (Exception e) {
            log.error("", e);
            return Response.failed();
        }
    }
}

