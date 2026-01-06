package com.ruoyi.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
import org.apache.commons.codec.digest.DigestUtils;
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

    @Autowired
    private com.ruoyi.system.service.ExcelTemplateExportService excelTemplateExportService;

    @Autowired
    private com.ruoyi.system.service.ReportStateMachineService reportStateMachineService;

    @Autowired
    private com.ruoyi.system.service.ISysDeptService deptService;

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
     * 查询实验任务列表（树形结构：部门-课程-任务）
     */
    @PreAuthorize("@ss.hasPermi('task:task:list')")
    @GetMapping("/tree")
    public AjaxResult getTaskTree(ExpTask expTask)
    {
        LoginUser loginUser = getLoginUser();

        // 教师只能查询自己发布的任务
        if (loginUser != null && loginUser.getUser() != null && !loginUser.getUser().isAdmin())
        {
            boolean isTeacher = SecurityUtils.hasPermi(loginUser.getPermissions(), "task:task:add");
            if (isTeacher)
            {
                expTask.setCreateBy(getUsername());
            }
        }

        // 查询所有任务
        List<ExpTask> taskList = expTaskService.selectExpTaskList(expTask);

        // 构建树形结构
        List<Map<String, Object>> treeData = buildTreeData(taskList);

        return success(treeData);
    }

    /**
     * 构建树形数据结构：部门 -> 课程 -> 任务
     */
    private List<Map<String, Object>> buildTreeData(List<ExpTask> taskList)
    {
        List<Map<String, Object>> result = new ArrayList<>();

        // 按部门分组
        Map<String, List<ExpTask>> deptMap = new LinkedHashMap<>();
        for (ExpTask task : taskList)
        {
            String deptKey = task.getDeptId() + "_" + task.getDeptName();
            deptMap.computeIfAbsent(deptKey, k -> new ArrayList<>()).add(task);
        }

        // 构建部门级节点
        for (Map.Entry<String, List<ExpTask>> deptEntry : deptMap.entrySet())
        {
            String deptKey = deptEntry.getKey();
            List<ExpTask> deptTasks = deptEntry.getValue();

            String[] parts = deptKey.split("_", 2);
            Long deptId = Long.valueOf(parts[0]);
            String deptName = parts[1];

            // 获取带年级的部门名称
            String deptNameWithGrade = getDeptNameWithGrade(deptId, deptName);

            Map<String, Object> deptNode = new HashMap<>();
            deptNode.put("id", "dept_" + deptId);
            deptNode.put("type", "dept");
            deptNode.put("deptId", deptId);
            deptNode.put("deptName", deptName);
            deptNode.put("name", deptNameWithGrade);

            // 统计部门级数据
            int deptTotalTasks = deptTasks.size();
            int deptPendingCount = deptTasks.stream().mapToInt(t -> t.getPendingCount() != null ? t.getPendingCount() : 0).sum();
            deptNode.put("totalTasks", deptTotalTasks);
            deptNode.put("pendingCount", deptPendingCount);

            // 按课程分组
            Map<String, List<ExpTask>> courseMap = new LinkedHashMap<>();
            for (ExpTask task : deptTasks)
            {
                courseMap.computeIfAbsent(task.getCourseName(), k -> new ArrayList<>()).add(task);
            }

            // 构建课程级节点
            List<Map<String, Object>> courseNodes = new ArrayList<>();
            for (Map.Entry<String, List<ExpTask>> courseEntry : courseMap.entrySet())
            {
                String courseName = courseEntry.getKey();
                List<ExpTask> courseTasks = courseEntry.getValue();

                Map<String, Object> courseNode = new HashMap<>();
                courseNode.put("id", "course_" + deptId + "_" + courseName);
                courseNode.put("type", "course");
                courseNode.put("courseName", courseName);
                courseNode.put("name", courseName);

                // 统计课程级数据
                int courseTotalTasks = courseTasks.size();
                int coursePendingCount = courseTasks.stream().mapToInt(t -> t.getPendingCount() != null ? t.getPendingCount() : 0).sum();
                courseNode.put("totalTasks", courseTotalTasks);
                courseNode.put("pendingCount", coursePendingCount);

                // 构建任务级节点
                List<Map<String, Object>> taskNodes = new ArrayList<>();
                for (ExpTask task : courseTasks)
                {
                    Map<String, Object> taskNode = new HashMap<>();
                    taskNode.put("id", "task_" + task.getTaskId());
                    taskNode.put("type", "task");
                    taskNode.put("taskId", task.getTaskId());
                    taskNode.put("taskName", task.getTaskName());
                    taskNode.put("name", task.getTaskName());
                    taskNode.put("courseName", task.getCourseName());
                    taskNode.put("deptName", task.getDeptName());
                    taskNode.put("createTime", task.getCreateTime());
                    taskNode.put("deadline", task.getDeadline());
                    taskNode.put("status", task.getStatus());
                    taskNode.put("submitCount", task.getSubmitCount() != null ? task.getSubmitCount() : 0);
                    taskNode.put("totalCount", task.getTotalCount() != null ? task.getTotalCount() : 0);
                    taskNode.put("reviewedCount", task.getReviewedCount() != null ? task.getReviewedCount() : 0);
                    taskNode.put("pendingCount", task.getPendingCount() != null ? task.getPendingCount() : 0);

                    taskNodes.add(taskNode);
                }

                courseNode.put("children", taskNodes);
                courseNodes.add(courseNode);
            }

            deptNode.put("children", courseNodes);
            result.add(deptNode);
        }

        return result;
    }

    /**
     * 获取带年级的部门名称
     * 向上查找父级部门链，找到包含4位数字年份的"级"部门（如"2022级"）
     *
     * @param deptId 部门ID
     * @param deptName 部门名称
     * @return 带年级的部门名称（如"2022级软件工程5班"）
     */
    private String getDeptNameWithGrade(Long deptId, String deptName)
    {
        try
        {
            // 如果部门名称本身就包含年份（4位数字开头），直接返回
            if (deptName.matches("^\\d{4}.*"))
            {
                return deptName;
            }

            // 查询当前部门信息
            com.ruoyi.common.core.domain.entity.SysDept dept = deptService.selectDeptById(deptId);
            if (dept == null || dept.getAncestors() == null)
            {
                return deptName;
            }

            // 解析祖级列表（格式："0,100,101,102"）
            String[] ancestorIds = dept.getAncestors().split(",");

            // 从最顶层开始查找包含年份的"级"部门
            for (String ancestorIdStr : ancestorIds)
            {
                if ("0".equals(ancestorIdStr))
                {
                    continue; // 跳过根节点
                }

                Long ancestorId = Long.valueOf(ancestorIdStr);
                com.ruoyi.common.core.domain.entity.SysDept ancestorDept = deptService.selectDeptById(ancestorId);

                if (ancestorDept != null && ancestorDept.getDeptName() != null)
                {
                    String ancestorName = ancestorDept.getDeptName();
                    // 匹配包含4位年份的部门名称（如"2022级"）
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{4})级");
                    java.util.regex.Matcher matcher = pattern.matcher(ancestorName);

                    if (matcher.find())
                    {
                        // 找到年份，拼接到班级名称前面
                        String year = matcher.group(1);
                        return year + "级" + deptName;
                    }
                }
            }

            // 如果没有找到年级部门，返回原始名称
            return deptName;
        }
        catch (Exception e)
        {
            logger.error("获取部门年级信息失败, deptId: {}, error: {}", deptId, e.getMessage());
            return deptName;
        }
    }

    /**
     * 查询实验任务列表
     */
    @PreAuthorize("@ss.hasPermi('task:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(ExpTask expTask)
    {
        // 如果不是管理员，则根据用户类型进行过滤
        LoginUser loginUser = getLoginUser();
        boolean isStudent = false;
        Long currentUserId = null;

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
                isStudent = true;
                currentUserId = loginUser.getUserId();
                Long deptId = getDeptId();
                if (deptId != null)
                {
                    expTask.setDeptId(deptId);
                }
            }
        }
        startPage();
        List<ExpTask> list = expTaskService.selectExpTaskList(expTask);

        // 格式化部门名称（添加年级信息）
        for (ExpTask task : list)
        {
            if (task.getDeptId() != null && task.getDeptName() != null)
            {
                String formattedDeptName = getDeptNameWithGrade(task.getDeptId(), task.getDeptName());
                task.setDeptName(formattedDeptName);
            }
        }

        // 如果是学生，查询每个任务的学生报告状态
        if (isStudent && currentUserId != null)
        {
            logger.info("========== 学生端查询报告状态 ==========");
            logger.info("当前登录学生ID: {}, 用户名: {}", currentUserId, loginUser.getUsername());
            logger.info("查询到的任务数量: {}", list.size());

            for (ExpTask task : list)
            {
                ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(task.getTaskId(), currentUserId);

                logger.info("任务ID: {}, 任务名称: {}, 查询结果: {}",
                    task.getTaskId(),
                    task.getTaskName(),
                    submit != null ? "找到提交记录" : "未找到提交记录");

                if (submit != null)
                {
                    logger.info("  - 提交记录ID: {}, 状态: {}, 用户ID: {}, 用户名: {}, 分数: {}, 更新时间: {}",
                        submit.getSubmitId(),
                        submit.getStatus(),
                        submit.getUserId(),
                        submit.getUserName(),
                        submit.getScore(),
                        submit.getUpdateTime());
                    task.setStudentSubmitStatus(submit.getStatus());
                    task.setScore(submit.getScore());
                    task.setReviewTime(submit.getUpdateTime());
                }
                else
                {
                    logger.warn("  - 未找到提交记录！任务ID={}, 学生ID={}", task.getTaskId(), currentUserId);
                    // 未创建提交记录，状态为null（前端可显示为"未开始"）
                    task.setStudentSubmitStatus(null);
                    task.setScore(null);
                    task.setReviewTime(null);
                }
            }
            logger.info("==========================================");
        }

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
     * 支持多选部门：如果提供了 deptIds，则为每个部门创建一个任务副本
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @Log(title = "实验任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ExpTask expTask)
    {
        expTask.setCreateBy(getUsername());

        // 检查是否多选了部门
        if (expTask.getDeptIds() != null && !expTask.getDeptIds().isEmpty())
        {
            logger.info("批量创建任务，部门数量: {}", expTask.getDeptIds().size());

            int successCount = 0;

            // 为每个部门创建任务副本
            for (Long deptId : expTask.getDeptIds())
            {
                try
                {
                    // 创建任务副本
                    ExpTask taskCopy = new ExpTask();
                    taskCopy.setTaskName(expTask.getTaskName());
                    taskCopy.setCourseName(expTask.getCourseName());
                    taskCopy.setDeptId(deptId);
                    taskCopy.setDeadline(expTask.getDeadline());
                    taskCopy.setStatus(expTask.getStatus());
                    taskCopy.setReportFileUrl(expTask.getReportFileUrl());
                    taskCopy.setAcademicTerm(expTask.getAcademicTerm());
                    taskCopy.setRemark(expTask.getRemark());
                    taskCopy.setCreateBy(getUsername());

                    // 保存任务
                    int result = expTaskService.insertExpTask(taskCopy);
                    if (result > 0)
                    {
                        successCount++;
                    }
                }
                catch (Exception e)
                {
                    logger.error("为部门 {} 创建任务失败", deptId, e);
                }
            }

            if (successCount == expTask.getDeptIds().size())
            {
                return AjaxResult.success(String.format("成功为 %d 个部门创建任务", successCount));
            }
            else if (successCount > 0)
            {
                return AjaxResult.success(String.format("成功为 %d/%d 个部门创建任务", successCount, expTask.getDeptIds().size()));
            }
            else
            {
                return AjaxResult.error("创建任务失败");
            }
        }
        else
        {
            // 单个部门的情况（向后兼容）
            return toAjax(expTaskService.insertExpTask(expTask));
        }
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
    public Response<Config> getConfig(@RequestParam String fileUrl,
                                       @RequestParam Mode mode,
                                       @RequestParam(required = false) String documentKey) throws UnsupportedEncodingException {
        logger.info("获取编辑器配置 - fileUrl: {}, mode: {}, documentKey: {}", fileUrl, mode, documentKey);

        Config config = this.configService.createConfig(fileUrl, mode, Type.DESKTOP, documentKey);

        // 记录生成的key用于排查问题
        if (config != null && config.getDocument() != null) {
            logger.info("返回配置 - documentKey: {}, fileUrl: {}", config.getDocument().getKey(), fileUrl);
        }

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
                // ✅ 如果报告被打回（状态4），强制生成新的documentKey以避免版本冲突
                if ("4".equals(existSubmit.getStatus())) {
                    Integer currentVersion = existSubmit.getDocumentVersion() != null ? existSubmit.getDocumentVersion() : 1;
                    Integer newVersion = currentVersion + 1;
                    String newDocumentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + newVersion + "_" + System.currentTimeMillis());

                    // 更新documentKey和版本号
                    existSubmit.setDocumentKey(newDocumentKey);
                    existSubmit.setDocumentVersion(newVersion);
                    expTaskSubmitService.updateExpTaskSubmit(existSubmit);

                    logger.info("打回报告重新打开，强制更新documentKey, taskId: {}, userId: {}, version: {} -> {}, newKey: {}",
                        taskId, userId, currentVersion, newVersion, newDocumentKey);

                    AjaxResult result = success(existSubmit.getFileUrl());
                    result.put("documentKey", newDocumentKey);
                    result.put("documentVersion", newVersion);
                    result.put("submitStatus", existSubmit.getStatus());
                    return result;
                }

                // 已经创建过副本，返回文件URL和documentKey（使用数据库中最新的documentKey）
                logger.info("学生已有副本文件, taskId: {}, userId: {}, fileUrl: {}, documentKey: {}, version: {}, status: {}",
                    taskId, userId, existSubmit.getFileUrl(), existSubmit.getDocumentKey(), existSubmit.getDocumentVersion(), existSubmit.getStatus());
                AjaxResult result = success(existSubmit.getFileUrl());
                result.put("documentKey", existSubmit.getDocumentKey());
                result.put("documentVersion", existSubmit.getDocumentVersion());
                result.put("submitStatus", existSubmit.getStatus()); // ✅ 返回报告状态
                return result;
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

            // 初始版本号为1
            Integer initialVersion = 1;

            // 生成带版本号的documentKey (包含taskId、userId和版本号)
            String documentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + initialVersion);
            logger.info("生成documentKey: {}, version: {}", documentKey, initialVersion);

            // 创建提交记录（初始状态，file_url为副本URL）
            ExpTaskSubmit submit = new ExpTaskSubmit();
            submit.setTaskId(taskId);
            submit.setUserId(userId);
            submit.setUserName(loginUser.getUsername());
            submit.setFileUrl(copyUrl);
            submit.setDocumentKey(documentKey);
            submit.setDocumentVersion(initialVersion); // 设置初始版本号
            submit.setStatus("0"); // 草稿
            expTaskSubmitService.insertExpTaskSubmit(submit);

            AjaxResult result = success(copyUrl);
            result.put("documentKey", documentKey);
            result.put("documentVersion", initialVersion);
            result.put("submitStatus", "0"); // ✅ 返回报告状态（草稿）
            return result;
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

            // 获取提交记录
            ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
            if (submit == null)
            {
                return error("未找到提交记录，请先打开编辑器");
            }

            if (errorCode == 4)
            {
                // error = 4: 文档没有做任何修改
                // OnlyOffice不会触发callback，直接完成提交
                logger.info("文档没有修改，直接完成提交");
                submit.setSubmitTime(new Date());
                submit.setSubmitPending(0);
                int updateResult = expTaskSubmitService.updateExpTaskSubmit(submit);

                if (updateResult > 0)
                {
                    // 根据当前状态触发相应的状态机转换
                    String currentStatus = submit.getStatus();
                    try
                    {
                        if ("0".equals(currentStatus))
                        {
                            // 草稿 -> 已提交
                            reportStateMachineService.submitReport(submit.getSubmitId());
                            logger.info("状态机触发成功：草稿 -> 已提交, submitId: {}", submit.getSubmitId());
                        }
                        else if ("4".equals(currentStatus))
                        {
                            // 已打回 -> 重新提交
                            reportStateMachineService.resubmit(submit.getSubmitId());
                            logger.info("状态机触发成功：已打回 -> 重新提交, submitId: {}", submit.getSubmitId());
                        }
                        else
                        {
                            logger.warn("当前状态{}不允许提交，submitId: {}", currentStatus, submit.getSubmitId());
                        }
                    }
                    catch (Exception e)
                    {
                        logger.error("状态机触发失败", e);
                        // 不影响主流程，继续返回成功
                    }

                    logger.info("提交成功（无修改）, taskId: {}, userId: {}", taskId, userId);
                    return success("提交成功");
                }
                else
                {
                    logger.error("更新提交记录失败");
                    return error("提交失败");
                }
            }
            else if (errorCode == 0)
            {
                // error = 0: 保存成功，文档有修改
                // 设置提交中状态，等待callback保存文件后更新submit_time
                logger.info("OnlyOffice保存命令发送成功，设置提交中状态");
                submit.setSubmitPending(1);
                int updateResult = expTaskSubmitService.updateExpTaskSubmit(submit);

                if (updateResult > 0)
                {
                    logger.info("已标记为提交中, taskId: {}, userId: {}", taskId, userId);
                    return success("正在保存，请稍候...");
                }
                else
                {
                    logger.error("设置提交中状态失败");
                    return error("提交失败");
                }
            }
            else
            {
                // 其他错误码
                logger.error("OnlyOffice保存失败, error code: {}", errorCode);
                return error("保存文档失败，错误码：" + errorCode);
            }
        }
        catch (Exception e)
        {
            logger.error("提交任务失败", e);
            return error("提交失败：" + e.getMessage());
        }
    }

    /**
     * 检查任务提交状态
     * 前端轮询此接口，检查callback是否已成功保存文件到MinIO
     */
    @PreAuthorize("@ss.hasPermi('task:task:query')")
    @GetMapping("/checkSubmitStatus")
    public AjaxResult checkSubmitStatus(@RequestParam Long taskId)
    {
        try
        {
            // 获取当前登录用户信息
            LoginUser loginUser = getLoginUser();
            Long userId = loginUser.getUserId();

            ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
            if (submit == null)
            {
                return error("未找到提交记录");
            }

            AjaxResult result = success();
            result.put("submitPending", submit.getSubmitPending());
            result.put("submitTime", submit.getSubmitTime());

            // submitPending=0 且 submitTime不为空，说明提交成功
            if (submit.getSubmitPending() == 0 && submit.getSubmitTime() != null)
            {
                result.put("status", "success");
                result.put("message", "提交成功");
            }
            // submitPending=1，说明正在提交中
            else if (submit.getSubmitPending() == 1)
            {
                result.put("status", "pending");
                result.put("message", "正在保存中...");
            }
            // 其他情况
            else
            {
                result.put("status", "not_submitted");
                result.put("message", "未提交");
            }

            return result;
        }
        catch (Exception e)
        {
            logger.error("检查提交状态失败", e);
            return error("检查状态失败：" + e.getMessage());
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

    // ==================== 批改报告相关接口 ====================

    /**
     * 获取任务的提交列表（教师批改用）
     * ✅ 显示任务所属部门的所有学生（包括未提交的）
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @GetMapping("/submit/list/{taskId}")
    public TableDataInfo getSubmitList(@PathVariable Long taskId, ExpTaskSubmit expTaskSubmit)
    {
        try
        {
            // 1. 获取任务信息（获取dept_id）
            ExpTask task = expTaskService.selectExpTaskByTaskId(taskId);
            if (task == null)
            {
                logger.error("任务不存在, taskId: {}", taskId);
                return getDataTable(new java.util.ArrayList<>());
            }

            Long deptId = task.getDeptId();
            if (deptId == null)
            {
                logger.error("任务没有关联部门, taskId: {}", taskId);
                return getDataTable(new java.util.ArrayList<>());
            }

            logger.info("查询批改列表, taskId: {}, deptId: {}", taskId, deptId);

            // 2. 查询该部门下的所有学生及其提交情况
            startPage();
            expTaskSubmit.setTaskId(taskId);
            expTaskSubmit.setDeptId(deptId); // ✅ 设置部门ID
            List<ExpTaskSubmit> list = expTaskSubmitService.selectExpTaskSubmitListWithAllStudents(expTaskSubmit);

            logger.info("查询到{}条记录（包括未提交的学生）", list.size());

            return getDataTable(list);
        }
        catch (Exception e)
        {
            logger.error("获取提交列表失败", e);
            return getDataTable(new java.util.ArrayList<>());
        }
    }

    /**
     * 获取批改详情（教师端）
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @GetMapping("/submit/{submitId}")
    public AjaxResult getSubmitDetail(@PathVariable Long submitId)
    {
        try
        {
            ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitBySubmitId(submitId);
            if (submit == null)
            {
                return error("提交记录不存在");
            }
            return success(submit);
        }
        catch (Exception e)
        {
            logger.error("获取批改详情失败", e);
            return error("获取批改详情失败：" + e.getMessage());
        }
    }

    /**
     * 学生端：查看自己的提交详情（包括评分和评语）
     */
    @GetMapping("/submit/my/{taskId}")
    public AjaxResult getMySubmitDetail(@PathVariable Long taskId)
    {
        try
        {
            // 获取当前登录学生的用户ID
            Long userId = SecurityUtils.getUserId();

            // 查询该学生在该任务下的提交记录
            ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);

            if (submit == null)
            {
                return error("未找到提交记录");
            }

            return success(submit);
        }
        catch (Exception e)
        {
            logger.error("学生查看提交详情失败", e);
            return error("查看提交详情失败：" + e.getMessage());
        }
    }

    /**
     * 保存批改结果
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @Log(title = "批改报告", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/review")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public AjaxResult saveReview(@RequestBody ExpTaskSubmit expTaskSubmit)
    {
        try
        {
            // 获取当前登录教师信息
            LoginUser loginUser = getLoginUser();

            // 获取当前提交记录
            ExpTaskSubmit currentSubmit = expTaskSubmitService.selectExpTaskSubmitBySubmitId(expTaskSubmit.getSubmitId());
            if (currentSubmit == null)
            {
                return error("提交记录不存在");
            }

            String currentStatus = currentSubmit.getStatus();
            logger.info("批改报告，当前状态: {}, submitId: {}", currentStatus, expTaskSubmit.getSubmitId());

            // 更新批改信息（分数和评语）
            expTaskSubmit.setUpdateBy(getUsername());
            expTaskSubmit.setUpdateTime(new Date());

            // 先更新分数和评语
            int result = expTaskSubmitService.updateExpTaskSubmit(expTaskSubmit);

            if (result > 0)
            {
                // ✅ 触发状态机转换（确保在同一事务中，失败会回滚）
                // 如果是"已提交"状态(1)，先开始批阅，再批阅通过
                if ("1".equals(currentStatus))
                {
                    reportStateMachineService.startReview(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：已提交 -> 批阅中, submitId: {}", expTaskSubmit.getSubmitId());

                    reportStateMachineService.approve(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：批阅中 -> 已批阅, submitId: {}", expTaskSubmit.getSubmitId());
                }
                // 如果是"重新提交"状态(5)，先开始批阅，再批阅通过
                else if ("5".equals(currentStatus))
                {
                    reportStateMachineService.startReview(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：重新提交 -> 批阅中, submitId: {}", expTaskSubmit.getSubmitId());

                    reportStateMachineService.approve(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：批阅中 -> 已批阅, submitId: {}", expTaskSubmit.getSubmitId());
                }
                // 如果是"批阅中"状态(2)，直接批阅通过
                else if ("2".equals(currentStatus))
                {
                    reportStateMachineService.approve(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：批阅中 -> 已批阅, submitId: {}", expTaskSubmit.getSubmitId());
                }
                // 如果已经是"已批阅"状态(3)，需要重新开始批阅流程，再批阅通过
                else if ("3".equals(currentStatus))
                {
                    reportStateMachineService.startReview(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：已批阅 -> 批阅中（重新批改）, submitId: {}", expTaskSubmit.getSubmitId());

                    reportStateMachineService.approve(expTaskSubmit.getSubmitId());
                    logger.info("状态机触发成功：批阅中 -> 已批阅, submitId: {}", expTaskSubmit.getSubmitId());
                }
                else
                {
                    logger.warn("当前状态{}不适合批改操作，submitId: {}", currentStatus, expTaskSubmit.getSubmitId());
                }

                logger.info("批改成功, submitId: {}, score: {}",
                    expTaskSubmit.getSubmitId(), expTaskSubmit.getScore());
                return success("批改成功");
            }
            else
            {
                return error("批改失败");
            }
        }
        catch (Exception e)
        {
            logger.error("批改失败", e);
            // 🔴 重要：抛出异常以触发事务回滚
            throw new RuntimeException("批改失败：" + e.getMessage(), e);
        }
    }

    /**
     * 导出单个任务的成绩（基于模板）
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @Log(title = "导出成绩", businessType = BusinessType.EXPORT)
    @GetMapping("/submit/export/{taskId}")
    public void exportGrades(@PathVariable Long taskId, HttpServletResponse response)
    {
        try
        {
            // 1. 获取任务信息
            ExpTask task = expTaskService.selectExpTaskByTaskId(taskId);

            // 2. 获取该任务所属部门的所有学生提交记录（包括未提交的）
            ExpTaskSubmit querySubmit = new ExpTaskSubmit();
            querySubmit.setTaskId(taskId);
            querySubmit.setDeptId(task.getDeptId());
            List<ExpTaskSubmit> list = expTaskSubmitService.selectExpTaskSubmitListWithAllStudents(querySubmit);

            // 3. 使用模板导出服务导出成绩
            excelTemplateExportService.exportGradesByTemplate(task, list, response);
        }
        catch (Exception e)
        {
            logger.error("导出成绩失败", e);
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("utf-8");
                response.getWriter().write("{\"msg\":\"导出成绩失败：" + e.getMessage() + "\",\"code\":500}");
            } catch (IOException ioException) {
                logger.error("写入错误响应失败", ioException);
            }
        }
    }

    /**
     * 横向汇总导出成绩(所有选中的实验在同一个Sheet中,每个实验占一列,只导出成绩不导出评语)
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @Log(title = "横向汇总导出成绩", businessType = BusinessType.EXPORT)
    @PostMapping("/submit/horizontalSummaryExport")
    public void horizontalSummaryExport(@RequestParam String taskIds, HttpServletResponse response)
    {
        try
        {
            // 1. 解析任务ID列表
            String[] taskIdArray = taskIds.split(",");
            List<Long> taskIdList = new java.util.ArrayList<>();
            for (String idStr : taskIdArray) {
                taskIdList.add(Long.parseLong(idStr.trim()));
            }

            // 2. 获取所有任务信息
            List<ExpTask> tasks = new java.util.ArrayList<>();
            for (Long taskId : taskIdList) {
                ExpTask task = expTaskService.selectExpTaskByTaskId(taskId);
                if (task != null) {
                    tasks.add(task);
                }
            }

            if (tasks.isEmpty()) {
                throw new RuntimeException("未找到任务信息");
            }

            // 3. 验证：检查是否为同一部门
            Long deptId = tasks.get(0).getDeptId();
            for (ExpTask task : tasks) {
                if (!task.getDeptId().equals(deptId)) {
                    throw new RuntimeException("只能导出同一班级的任务成绩");
                }
            }

            // 4. 验证：检查是否为同一课程
            String courseName = tasks.get(0).getCourseName();
            for (ExpTask task : tasks) {
                if (!task.getCourseName().equals(courseName)) {
                    throw new RuntimeException("只能导出同一课程的任务成绩");
                }
            }

            // 5. 获取该部门的学生提交记录
            Map<Long, List<ExpTaskSubmit>> submitListMap = new java.util.HashMap<>();
            for (ExpTask task : tasks) {
                ExpTaskSubmit querySubmit = new ExpTaskSubmit();
                querySubmit.setTaskId(task.getTaskId());
                querySubmit.setDeptId(task.getDeptId());
                List<ExpTaskSubmit> list = expTaskSubmitService.selectExpTaskSubmitListWithAllStudents(querySubmit);
                submitListMap.put(task.getTaskId(), list);
            }

            logger.info("横向汇总导出：班级={}, 课程={}, 任务数={}",
                tasks.get(0).getDeptName(), courseName, tasks.size());

            // 6. 使用模板导出服务横向汇总导出成绩
            excelTemplateExportService.horizontalSummaryExport(tasks, submitListMap, response);
        }
        catch (Exception e)
        {
            logger.error("横向汇总导出成绩失败", e);
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("utf-8");
                response.getWriter().write("{\"msg\":\"横向汇总导出成绩失败：" + e.getMessage() + "\",\"code\":500}");
            } catch (IOException ioException) {
                logger.error("写入错误响应失败", ioException);
            }
        }
    }

    /**
     * 获取提交ID列表（用于上一个/下一个导航）
     * 只返回待批改的报告（已提交、批阅中、重新提交状态）
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @GetMapping("/submit/idList/{taskId}")
    public AjaxResult getSubmitIdList(@PathVariable Long taskId)
    {
        try
        {
            ExpTaskSubmit querySubmit = new ExpTaskSubmit();
            querySubmit.setTaskId(taskId);
            List<ExpTaskSubmit> list = expTaskSubmitService.selectExpTaskSubmitList(querySubmit);

            // 只返回待批改的记录的ID列表（已提交1、批阅中2、重新提交5）
            List<Long> idList = list.stream()
                .filter(submit -> submit.getSubmitTime() != null)  // 已提交
                .filter(submit -> {
                    String status = submit.getStatus();
                    // 只包含：已提交(1)、批阅中(2)、重新提交(5)
                    return "1".equals(status) || "2".equals(status) || "5".equals(status);
                })
                .map(ExpTaskSubmit::getSubmitId)
                .collect(java.util.stream.Collectors.toList());

            logger.info("任务{}的待批改报告数量: {}", taskId, idList.size());
            return success(idList);
        }
        catch (Exception e)
        {
            logger.error("获取提交ID列表失败", e);
            return error("获取提交ID列表失败：" + e.getMessage());
        }
    }

    // ==================== 状态机相关接口 ====================

    /**
     * 触发状态转换（通用接口）
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @PostMapping("/submit/fire/{submitId}/{trigger}")
    public AjaxResult fireStateMachine(@PathVariable Long submitId, @PathVariable String trigger)
    {
        try
        {
            com.ruoyi.system.domain.enums.ReportTrigger reportTrigger =
                com.ruoyi.system.domain.enums.ReportTrigger.valueOf(trigger);
            boolean result = reportStateMachineService.fire(submitId, reportTrigger);
            return result ? success("操作成功") : error("操作失败");
        }
        catch (IllegalArgumentException e)
        {
            logger.error("无效的触发器: {}", trigger);
            return error("无效的操作");
        }
        catch (Exception e)
        {
            logger.error("状态转换失败", e);
            return error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 打回报告
     */
    @PreAuthorize("@ss.hasPermi('task:task:add')")
    @PostMapping("/submit/reject/{submitId}")
    public AjaxResult rejectReport(@PathVariable Long submitId, @RequestParam String reason)
    {
        try
        {
            boolean result = reportStateMachineService.reject(submitId, reason);
            return result ? success("已打回") : error("打回失败");
        }
        catch (Exception e)
        {
            logger.error("打回报告失败", e);
            return error("打回失败：" + e.getMessage());
        }
    }

    /**
     * 获取允许的操作列表
     */
    @GetMapping("/submit/actions/{submitId}")
    public AjaxResult getPermittedActions(@PathVariable Long submitId)
    {
        try
        {
            Iterable<com.ruoyi.system.domain.enums.ReportTrigger> triggers =
                reportStateMachineService.getPermittedTriggers(submitId);
            return success(triggers);
        }
        catch (Exception e)
        {
            logger.error("获取允许操作失败", e);
            return error("获取允许操作失败：" + e.getMessage());
        }
    }

    /**
     * 检查是否允许操作
     */
    @GetMapping("/submit/canFire/{submitId}/{trigger}")
    public AjaxResult canFire(@PathVariable Long submitId, @PathVariable String trigger)
    {
        try
        {
            com.ruoyi.system.domain.enums.ReportTrigger reportTrigger =
                com.ruoyi.system.domain.enums.ReportTrigger.valueOf(trigger);
            boolean result = reportStateMachineService.canFire(submitId, reportTrigger);
            return success(result);
        }
        catch (Exception e)
        {
            logger.error("检查操作权限失败", e);
            return error("检查操作权限失败：" + e.getMessage());
        }
    }
}

