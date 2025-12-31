# "文档版本已过期" 问题排查指南

## 🔍 问题描述
在打开任务的"在线完成"时，偶尔会出现"文档版本已过期"错误，OnlyOffice编辑器无法正常加载。

## 📊 诊断工具

现在系统已添加详细的日志，帮助你定位问题发生的具体时机和原因。

### **前端控制台日志**
打开浏览器开发者工具（F12），查看Console标签页，关注以下关键日志：

#### 正常流程日志：
```
=== 开始创建实验报告副本 ===
taskId: 1
templateUrl: http://...

=== 副本创建成功 ===
fileUrl: http://47.115.163.152:10001/winter/2025/12/30/submit_1_2_xxx.docx
documentKey: abc123...

=== 开始获取编辑器配置 ===
fileUrl: http://...
documentKey: abc123...
调用 getConfig(fileUrl, mode, documentKey)

=== 获取到编辑器配置 ===
config.document.key: abc123...
config.document.url: http://...
config.editorConfig.mode: edit

[OnlyOffice] 文档已准备就绪
```

#### 异常流程日志（documentKey不一致）：
```
=== 副本创建成功 ===
documentKey: abc123...

=== 获取到编辑器配置 ===
config.document.key: xyz789...

⚠️⚠️⚠️ 严重警告：documentKey不一致！⚠️⚠️⚠️
前端保存的documentKey: abc123...
后端返回的documentKey: xyz789...
这会导致"文档版本已过期"错误！
```

#### 版本过期错误日志：
```
⚠️⚠️⚠️ [OnlyOffice] 文档版本过期！⚠️⚠️⚠️
事件详情: {...}
当前 documentKey: abc123...
当前 fileUrl: http://...
```

### **后端日志**
查看后端控制台或日志文件，关注以下日志：

#### createCopy日志：
```
开始创建副本, templateUrl: http://...
副本创建成功, copyUrl: http://...
生成documentKey: abc123...
```

#### getConfig日志：
```
获取编辑器配置 - fileUrl: http://..., mode: EDIT, documentKey: abc123...
使用自定义documentKey: abc123...
返回配置 - documentKey: abc123..., fileUrl: http://...
```

OR（如果使用了自动生成）：
```
获取编辑器配置 - fileUrl: http://..., mode: EDIT, documentKey: null
自动生成documentKey: xyz789...
返回配置 - documentKey: xyz789..., fileUrl: http://...
```

## 🎯 可能的触发场景

### **场景1: 提交后重新打开**
**操作步骤：**
1. 学生打开任务编辑器
2. 编辑文档
3. 点击"提交任务"
4. 关闭编辑器
5. 再次点击"在线完成"

**预期行为：**
- createCopy应该返回已存在的documentKey
- getConfig应该使用这个documentKey
- 两个key应该完全一致

**异常情况：**
- 如果callback保存文件后，MinIO文件的lastModified改变
- 但我们使用的是基于taskId和userId的稳定key，理论上不会受影响

**排查方法：**
查看前端日志，对比两次打开时的documentKey是否一致：
```
第一次打开: documentKey: abc123...
提交任务...
第二次打开: documentKey: abc123...  (应该相同)
```

### **场景2: documentKey传递丢失**
**可能原因：**
- createCopy返回的response格式错误，前端没有正确提取documentKey
- 前端页面刷新，documentKey丢失

**排查方法：**
1. 查看前端日志中的警告信息：
```
⚠️ 警告：documentKey为空，可能导致版本冲突！
```

2. 检查createCopy的响应：
```javascript
console.log('createCopy响应:', response)
console.log('response.documentKey:', response.documentKey)
```

### **场景3: 浏览器缓存问题**
**可能原因：**
- 浏览器缓存了旧的JavaScript代码
- OnlyOffice API缓存了旧的文档配置

**解决方法：**
1. 强制刷新页面：Ctrl + Shift + R (Windows) 或 Cmd + Shift + R (Mac)
2. 清空浏览器缓存
3. 重启浏览器

### **场景4: 并发访问**
**可能原因：**
- 同一个学生在多个浏览器/标签页同时打开同一任务
- 不同的标签页使用了不同的documentKey

**排查方法：**
- 检查是否有多个标签页打开了编辑器
- 关闭其他标签页，只保留一个

## 🛠️ 调试步骤

### **步骤1: 复现问题**
1. 打开浏览器开发者工具（F12）
2. 切换到Console标签页
3. 执行触发问题的操作
4. 截图保存所有日志

