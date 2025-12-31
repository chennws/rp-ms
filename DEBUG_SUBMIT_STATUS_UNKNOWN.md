# 调试：报告状态显示"未知"的问题

## 问题描述

学生端任务列表中，报告状态列显示"未知"而不是正确的状态（草稿、已提交等）。

## 调试步骤

### 1. 打开浏览器开发者工具

1. 使用学生账号登录系统
2. 进入"实验任务管理"页面
3. 按 `F12` 打开浏览器开发者工具
4. 切换到 **Console（控制台）** 标签页

### 2. 查看调试日志

刷新页面后，在控制台中查找以下日志：

```javascript
// 1. 查看任务列表数据
学生端任务列表数据: Array(3) [...]

// 2. 查看第一个任务的报告状态
第一个任务的报告状态: "1"  // 或 null 或 undefined

// 3. 如果显示"未知"，会有警告
未知的报告状态: "某个值" 类型: "string"
```

### 3. 检查网络请求

1. 切换到 **Network（网络）** 标签页
2. 刷新页面
3. 找到 `/Task/list` 请求
4. 点击查看 **Response（响应）**

**期望的响应格式**：
```json
{
  "total": 10,
  "rows": [
    {
      "taskId": 1,
      "taskName": "Java实验1",
      "status": "1",  // 任务状态（进行中）
      "studentSubmitStatus": "1",  // ✅ 学生报告状态（已提交）
      "courseName": "Java编程",
      ...
    },
    {
      "taskId": 2,
      "taskName": "Python实验",
      "status": "1",
      "studentSubmitStatus": null,  // ✅ 未开始（没有提交记录）
      "courseName": "Python",
      ...
    }
  ]
}
```

### 4. 检查后端日志

查看后端控制台，搜索以下关键字：

```
ExpTaskController - 查询任务列表
```

检查是否有异常或错误信息。

## 常见问题和解决方案

### 问题1: `studentSubmitStatus` 字段不存在

**症状**：
- 前端控制台显示：`第一个任务的报告状态: undefined`
- 网络响应中没有 `studentSubmitStatus` 字段

**原因**：后端代码没有正确编译或部署

**解决方案**：
```bash
# 1. 重新编译后端
cd E:\Idea_project\rp-ms
mvn clean compile

# 2. 重启后端服务
# 停止旧进程，重新运行 RuoYiApplication
```

### 问题2: 状态值不在预期范围内

**症状**：
- 控制台显示：`未知的报告状态: "7" 类型: "string"`
- 状态值不是 0/1/2/3/4/5

**原因**：数据库中的 status 值异常

**解决方案**：
```sql
-- 检查数据库中的状态值
SELECT DISTINCT status, COUNT(*) as count
FROM exp_task_submit
GROUP BY status;

-- 预期结果应该只有：0, 1, 2, 3, 4, 5
-- 如果有其他值，需要修正数据

-- 修正异常数据（如果需要）
UPDATE exp_task_submit
SET status = '0'  -- 改为草稿
WHERE status NOT IN ('0', '1', '2', '3', '4', '5');
```

### 问题3: 学生用户判断失败

**症状**：
- 控制台没有任何调试日志
- 报告状态列完全不显示

**原因**：前端 `isTeacher` 判断错误，学生被识别为教师

**检查方法**：
在浏览器控制台手动输入：
```javascript
// 查看当前用户权限
console.log('当前用户权限:', this.$store.getters.permissions)

// 检查是否为教师
const isTeacher = this.$store.getters.permissions.some(
  p => p === 'task:task:add' || p === '*:*:*'
)
console.log('是否为教师:', isTeacher)
```

**解决方案**：
- 确认学生账号没有 `task:task:add` 权限
- 检查角色权限配置

### 问题4: 后端查询学生提交记录失败

**症状**：
- 所有任务的 `studentSubmitStatus` 都是 `null`
- 即使学生已提交，状态仍显示"未开始"

**原因**：`selectExpTaskSubmitByTaskIdAndUserId` 方法查询失败

**调试方法**：
在 `ExpTaskController.list()` 方法中添加日志：

```java
// 第164-177行
for (ExpTask task : list)
{
    ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(
        task.getTaskId(), currentUserId);

    // 添加日志
    logger.info("任务ID: {}, 学生ID: {}, 提交记录: {}",
        task.getTaskId(), currentUserId,
        submit != null ? submit.getStatus() : "null");

    if (submit != null)
    {
        task.setStudentSubmitStatus(submit.getStatus());
    }
    else
    {
        task.setStudentSubmitStatus(null);
    }
}
```

**检查数据库**：
```sql
-- 查看学生的提交记录
SELECT t.task_id, t.task_name, s.status, s.submit_time
FROM exp_task t
LEFT JOIN exp_task_submit s ON t.task_id = s.task_id AND s.user_id = 100
WHERE t.dept_id = 103;

-- 确认：
-- 1. user_id 是否正确（100是当前登录学生的ID）
-- 2. task_id 和 user_id 的组合是否能查到数据
-- 3. status 字段的值是否正确
```

