package com.ruoyi.web.controller;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.ExpTask;
import com.ruoyi.system.service.IExpTaskService;
import com.ruoyi.web.core.config.MinioConfig;
import com.ruoyi.web.utils.MinioUtil;

/**
 * 实验任务Controller
 * 
 * @author ruoyi
 * @date 2024-03-01
 */
@RestController
@RequestMapping("/Task")
public class ExpTaskController extends BaseController
{
    @Autowired
    private IExpTaskService expTaskService;

    @Autowired
    private MinioUtil minioUtil;

    @Autowired
    private MinioConfig minioConfig;

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
}

