# 修复：学生重新提交后状态仍为"已打回"的问题

## 问题描述

当教师将报告打回后，学生修改并重新提交，状态仍然显示为"已打回"，没有正确转换为"重新提交"状态。

## 问题原因

在学生提交任务的代码中，**只处理了草稿状态(0)的提交，没有处理已打回状态(4)的重新提交**。

### 问题代码位置

#### 1. ExpTaskController.submitTask() (行617-630)
```java
// ❌ 旧代码：只处理草稿状态
if ("0".equals(submit.getStatus()))
{
    reportStateMachineService.submitReport(submit.getSubmitId());
}
```

#### 2. CallbackServiceImpl.handlerForcesave() (行217-225)
```java
// ❌ 旧代码：只处理草稿状态
if ("0".equals(submit.getStatus())) {
    reportStateMachineService.submitReport(submit.getSubmitId());
}
```

## 解决方案

根据当前状态触发相应的状态机转换：
- 草稿(0) → 使用 `submitReport()` → 转换为已提交(1)
- 已打回(4) → 使用 `resubmit()` → 转换为重新提交(5)

## 修复内容

### 1. 修复 ExpTaskController.submitTask()

**文件**: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java`

**修改位置**: 行617-642

```java
// ✅ 新代码：根据状态触发不同的转换
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
}
```

### 2. 修复 CallbackServiceImpl.handlerForcesave()

**文件**: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/CallbackServiceImpl.java`

**修改位置**: 行216-233

```java
// ✅ 新代码：根据状态触发不同的转换
// 根据当前状态触发相应的状态机转换
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
}
```

## 状态机完整流程

### 正常提交流程
```
草稿(0)
  ↓ [学生提交]
  ↓ submitReport()
已提交(1)
  ↓ [教师开始批阅]
  ↓ startReview()
批阅中(2)
  ↓ [教师批阅通过]
  ↓ approve()
已批阅(3)
```

### 打回重提流程（本次修复）
```
批阅中(2)
  ↓ [教师打回]
  ↓ reject()
已打回(4)
  ↓ [学生修改后重新提交]
  ↓ resubmit() ✅ 修复点
重新提交(5)
  ↓ [教师再次批阅]
  ↓ startReview()
批阅中(2)
  ↓ [教师批阅通过]
  ↓ approve()
已批阅(3)
```

## 测试步骤

### 1. 准备测试数据
```sql
-- 创建一个测试任务和提交记录
INSERT INTO exp_task_submit (task_id, user_id, user_name, file_url, document_key, status, document_version)
VALUES (1, 100, '测试学生', 'http://test.com/file.docx', 'key123', '4', 1);
```

### 2. 测试打回重提流程

**步骤1**: 教师打回报告
```
当前状态: 批阅中(2)
操作: 教师点击"打回"
预期结果: 状态 → 已打回(4)
```

**步骤2**: 学生查看打回原因
```
当前状态: 已打回(4)
预期显示: 打回原因文本
```

**步骤3**: 学生修改并重新提交
```
当前状态: 已打回(4)
操作: 学生编辑文档并点击"提交"
预期结果: 状态 → 重新提交(5) ✅
```

**步骤4**: 教师重新批阅
```
当前状态: 重新提交(5)
操作: 教师点击"开始批改"
预期结果: 状态 → 批阅中(2)
```

**步骤5**: 教师批阅通过
```
当前状态: 批阅中(2)
操作: 教师填写分数和评语，点击"保存"
预期结果: 状态 → 已批阅(3)
```

### 3. 查看日志验证

重新提交时应该看到：
```
状态机触发成功：已打回 -> 重新提交, submitId: xxx
```

查询数据库验证：
```sql
SELECT submit_id, status, submit_time, reject_reason
FROM exp_task_submit
WHERE task_id = 1 AND user_id = 100;
```

预期：`status = '5'` (重新提交)

## 可能遇到的问题

### Q1: 修改后仍然是已打回状态？
**A**: 检查后端日志，确认是否有错误信息。可能原因：
1. 状态机配置错误
2. 权限不足
3. 数据库事务回滚

### Q2: 日志显示"不允许的状态转换"？
**A**: 检查当前状态是否正确，确保：
- 打回后状态确实为 "4"
- 状态机配置中有 REJECTED → RESUBMIT 的转换

### Q3: 提交成功但状态没变？
**A**: 可能是状态机触发失败，检查：
1. 是否捕获了异常但没有抛出
2. 数据库是否正确更新
3. 查看详细错误日志

## 相关文件

- `ExpTaskController.java:606-652` - 学生提交任务接口
- `CallbackServiceImpl.java:197-236` - OnlyOffice回调处理
- `ReportStateMachineService.java:154-156` - 重新提交方法
- `ReportState.java` - 状态枚举定义
- `ReportTrigger.java` - 触发器枚举定义

## 总结

此次修复确保了**打回重提流程**的状态转换正确性：
- ✅ 草稿状态提交 → 已提交
- ✅ 已打回状态提交 → 重新提交 (本次修复)
- ✅ 日志记录完整
- ✅ 异常处理健壮

现在学生重新提交后，状态会正确转换为"重新提交(5)"，教师可以看到学生已重新提交，并继续批阅流程。
