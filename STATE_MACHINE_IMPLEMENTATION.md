# 实验报告状态机实现说明

## 概述

本项目已完成基于 stateless4j 的实验报告状态机实现，替代了之前直接修改 MySQL 字段的方式，提供了更规范、更可控的状态流转管理。

## 状态机设计

### 状态定义 (ReportState)

| 状态代码 | 状态名称 | 说明 |
|---------|---------|------|
| 0 | 草稿 | 学生创建副本但未提交 |
| 1 | 已提交 | 学生提交报告 |
| 2 | 批阅中 | 教师开始批阅 |
| 3 | 已批阅 | 教师批阅完成 |
| 4 | 已打回 | 教师打回报告 |
| 5 | 重新提交 | 学生重新提交 |
| 6 | 已归档 | 报告归档 |

### 触发器定义 (ReportTrigger)

| 触发器 | 说明 | 状态转换 |
|-------|------|---------|
| SUBMIT | 提交报告 | 草稿(0) → 已提交(1) |
| START_REVIEW | 开始批阅 | 已提交(1)/重新提交(5) → 批阅中(2) |
| APPROVE | 批阅通过 | 批阅中(2) → 已批阅(3) |
| REJECT | 打回报告 | 批阅中(2) → 已打回(4) |
| RESUBMIT | 重新提交 | 已打回(4) → 重新提交(5) |
| ARCHIVE | 归档 | 已批阅(3) → 已归档(6) |

### 状态流转图

```
草稿(0) --SUBMIT--> 已提交(1) --START_REVIEW--> 批阅中(2) --APPROVE--> 已批阅(3) --ARCHIVE--> 已归档(6)
                                                      |
                                                   REJECT
                                                      ↓
                    已打回(4) <---------------------
                        |
                    RESUBMIT
                        ↓
                    重新提交(5) --START_REVIEW--> 批阅中(2)
```

## 后端实现

### 1. 核心文件

#### 状态机配置
- **ReportState.java**: 状态枚举类
  - 位置: `ruoyi-system/src/main/java/com/ruoyi/system/domain/enums/ReportState.java`
  - 提供 `fromCode()` 方法将数据库字符码转换为枚举

- **ReportTrigger.java**: 触发器枚举类
  - 位置: `ruoyi-system/src/main/java/com/ruoyi/system/domain/enums/ReportTrigger.java`

- **ReportStateMachineConfig.java**: 状态机配置类
  - 位置: `ruoyi-system/src/main/java/com/ruoyi/system/service/statemachine/ReportStateMachineConfig.java`
  - 配置所有状态转换规则
  - 提供 `canFire()` 和 `getPermittedTriggers()` 方法

- **ReportStateMachineService.java**: 状态机服务类
  - 位置: `ruoyi-system/src/main/java/com/ruoyi/system/service/ReportStateMachineService.java`
  - 提供业务方法：submitReport(), startReview(), approve(), reject(), resubmit(), archive()

### 2. 集成点

#### 学生提交任务 (ExpTaskController.java:536)
```java
// 文档无修改时（errorCode=4）
if ("0".equals(submit.getStatus())) {
    reportStateMachineService.submitReport(submit.getSubmitId());
}
```

#### OnlyOffice回调 (CallbackServiceImpl.java:196)
```java
// 文档保存完成后
if ("0".equals(submit.getStatus())) {
    reportStateMachineService.submitReport(submit.getSubmitId());
}
```

#### 教师批改保存 (ExpTaskController.java:814)
```java
// 根据当前状态自动触发状态转换
if ("1".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
}
if ("5".equals(currentStatus)) {
    reportStateMachineService.startReview(submitId);
}
reportStateMachineService.approve(submitId);
```

### 3. API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/task/submit/fire/{submitId}/{trigger}` | POST | 触发状态转换 |
| `/task/submit/reject/{submitId}?reason=xxx` | POST | 打回报告 |
| `/task/submit/actions/{submitId}` | GET | 获取允许的操作列表 |
| `/task/submit/canFire/{submitId}/{trigger}` | GET | 检查是否允许触发 |

## 前端实现

### 1. 状态机API封装 (stateMachine.js)

位置: `ruoyi-ui/src/api/task/stateMachine.js`

提供方法:
- `fireStateMachine(submitId, trigger)`: 触发状态转换
- `canFire(submitId, trigger)`: 检查是否允许触发
- `getPermittedActions(submitId)`: 获取允许的操作列表
- `submitReport(submitId)`: 提交报告
- `startReview(submitId)`: 开始批阅
- `approveReport(submitId)`: 批阅通过
- `rejectReport(submitId, reason)`: 打回报告
- `resubmitReport(submitId)`: 重新提交
- `archiveReport(submitId)`: 归档

### 2. 批改页面集成 (review-detail.vue)

**新增功能**:
1. 显示报告状态标签（不同颜色）
2. 显示打回原因（如果有）
3. 添加"打回"按钮
4. 打回对话框（输入打回原因）

**状态显示**:
```vue
<el-tag :type="getStatusType(submitInfo.status)" size="mini">
  {{ getStatusText(submitInfo.status) }}
</el-tag>
```

