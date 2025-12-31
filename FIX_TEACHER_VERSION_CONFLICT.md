# 修复：教师批改后再次打开报"文件版本过期"的问题

## 问题描述

教师在线批改文档后退出，再次打开同一份报告时，OnlyOffice提示"文档版本已更改"或"文件版本过期"，无法正常打开编辑器。

## 问题原因

### 旧的回调逻辑（有Bug）

```java
// ❌ 旧代码：只在学生提交时才更新版本号
if (submit != null && submit.getSubmitPending() == 1) {
    // 版本号+1
    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);
}
```

### 问题分析

1. **教师批改流程**：
   ```
   教师打开批改页面
     ↓
   修改文档（添加批注、评分等）
     ↓
   OnlyOffice自动保存
     ↓
   触发回调 handlerForcesave()
     ↓
   文件保存到MinIO ✅
     ↓
   检查 submitPending == 1？❌ (教师批改时为0)
     ↓
   ⚠️ 版本号没有更新！
   ```

2. **再次打开时的问题**：
   ```
   教师再次打开批改页面
     ↓
   从数据库读取 documentKey (还是旧的v1)
     ↓
   OnlyOffice检查版本
     ↓
   文件已变化 vs 使用旧key
     ↓
   ❌ 版本冲突！报错！
   ```

### 核心问题

**`submitPending`标志只在学生主动提交时设为1**，教师批改时始终为0，导致：
- ✅ 文件保存成功
- ❌ 版本号没更新
- ❌ documentKey还是旧的
- ❌ 下次打开冲突

## 解决方案

### 修改策略

**无论是学生提交还是教师批改，只要OnlyOffice保存了文件，就应该更新版本号**。

### 新的回调逻辑

```java
// ✅ 新代码：无论什么情况都更新版本号
if (submit != null) {
    // 1. 先更新版本号（对所有保存操作都生效）
    Integer currentVersion = submit.getDocumentVersion() != null ? submit.getDocumentVersion() : 1;
    Integer newVersion = currentVersion + 1;
    String newDocumentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + newVersion);

    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);

    // 2. 检查是否是学生提交
    boolean isStudentSubmit = submit.getSubmitPending() == 1;

    if (isStudentSubmit) {
        // 学生提交：额外更新提交时间和状态
        submit.setSubmitTime(new Date());
        submit.setSubmitPending(0);
        // 触发状态机转换
    } else {
        // 教师批改：只更新版本号
        log.info("文档已保存（教师批改），已更新版本号...");
    }

    // 3. 更新数据库
    expTaskSubmitService.updateExpTaskSubmit(submit);
}
```

## 修复内容

### 文件位置
`ruoyi-system/src/main/java/com/ruoyi/system/service/impl/CallbackServiceImpl.java`

### 修改范围
行176-255 (`handlerForcesave`方法)

### 关键改动

#### 1. 提前更新版本号
```java
// 查询提交记录
if (submit != null) {
    // ✅ 无论什么情况，文件保存了就更新版本号
    Integer currentVersion = submit.getDocumentVersion() != null ? submit.getDocumentVersion() : 1;
    Integer newVersion = currentVersion + 1;
    String newDocumentKey = DigestUtils.sha256Hex("task_" + taskId + "_user_" + userId + "_v" + newVersion);

    submit.setDocumentVersion(newVersion);
    submit.setDocumentKey(newDocumentKey);
```

#### 2. 区分学生提交和教师批改
```java
    // 检查是否是学生提交（submitPending=1）
    boolean isStudentSubmit = submit.getSubmitPending() != null && submit.getSubmitPending() == 1;

    if (isStudentSubmit) {
        // 学生提交：更新提交时间并清除提交中标记
        submit.setSubmitTime(new Date());
        submit.setSubmitPending(0);

        log.info("学生提交成功，已更新submit_time和版本号...");

        // 触发状态机转换（草稿→已提交 或 已打回→重新提交）
    } else {
        // 教师批改或其他情况：只更新版本号，不更新提交时间
        log.info("文档已保存（教师批改或编辑），已更新版本号...");
    }
```

#### 3. 统一更新数据库
```java
    // 更新数据库（无论学生提交还是教师批改）
    expTaskSubmitService.updateExpTaskSubmit(submit);
}
```

## 工作流程对比

### 修复前（有Bug）

#### 学生提交
```
学生编辑 → 提交 → 回调
  ↓
submitPending = 1 ✅
  ↓
版本号: 1 → 2 ✅
  ↓
再次打开: 用v2 ✅ 正常
```