### 问题5: 状态值类型不匹配

**症状**：
- 控制台显示：`未知的报告状态: 1 类型: "number"`
- 状态值是数字而不是字符串

**原因**：后端返回的是数字类型，前端期望字符串

**解决方案1（推荐）** - 修改前端 reportState.js：
```javascript
// 添加数字映射
export const ReportStateDesc = {
  [ReportState.DRAFT]: '草稿',
  [ReportState.SUBMITTED]: '已提交',
  [ReportState.REVIEWING]: '批阅中',
  [ReportState.REVIEWED]: '已批阅',
  [ReportState.REJECTED]: '已打回',
  [ReportState.RESUBMITTED]: '重新提交',
  [ReportState.ARCHIVED]: '已归档',
  // 添加数字键映射
  0: '草稿',
  1: '已提交',
  2: '批阅中',
  3: '已批阅',
  4: '已打回',
  5: '重新提交',
  6: '已归档'
}

export const ReportStateType = {
  [ReportState.DRAFT]: 'info',
  [ReportState.SUBMITTED]: 'primary',
  [ReportState.REVIEWING]: 'warning',
  [ReportState.REVIEWED]: 'success',
  [ReportState.REJECTED]: 'danger',
  [ReportState.RESUBMITTED]: 'primary',
  [ReportState.ARCHIVED]: 'info',
  // 添加数字键映射
  0: 'info',
  1: 'primary',
  2: 'warning',
  3: 'success',
  4: 'danger',
  5: 'primary',
  6: 'info'
}
```

**解决方案2** - 修改前端显示逻辑，转换为字符串：
```vue
<el-tag v-else :type="getStateType(String(scope.row.studentSubmitStatus))" size="medium">
  {{ getStateDesc(String(scope.row.studentSubmitStatus)) }}
</el-tag>
```

## 完整测试流程

### 1. 准备测试数据

```sql
-- 创建一个测试任务
INSERT INTO exp_task (task_name, course_name, dept_id, deadline, status, create_by, create_time)
VALUES ('测试任务', '测试课程', 103, DATE_ADD(NOW(), INTERVAL 7 DAY), '1', 'admin', NOW());

-- 获取刚创建的任务ID
SET @taskId = LAST_INSERT_ID();

-- 为学生创建提交记录（已提交状态）
INSERT INTO exp_task_submit (task_id, user_id, user_name, file_url, status, document_key, document_version)
VALUES (@taskId, 100, 'student', 'http://test.com/file.docx', '1', 'key123', 1);
```

### 2. 学生端登录测试

1. 使用学生账号登录（user_id = 100）
2. 进入"实验任务管理"
3. 查看"测试任务"的报告状态

**预期结果**：
- 任务状态：进行中（蓝色）
- 报告状态：已提交（蓝色）

### 3. 测试不同状态

```sql
-- 测试草稿状态
UPDATE exp_task_submit SET status = '0' WHERE task_id = @taskId AND user_id = 100;

-- 测试批阅中状态
UPDATE exp_task_submit SET status = '2' WHERE task_id = @taskId AND user_id = 100;

-- 测试已批阅状态
UPDATE exp_task_submit SET status = '3', score = 85 WHERE task_id = @taskId AND user_id = 100;

-- 测试已打回状态
UPDATE exp_task_submit SET status = '4', reject_reason = '需要修改' WHERE task_id = @taskId AND user_id = 100;

-- 测试重新提交状态
UPDATE exp_task_submit SET status = '5' WHERE task_id = @taskId AND user_id = 100;
```

每次修改后刷新页面，检查报告状态是否正确显示。

## 移除调试日志

问题解决后，删除调试代码：

### 前端 (index.vue)

**删除第308-313行的调试日志**：
```javascript
// 删除这段
// 调试：查看学生报告状态
if (!this.isTeacher && this.taskList.length > 0) {
  console.log('学生端任务列表数据:', this.taskList)
  console.log('第一个任务的报告状态:', this.taskList[0].studentSubmitStatus)
}
```

**简化第480-487行的方法**：
```javascript
/** 获取报告状态描述 */
getStateDesc(state) {
  return getStateDesc(state)
},
```

## 总结

最常见的原因：
1. ✅ 后端代码未编译/未重启
2. ✅ 用户权限判断错误（学生被识别为教师）
3. ✅ 数据库查询失败（user_id 不匹配）
4. ✅ 状态值类型不匹配（数字 vs 字符串）

建议按以下顺序排查：
1. 检查浏览器控制台日志
2. 检查网络请求响应
3. 检查数据库数据
4. 检查后端日志