**打回功能**:
- 打回按钮: 黄色警告按钮，图标为 `el-icon-refresh-left`
- 打回对话框: 要求输入至少5个字符的打回原因
- 打回成功后自动跳转到下一个报告

### 3. 按钮布局优化

4个按钮采用2x2网格布局:
```css
.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-btn {
  flex: 1 1 calc(50% - 4px);
  min-width: 80px;
}
```

## 数据库变更

### 需要执行的SQL

位置: `sql/add_report_state_machine.sql`

```sql
-- 添加打回原因字段
ALTER TABLE exp_task_submit
ADD COLUMN reject_reason VARCHAR(500) DEFAULT NULL COMMENT '打回原因' AFTER teacher_remark;

-- 更新状态字段注释
ALTER TABLE exp_task_submit
MODIFY COLUMN status CHAR(1) DEFAULT '0' COMMENT '状态（0草稿 1已提交 2批阅中 3已批阅 4已打回 5重新提交 6已归档）';
```

## Maven依赖

已添加 stateless4j 依赖到两个模块:
- `ruoyi-system/pom.xml`
- `ruoyi-admin/pom.xml`

```xml
<dependency>
    <groupId>com.github.oxo42</groupId>
    <artifactId>stateless4j</artifactId>
    <version>2.5.0</version>
</dependency>
```

## 使用说明

### 1. 启动应用前

执行数据库迁移脚本:
```bash
mysql -u root -p your_database < sql/add_report_state_machine.sql
```

### 2. 编译项目

```bash
mvn clean compile
```

### 3. 状态流程示例

**学生提交流程**:
1. 学生打开任务 → 状态: 草稿(0)
2. 学生提交报告 → 触发 SUBMIT → 状态: 已提交(1)

**教师批改流程**:
1. 教师打开批改页面 → 自动触发 START_REVIEW → 状态: 批阅中(2)
2. 教师保存评分和评语 → 触发 APPROVE → 状态: 已批阅(3)

**打回重提流程**:
1. 教师点击"打回"按钮 → 输入原因 → 触发 REJECT → 状态: 已打回(4)
2. 学生修改后重新提交 → 触发 RESUBMIT → 状态: 重新提交(5)
3. 教师再次批改 → 触发 START_REVIEW → 状态: 批阅中(2)
4. 教师保存 → 触发 APPROVE → 状态: 已批阅(3)

## 技术特点

### 1. 状态转换安全性
- 使用 stateless4j 确保只能进行允许的状态转换
- 不允许的转换会抛出异常
- 提供 `canFire()` 方法预先检查

### 2. 向后兼容
- 数据库仍使用字符码 ('0', '1', '2'...)
- ReportState 提供 `fromCode()` 和 `getCode()` 转换
- 旧数据无需迁移

### 3. 异常处理
- 状态机触发失败不影响主业务流程
- 记录详细日志便于排查问题

### 4. 扩展性
- 新增状态只需修改枚举和配置类
- 前端通过API自动获取允许的操作

## 测试建议

1. **提交流程测试**:
   - 测试文档无修改的提交
   - 测试文档有修改的提交

2. **批改流程测试**:
   - 测试首次批改
   - 测试打回后重新批改

3. **状态转换测试**:
   - 尝试非法的状态转换（应该失败）
   - 检查数据库状态字段是否正确更新

4. **前端显示测试**:
   - 验证状态标签颜色
   - 验证打回原因显示
   - 验证按钮布局

## 注意事项

1. ⚠️ **必须先执行数据库脚本**，否则 `reject_reason` 字段不存在会导致错误
2. ⚠️ **状态机服务使用 @Transactional**，确保数据库事务支持
3. ⚠️ **前端需要正确导入 stateMachine.js API文件**
4. ⚠️ **ClassNotFoundException 已修复**，确保两个模块都有 stateless4j 依赖

## 文件清单

### 后端新增文件
- `ruoyi-system/src/main/java/com/ruoyi/system/domain/enums/ReportState.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/domain/enums/ReportTrigger.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/statemachine/ReportStateMachineConfig.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/ReportStateMachineService.java`

### 后端修改文件
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/CallbackServiceImpl.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/domain/ExpTaskSubmit.java`
- `ruoyi-system/src/main/resources/mapper/system/ExpTaskSubmitMapper.xml`
- `ruoyi-system/pom.xml`
- `ruoyi-admin/pom.xml`

### 前端新增文件
- `ruoyi-ui/src/api/task/stateMachine.js`

### 前端修改文件
- `ruoyi-ui/src/views/task/review-detail.vue`

### SQL文件
- `sql/add_report_state_machine.sql`

## 编译状态

✅ **BUILD SUCCESS** - 所有模块编译成功

```
[INFO] ruoyi .............................................. SUCCESS
[INFO] ruoyi-common ....................................... SUCCESS
[INFO] ruoyi-system ....................................... SUCCESS
[INFO] ruoyi-framework .................................... SUCCESS
[INFO] ruoyi-quartz ....................................... SUCCESS
[INFO] ruoyi-generator .................................... SUCCESS
[INFO] ruoyi-admin ........................................ SUCCESS
[INFO] BUILD SUCCESS
```

## 下一步

1. 执行数据库迁移脚本
2. 启动应用进行功能测试
3. 验证状态转换是否符合预期
4. 测试打回功能是否正常工作
