# 状态机转换失败和事务回滚问题修复

## 问题描述

### 问题1：状态转换失败 - REVIEWED 不能 APPROVE
```
当前状态: REVIEWED, 触发器: APPROVE
不允许的状态转换: REVIEWED -> APPROVE
```

**错误场景**：
- 教师对已批阅的报告（状态=3）进行二次批改（修改分数/评语）时
- 系统尝试触发 APPROVE 操作
- 但状态机配置不允许 REVIEWED 状态再次 APPROVE

### 问题2：缺少事务回滚机制
- `saveReview` 方法先更新分数和评语，再触发状态机
- 状态机失败时，已更新的数据**不会回滚**
- 导致数据不一致：分数/评语已更新，但状态转换失败

## 根本原因

### 1. 状态机配置不完整
原配置（`ReportStateMachineConfig.java`）：
```java
// 已批阅 -> 已归档
config.configure(ReportState.REVIEWED)
        .permit(ReportTrigger.ARCHIVE, ReportState.ARCHIVED);
```

**问题**：REVIEWED 状态只允许归档，不允许重新批改。

### 2. 缺少事务管理
原代码（`ExpTaskController.java:saveReview`）：
```java
@PostMapping("/submit/review")
public AjaxResult saveReview(@RequestBody ExpTaskSubmit expTaskSubmit) {
    // 先更新分数和评语
    int result = expTaskSubmitService.updateExpTaskSubmit(expTaskSubmit);

    try {
        // 再触发状态机
        reportStateMachineService.approve(...);
    } catch (Exception e) {
        // ❌ 异常被吞掉，已更新的数据不会回滚
        logger.warn("状态转换失败，但分数和评语已保存");
    }
}
```

## 修复方案

### 修复1：扩展状态机配置，允许 REVIEWED 状态重新批改

**文件**：`ruoyi-system/src/main/java/com/ruoyi/system/service/statemachine/ReportStateMachineConfig.java`

```java
// 已批阅 -> 批阅中（允许重新批改） 或 已归档
config.configure(ReportState.REVIEWED)
        .permit(ReportTrigger.START_REVIEW, ReportState.REVIEWING)  // ✅ 新增：允许重新开始批阅
        .permit(ReportTrigger.ARCHIVE, ReportState.ARCHIVED);
```

**支持的状态流转**：
1. **初次批改**：已提交(1) → 批阅中(2) → 已批阅(3)
2. **重新批改**：已批阅(3) → 批阅中(2) → 已批阅(3)
3. **归档**：已批阅(3) → 已归档(6)

### 修复2：添加事务注解，确保操作原子性

**文件**：`ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java`

#### 2.1 添加 @Transactional 注解
```java
@PostMapping("/submit/review")
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)  // ✅ 新增事务注解
public AjaxResult saveReview(@RequestBody ExpTaskSubmit expTaskSubmit) {
    // ...
}
```

#### 2.2 处理 REVIEWED 状态的重新批改
```java
if (result > 0) {
    // 如果已经是"已批阅"状态(3)，需要重新开始批阅流程
    if ("3".equals(currentStatus)) {
        reportStateMachineService.startReview(expTaskSubmit.getSubmitId());
        logger.info("状态机触发成功：已批阅 -> 批阅中（重新批改）");

        reportStateMachineService.approve(expTaskSubmit.getSubmitId());
        logger.info("状态机触发成功：批阅中 -> 已批阅");
    }
    // ... 其他状态处理
}
```

#### 2.3 异常处理：抛出异常触发回滚
```java
catch (Exception e) {
    logger.error("批改失败", e);
    // ✅ 抛出异常，触发事务回滚
    throw new RuntimeException("批改失败：" + e.getMessage(), e);
}
```

**原来的问题**：
```java
catch (Exception e) {
    // ❌ 返回错误消息，异常被吞掉，数据不会回滚
    return error("批改失败：" + e.getMessage());
}
```

## 修复效果

### 1. 支持教师重新批改已批阅的报告
- 教师可以修改已批阅报告的分数和评语
- 状态机流转：已批阅(3) → 批阅中(2) → 已批阅(3)

### 2. 保证数据一致性
- 分数/评语更新和状态转换在**同一事务**中
- 任何步骤失败，**所有操作都会回滚**
- 避免出现"分数已更新但状态未变"的不一致情况

### 3. 完整的状态流转支持

| 当前状态 | 操作 | 状态转换 |
|---------|------|---------|
| 已提交(1) | 批改 | 1 → 2 → 3 |
| 重新提交(5) | 批改 | 5 → 2 → 3 |
| 批阅中(2) | 批改通过 | 2 → 3 |
| 已批阅(3) | **重新批改** | 3 → 2 → 3 ✅ |
| 已批阅(3) | 归档 | 3 → 6 |

## 测试建议

### 测试场景1：正常重新批改
1. 教师批改一份报告（状态变为"已批阅"）
2. 教师再次修改分数/评语并保存
3. **预期**：操作成功，状态仍为"已批阅"

### 测试场景2：状态转换失败时的回滚
1. 模拟状态机异常（如修改数据库状态为非法值）
2. 教师批改报告
3. **预期**：操作失败，分数/评语**不会**被保存（事务回滚）

### 测试场景3：学生重新提交后的批改
1. 学生提交报告
2. 教师打回
3. 学生重新提交（状态应为"重新提交"5）
4. 教师批改
5. **预期**：状态正确流转 5 → 2 → 3

## 注意事项

### 1. 前端异常处理
由于后端现在抛出 RuntimeException，前端需要正确处理：
```javascript
// 前端需要捕获 500 错误
.catch(error => {
    this.$message.error('批改失败：' + (error.message || '未知错误'));
});
```

### 2. 数据库事务支持
确保数据库配置支持事务：
- MySQL：使用 InnoDB 引擎（默认）
- 事务隔离级别：READ_COMMITTED 或 REPEATABLE_READ

### 3. 日志监控
关注以下日志，排查问题：
```
状态机触发成功：已批阅 -> 批阅中（重新批改）
状态机触发成功：批阅中 -> 已批阅
```

## 相关文件

- `ruoyi-system/src/main/java/com/ruoyi/system/service/statemachine/ReportStateMachineConfig.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/ReportStateMachineService.java`

## 总结

通过以下两个关键修复：
1. **扩展状态机**：允许 REVIEWED → START_REVIEW 转换
2. **添加事务管理**：确保分数更新和状态转换的原子性

解决了教师重新批改已批阅报告时的状态转换失败问题，并确保了数据的一致性。
