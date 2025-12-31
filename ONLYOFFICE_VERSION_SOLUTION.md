# OnlyOffice文档版本管理解决方案

## 问题背景

OnlyOffice编辑器采用基于key的缓存机制来验证文档版本：
- 当使用相同的key打开文档时，OnlyOffice会检查Redis缓存中的版本
- 如果文件内容已更新但key未变，会提示"文档版本已更改"错误
- 这导致用户无法正常打开已保存的文档

## 解决方案

采用**文档版本号管理**方案，核心思路：
1. 在数据库中维护每个文档的版本号
2. 将版本号包含在documentKey中
3. 每次保存成功后版本号+1，生成新的documentKey
4. 下次打开时使用新的documentKey，避免版本冲突

## 实现步骤

### 1. 数据库迁移

执行SQL脚本添加版本号字段：

```sql
-- sql/add_document_version.sql
ALTER TABLE exp_task_submit
ADD COLUMN document_version INT DEFAULT 1 COMMENT '文档版本号，每次保存后自动+1';

UPDATE exp_task_submit
SET document_version = 1
WHERE document_version IS NULL;

CREATE INDEX idx_task_user ON exp_task_submit(task_id, user_id);
```

### 2. 实体类修改

**ExpTaskSubmit.java** 添加版本号字段：

```java
/** 文档版本号（用于解决OnlyOffice版本冲突） */
private Integer documentVersion;

public void setDocumentVersion(Integer documentVersion) {
    this.documentVersion = documentVersion;
}

public Integer getDocumentVersion() {
    return documentVersion;
}
```

### 3. 创建副本时生成带版本号的Key

**ExpTaskController.createCopy()** 修改：

```java
// 初始版本号为1
Integer initialVersion = 1;

// 生成带版本号的documentKey (包含taskId、userId和版本号)
String documentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + initialVersion);

submit.setDocumentKey(documentKey);
submit.setDocumentVersion(initialVersion); // 设置初始版本号
```

### 4. 回调接口更新版本号

**CallbackServiceImpl.handlerForcesave()** 修改：

```java
// 查询提交记录
ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(taskId, userId);
if (submit != null && submit.getSubmitPending() == 1) {
    // 更新提交时间
    submit.setSubmitTime(new Date());
    submit.setSubmitPending(0);

    // ✅ 重要：版本号+1，并生成新的documentKey（解决版本冲突问题）
    Integer currentVersion = submit.getDocumentVersion() != null ? submit.getDocumentVersion() : 1;
    Integer newVersion = currentVersion + 1;
    String newDocumentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + newVersion);

    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);

    expTaskSubmitService.updateExpTaskSubmit(submit);
    log.info("任务提交成功，版本号: {} -> {}, newKey: {}", currentVersion, newVersion, newDocumentKey);
}
```

## 工作流程

### 首次创建副本
```
学生打开任务
  ↓
createCopy() 创建副本
  ↓
生成 documentKey = hash("task_1_user_100_v1")
  ↓
documentVersion = 1
  ↓
存入数据库
```

### 编辑并保存
```
学生编辑文档
  ↓
点击提交
  ↓
OnlyOffice保存文档
  ↓
回调 handlerForcesave()
  ↓
版本号: 1 → 2
  ↓
新key = hash("task_1_user_100_v2")
  ↓
更新数据库
```

### 再次打开
```
学生再次打开任务
  ↓
createCopy() 查询已有副本
  ↓
返回最新的 documentKey (v2)
  ↓
OnlyOffice用新key打开
  ↓
✅ 版本匹配，正常打开
```

## 关键点说明

### 1. documentKey的组成
```
原始值: "task_{taskId}_user_{userId}_v{version}"
示例: "task_1_user_100_v3"
最终Key: SHA256("task_1_user_100_v3")
```

### 2. 版本号何时增加？
- ✅ 文档保存成功时（回调status=2）
- ❌ 打开文档时
- ❌ 编辑过程中

### 3. 为什么要用版本号？
- OnlyOffice根据key判断文档版本
- 相同key = 相同版本 → 如果内容变了会报错
- 不同key = 不同版本 → 强制OnlyOffice重新加载

### 4. 版本号存储位置
- 数据库表：`exp_task_submit.document_version`
- 数据类型：INT
- 默认值：1
- 每次保存后自动递增

## 验证方法

### 1. 检查数据库
```sql
SELECT task_id, user_id, document_key, document_version, submit_time
FROM exp_task_submit
WHERE task_id = 1 AND user_id = 100;
```

预期结果：
- 首次创建：`document_version = 1`
- 保存1次后：`document_version = 2`
- 保存2次后：`document_version = 3`

### 2. 查看日志
创建副本时：
```
生成documentKey: abc123..., version: 1
```

保存成功时：
```
任务提交成功，版本号: 1 -> 2, newKey: def456...
```

再次打开时：
```
学生已有副本文件, documentKey: def456..., version: 2
```

### 3. 测试流程
1. 学生打开任务 → 检查version=1
2. 编辑并保存 → 检查version=2，key已更新
3. 关闭编辑器
4. 再次打开 → ✅ 应正常打开，无版本冲突提示

## 常见问题

### Q1: 如果回调失败，版本号会错乱吗？
**A:** 不会。版本号只在回调成功时更新，如果保存失败，版本号保持不变。

### Q2: 多个学生同时编辑会冲突吗？
**A:** 不会。每个学生有独立的副本和版本号（基于userId）。

### Q3: 教师批改时会更新版本号吗？
**A:** 不会。版本号只在学生提交时更新，教师查看不会触发版本变化。

### Q4: 如果版本号达到最大值怎么办？
**A:** INT类型最大值为2,147,483,647，正常使用不会达到。

### Q5: 能否回退到旧版本？
**A:** 当前方案不支持版本回退，只保留最新版本。如需版本历史，需要额外实现。

## 总结

这个方案通过在documentKey中包含版本号，完美解决了OnlyOffice的版本冲突问题：
- ✅ 每次保存后自动生成新key
- ✅ 再次打开时使用最新key
- ✅ OnlyOffice不会报版本冲突
- ✅ 对用户完全透明，无需手动操作

**核心原理**：让OnlyOffice每次都认为打开的是"新版本"文档，从而绕过版本检查。
