# 修复：学生提交后状态仍为草稿 & 教师重复保存报错

## 问题1：学生提交后状态还是草稿

### 问题描述
学生在线完成报告并提交后，数据库中`submit_time`已更新，但`status`仍然是'0'（草稿），没有变成'1'（已提交）。

### 问题原因

**数据库更新顺序错误导致状态机更新被覆盖**

**旧代码流程**（CallbackServiceImpl.java:197-244）：
```java
// 1. 修改内存中的submit对象
submit.setDocumentVersion(newVersion);
submit.setDocumentKey(newDocumentKey);
submit.setSubmitTime(new Date());
submit.setSubmitPending(0);

// 2. 触发状态机（状态机内部更新数据库，status: 0 → 1）
reportStateMachineService.submitReport(submit.getSubmitId());

// 3. ❌ 问题：用内存中的旧submit对象更新数据库
expTaskSubmitService.updateExpTaskSubmit(submit);
// submit.status 还是 0，覆盖了状态机的更新！
```

**执行结果**：
```
数据库初始状态: status=0
  ↓
状态机更新: status=1 ✅
  ↓
updateExpTaskSubmit(submit): status=0 ❌ 覆盖！
  ↓
最终状态: status=0（草稿）
```

### 解决方案

**先更新数据库，再触发状态机**

**新代码流程**：
```java
if (isStudentSubmit) {
    // 1. 先更新版本号、提交时间等字段
    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);
    submit.setSubmitTime(new Date());
    submit.setSubmitPending(0);

    // 2. ✅ 先更新数据库
    expTaskSubmitService.updateExpTaskSubmit(submit);

    // 3. ✅ 然后触发状态机（状态机会单独更新status字段）
    String currentStatus = submit.getStatus();
    if ("0".equals(currentStatus)) {
        reportStateMachineService.submitReport(submit.getSubmitId());
    }
}
```

**执行结果**：
```
数据库初始状态: status=0, submit_time=null
  ↓
updateExpTaskSubmit: submit_time=now, version=2, submitPending=0 ✅
  ↓
状态机更新: status=1 ✅
  ↓
最终状态: status=1（已提交），submit_time已更新
```

## 问题2：教师重复保存报"不允许的状态转换"

### 问题描述
教师首次批改保存成功，修改分数后再次保存时报错：
```
当前状态: REVIEWED, 触发器: APPROVE
不允许的状态转换: REVIEWED -> APPROVE
```

### 问题原因

**旧代码无论当前状态如何，每次保存都触发approve**

**旧代码逻辑**（ExpTaskController.java:859-878）：
```java
// 如果是"已提交"(1)，先开始批阅
if ("1".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
}

// 如果是"重新提交"(5)，也先开始批阅
if ("5".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
}

// ❌ 问题：无论什么状态，都执行approve
reportStateMachineService.approve(submitId);
```

**问题场景**：
```
第1次保存:
  状态: REVIEWING(2) → approve() → REVIEWED(3) ✅

第2次保存:
  状态: REVIEWED(3) → approve() → ❌ 不允许的转换！
```

### 解决方案

**根据当前状态，只在需要时触发状态机**

**新代码逻辑**：
```java
// 已提交(1) → 批阅中(2) → 已批阅(3)
if ("1".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
    reportStateMachineService.approve(submitId);
}
// 重新提交(5) → 批阅中(2) → 已批阅(3)
else if ("5".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
    reportStateMachineService.approve(submitId);
}
// 批阅中(2) → 已批阅(3)
else if ("2".equals(currentStatus)) {
    reportStateMachineService.approve(submitId);
}
// ✅ 已批阅(3)：不触发状态机，只更新分数和评语
else if ("3".equals(currentStatus)) {
    logger.info("已是已批阅状态，只更新分数和评语");
}
```

**执行结果**：
```
第1次保存:
  状态: SUBMITTED(1)
    → startReview() → REVIEWING(2)
    → approve() → REVIEWED(3) ✅

第2次保存:
  状态: REVIEWED(3)
    → 不触发状态机，只更新分数和评语 ✅
```

## 修复内容汇总

### 1. CallbackServiceImpl.java (行197-252)

**修改要点**：
- 学生提交时：先更新数据库，再触发状态机
- 教师批改时：只更新版本号，不触发状态机

```java
if (isStudentSubmit) {
    // 先更新数据库（版本号、提交时间、submitPending）
    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);
    submit.setSubmitTime(new Date());
    submit.setSubmitPending(0);
    expTaskSubmitService.updateExpTaskSubmit(submit);

    // 然后触发状态机（单独更新status）
    if ("0".equals(currentStatus)) {
        reportStateMachineService.submitReport(submitId);
    } else if ("4".equals(currentStatus)) {
        reportStateMachineService.resubmit(submitId);
    }
} else {
    // 教师批改：只更新版本号
    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);
    expTaskSubmitService.updateExpTaskSubmit(submit);
}
```

### 2. ExpTaskController.java (行827-900)

**修改要点**：
- 根据当前状态判断是否需要状态转换
- 已批阅状态不再触发状态机
- 状态机失败不影响分数和评语的保存