### **步骤2: 分析日志**
查找以下关键信息：
- ✅ 第一个documentKey是什么？（从createCopy响应中）
- ✅ 第二个documentKey是什么？（从getConfig响应中）
- ✅ 两个key是否一致？
- ✅ 是否有"⚠️ 严重警告"？

### **步骤3: 检查数据库**
查询exp_task_submit表：
```sql
SELECT task_id, user_id, file_url, document_key, submit_pending, submit_time
FROM exp_task_submit
WHERE task_id = {你的taskId} AND user_id = {你的userId};
```

确认：
- ✅ document_key字段有值
- ✅ file_url字段正确
- ✅ 如果已提交，submit_pending应该是0

### **步骤4: 检查文件**
1. 从file_url中提取MinIO文件路径
2. 登录MinIO控制台查看文件是否存在
3. 检查文件的最后修改时间

## 🔧 解决方案

### **临时解决方案**
如果遇到"文档版本已过期"错误：
1. **等待2秒**：系统会自动重试，使用相同的documentKey
2. **如果还是失败**：关闭编辑器，重新点击"在线完成"
3. **如果仍然失败**：清空浏览器缓存，强制刷新页面

### **永久解决方案**

#### 方案1: 确保documentKey传递正确
**检查createCopy接口返回：**
```java
// ExpTaskController.java createCopy方法
AjaxResult result = success(existSubmit.getFileUrl());
result.put("documentKey", existSubmit.getDocumentKey());  // 必须返回
return result;
```

#### 方案2: 数据库documentKey缺失
如果数据库中document_key字段为NULL：
```sql
-- 重新生成documentKey
UPDATE exp_task_submit
SET document_key = SHA2(CONCAT('task_', task_id, '_user_', user_id), 256)
WHERE document_key IS NULL OR document_key = '';
```

#### 方案3: 清除缓存重新创建
如果某个记录的documentKey一直有问题：
```sql
-- 删除有问题的记录（谨慎操作，会丢失提交历史）
DELETE FROM exp_task_submit
WHERE task_id = {taskId} AND user_id = {userId};

-- 重新打开任务，系统会创建新的记录
```

## 📋 预防措施

### **1. 确保数据库字段存在**
```sql
-- 检查字段是否存在
SHOW COLUMNS FROM exp_task_submit LIKE 'document_key';

-- 如果不存在，执行迁移SQL
ALTER TABLE `exp_task_submit`
ADD COLUMN `document_key` varchar(128) DEFAULT NULL COMMENT 'OnlyOffice文档唯一标识key' AFTER `file_url`,
ADD COLUMN `submit_pending` tinyint(1) DEFAULT 0 COMMENT '是否正在提交中(0否 1是)' AFTER `document_key`;
```

### **2. 定期检查日志**
查看是否有documentKey不一致的警告：
```bash
# Linux
grep "documentKey不一致" /path/to/logs/*.log

# Windows PowerShell
Select-String -Path "E:\logs\*.log" -Pattern "documentKey不一致"
```

### **3. 监控异常率**
统计版本过期错误的发生频率：
```sql
-- 查看提交记录的异常分布
SELECT
    DATE(create_time) as date,
    COUNT(*) as total,
    SUM(CASE WHEN document_key IS NULL THEN 1 ELSE 0 END) as missing_key
FROM exp_task_submit
GROUP BY DATE(create_time)
ORDER BY date DESC;
```

## 📞 上报问题

如果以上方法都无法解决，请提供以下信息：

1. **前端控制台完整日志**（截图或复制文本）
2. **后端日志**（从createCopy到getConfig的完整日志）
3. **数据库记录**：
```sql
SELECT * FROM exp_task_submit
WHERE task_id = {taskId} AND user_id = {userId};
```
4. **触发条件**：
   - 什么时候出现？（首次打开 / 提交后重新打开 / 其他）
   - 是否可以稳定复现？
   - 浏览器和版本

## ✅ 验证修复

修复后，按以下流程验证：

1. ✅ 首次打开任务 → 查看日志 → documentKey正常生成
2. ✅ 编辑文档 → 保存 → 无异常
3. ✅ 提交任务 → 等待callback成功
4. ✅ 关闭编辑器
5. ✅ 重新打开任务 → 查看日志 → documentKey与之前一致
6. ✅ 编辑器正常加载，无"版本已过期"错误
7. ✅ 重复步骤3-6多次，确保稳定

如果所有步骤都通过，说明问题已解决！🎉