#### 教师批改
```
教师批改 → 保存 → 回调
  ↓
submitPending = 0 ❌
  ↓
版本号: 1 → 1 ❌ 没变
  ↓
再次打开: 用v1，但文件已变 ❌ 冲突！
```

### 修复后（正常）

#### 学生提交
```
学生编辑 → 提交 → 回调
  ↓
版本号: 1 → 2 ✅
submitTime更新 ✅
状态转换 ✅
  ↓
再次打开: 用v2 ✅ 正常
```

#### 教师批改
```
教师批改 → 保存 → 回调
  ↓
版本号: 2 → 3 ✅
submitTime不变 ✅
状态不变 ✅
  ↓
再次打开: 用v3 ✅ 正常
```

## 测试步骤

### 1. 测试教师批改流程

**步骤1**: 学生提交报告
```
操作: 学生完成并提交报告
检查: submit_id=1, document_version=2, status='1'
```

**步骤2**: 教师第一次批改
```
操作: 教师打开批改页面，添加批注
检查:
  - 日志: "文档已保存（教师批改），版本号: 2 -> 3"
  - 数据库: document_version=3, status='1'（状态不变）
```

**步骤3**: 教师退出后再次打开
```
操作: 关闭浏览器，重新打开同一报告
预期: ✅ 正常打开，无版本冲突提示
检查: 使用的documentKey对应version=3
```

**步骤4**: 教师再次修改
```
操作: 添加更多批注，保存
检查: document_version=4
```

**步骤5**: 再次打开验证
```
操作: 关闭后重新打开
预期: ✅ 正常打开
检查: 使用documentKey对应version=4
```

### 2. 测试学生提交流程（确保不受影响）

**步骤1**: 学生提交
```
操作: 学生编辑并提交
检查:
  - document_version: 1 → 2
  - submit_time: 已更新
  - status: '0' → '1'
```

**步骤2**: 教师打回
```
操作: 教师批改后打回
检查: status: '1' → '4'
```

**步骤3**: 学生重新提交
```
操作: 学生修改后重新提交
检查:
  - document_version: 2 → 3
  - status: '4' → '5'
```

### 3. 查看日志

**学生提交时**：
```log
学生提交成功，已更新submit_time和版本号, taskId: 1, userId: 100, version: 1 -> 2
状态机触发成功：草稿 -> 已提交, submitId: 1
```

**教师批改时**：
```log
文档已保存（教师批改或编辑），已更新版本号, taskId: 1, userId: 100, version: 2 -> 3
```

### 4. 数据库验证

```sql
-- 查看版本号变化
SELECT
    submit_id,
    task_id,
    user_id,
    document_version,
    submit_time,
    status,
    update_time
FROM exp_task_submit
WHERE task_id = 1 AND user_id = 100
ORDER BY update_time DESC;
```

预期结果：
- 每次保存后 `document_version` 都会递增
- 学生提交时 `submit_time` 会更新
- 教师批改时 `submit_time` 保持不变
- 状态转换仅在学生提交时发生

## 可能遇到的问题

### Q1: 修复后教师批改，版本号还是没变？
**A**: 检查以下几点：
1. 是否重启了后端服务
2. 查看日志是否有异常
3. 确认OnlyOffice回调是否成功（status=2或6）

### Q2: 版本号递增但还是报冲突？
**A**: 可能原因：
1. 浏览器缓存了旧的documentKey，清除缓存重试
2. 前端传递的documentKey不是最新的，检查获取逻辑
3. OnlyOffice Redis缓存问题，重启OnlyOffice服务

### Q3: 学生提交后状态没变？
**A**: 检查：
1. `submitPending` 是否正确设为1
2. 状态机配置是否正确
3. 查看详细错误日志

## 相关代码位置

- `CallbackServiceImpl.java:176-255` - 回调处理（本次修复）
- `ExpTaskController.java:533-670` - 学生提交任务
- `ExpTaskSubmit.java:74` - documentVersion字段定义
- `ExpTaskSubmitMapper.xml:14,29,62,100` - 版本号映射

## 总结

### 修复前的问题
- ❌ 教师批改后版本号不更新
- ❌ 再次打开报版本冲突
- ❌ 无法正常使用

### 修复后的效果
- ✅ 任何保存操作都更新版本号
- ✅ 区分学生提交和教师批改
- ✅ 学生提交：版本号+1 + 提交时间 + 状态转换
- ✅ 教师批改：版本号+1（不影响提交时间和状态）
- ✅ 再次打开无冲突

### 核心原理
**文件保存 = 版本号递增 = 新的documentKey = OnlyOffice认为是新版本 = 无冲突**

现在教师可以放心批改，多次打开编辑都不会报版本冲突错误了！🎉