```java
// 已提交(1) 或 重新提交(5)：需要完整转换
if ("1".equals(currentStatus) || "5".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
    reportStateMachineService.approve(submitId);
}
// 批阅中(2)：只需批阅通过
else if ("2".equals(currentStatus)) {
    reportStateMachineService.approve(submitId);
}
// 已批阅(3)：不触发状态机
else if ("3".equals(currentStatus)) {
    logger.info("已是已批阅状态，只更新分数和评语");
}
```

## 状态转换完整流程

### 学生提交流程
```
草稿(0)
  ↓ [学生编辑完成，点击提交]
  ↓ OnlyOffice保存 → 回调
  ↓ updateExpTaskSubmit(版本号、提交时间)
  ↓ submitReport() ← 状态机
已提交(1) ✅
```

### 教师批改流程
```
已提交(1)
  ↓ [教师第1次保存]
  ↓ startReview() → 批阅中(2)
  ↓ approve() → 已批阅(3)
  ↓
  ↓ [教师第2次保存（修改分数）]
  ↓ 只更新分数和评语，不触发状态机
已批阅(3) ✅ 状态不变
```

### 打回重提流程
```
批阅中(2)
  ↓ [教师打回]
  ↓ reject()
已打回(4)
  ↓ [学生修改后重新提交]
  ↓ resubmit()
重新提交(5)
  ↓ [教师再次批阅]
  ↓ startReview() → 批阅中(2)
  ↓ approve() → 已批阅(3)
已批阅(3) ✅
```

## 测试步骤

### 测试1：学生提交

**步骤1**：学生打开任务编辑
```sql
SELECT status, submit_time, document_version
FROM exp_task_submit WHERE submit_id = 1;
-- 预期: status='0', submit_time=NULL, version=1
```

**步骤2**：学生编辑并提交
```
操作: 修改文档，点击"提交"
日志: "学生提交成功，已更新submit_time和版本号, version: 1 -> 2"
日志: "状态机触发成功：草稿 -> 已提交, submitId: 1"
```

**步骤3**：检查数据库
```sql
SELECT status, submit_time, document_version
FROM exp_task_submit WHERE submit_id = 1;
-- 预期: status='1', submit_time已更新, version=2 ✅
```

### 测试2：教师批改

**步骤1**：教师首次批改
```
当前状态: status='1'（已提交）
操作: 打开批改，填写分数80，评语"良好"，保存
日志: "状态机触发：已提交 -> 批阅中"
日志: "状态机触发：批阅中 -> 已批阅"
```

**步骤2**：检查状态
```sql
SELECT status, score, teacher_remark
FROM exp_task_submit WHERE submit_id = 1;
-- 预期: status='3', score=80, teacher_remark='良好' ✅
```

**步骤3**：教师修改分数
```
当前状态: status='3'（已批阅）
操作: 修改分数为85，保存
日志: "已是已批阅状态，只更新分数和评语，不触发状态机"
```

**步骤4**：检查状态
```sql
SELECT status, score
FROM exp_task_submit WHERE submit_id = 1;
-- 预期: status='3'（状态不变）, score=85（分数已更新）✅
```

### 测试3：打回重提

**步骤1**：教师打回
```
操作: 点击"打回"，填写原因
预期: status='4'（已打回）
```

**步骤2**：学生重新提交
```
操作: 修改文档，重新提交
日志: "状态机触发成功：已打回 -> 重新提交"
预期: status='5'（重新提交）✅
```

**步骤3**：教师再次批改
```
操作: 批改并保存
日志: "状态机触发：重新提交 -> 批阅中"
日志: "状态机触发：批阅中 -> 已批阅"
预期: status='3'（已批阅）✅
```

## 可能遇到的问题

### Q1: 学生提交后状态还是草稿？
**A**: 检查：
1. 回调是否成功（查看日志）
2. 状态机是否触发失败（查看错误日志）
3. 数据库更新是否被回滚（检查事务）

### Q2: 教师保存还是报错？
**A**: 检查：
1. 当前状态是否正确
2. 是否有多个请求并发执行
3. 清除浏览器缓存重试

### Q3: 版本号没有递增？
**A**: 检查：
1. OnlyOffice回调是否成功
2. 查看回调日志
3. 检查MinIO文件是否保存成功

## 相关文件

- `CallbackServiceImpl.java:197-252` - 回调处理（问题1修复）
- `ExpTaskController.java:827-900` - 批改保存（问题2修复）
- `ReportStateMachineService.java` - 状态机服务
- `ExpTaskSubmit.java` - 提交记录实体

## 总结

### 修复前的问题
- ❌ 学生提交后status还是0（草稿）
- ❌ 教师第2次保存报"不允许的状态转换"错误
- ❌ 数据库更新顺序混乱

### 修复后的效果
- ✅ 学生提交后status正确变为1（已提交）
- ✅ 教师可以多次修改分数和评语
- ✅ 状态机只在需要时触发
- ✅ 数据库更新顺序正确
- ✅ 日志记录完整

### 核心原则
1. **先更新数据库，再触发状态机**（避免覆盖）
2. **根据当前状态判断操作**（避免重复转换）
3. **状态机失败不影响业务**（降级处理）
