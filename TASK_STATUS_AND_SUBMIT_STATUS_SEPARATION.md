# 任务状态与报告状态分离显示

## 功能需求

学生端的实验任务管理页面，需要分开显示：
1. **任务状态**：实验任务本身的时间状态（未开始、进行中、已结束）
2. **报告状态**：学生自己的报告提交状态（草稿、已提交、批阅中、已批阅、已打回、重新提交）

## 修改内容

### 1. 后端修改

#### 1.1 ExpTask.java - 添加学生报告状态字段

**文件位置**: `ruoyi-system/src/main/java/com/ruoyi/system/domain/ExpTask.java`

**修改内容**:
```java
/** 学生报告状态（仅学生端使用，0草稿 1已提交 2批阅中 3已批阅 4已打回 5重新提交） */
private String studentSubmitStatus;

public String getStudentSubmitStatus()
{
    return studentSubmitStatus;
}

public void setStudentSubmitStatus(String studentSubmitStatus)
{
    this.studentSubmitStatus = studentSubmitStatus;
}
```

**说明**:
- 此字段仅在学生端查询任务列表时填充
- 教师端不需要此字段
- 字段值对应 `exp_task_submit` 表的 `status` 字段

#### 1.2 ExpTaskController.java - 查询学生报告状态

**文件位置**: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java`

**修改位置**: 第125-180行 (`list` 方法)

**修改内容**:
```java
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

    // 如果是学生，查询每个任务的学生报告状态
    if (isStudent && currentUserId != null)
    {
        for (ExpTask task : list)
        {
            ExpTaskSubmit submit = expTaskSubmitService.selectExpTaskSubmitByTaskIdAndUserId(task.getTaskId(), currentUserId);
            if (submit != null)
            {
                task.setStudentSubmitStatus(submit.getStatus());
            }
            else
            {
                // 未创建提交记录，状态为null（前端可显示为"未开始"）
                task.setStudentSubmitStatus(null);
            }
        }
    }

    return getDataTable(list);
}
```

**核心逻辑**:
1. 判断当前用户是否为学生（无 `task:task:add` 权限）
2. 如果是学生，查询任务列表后，遍历每个任务
3. 根据 `taskId` 和 `userId` 查询学生的提交记录
4. 将提交记录的 `status` 设置到 `task.studentSubmitStatus`
5. 如果没有提交记录，设为 `null`（表示未开始）

### 2. 前端修改

#### 2.1 index.vue - 分开显示两种状态

**文件位置**: `ruoyi-ui/src/views/task/index.vue`

**修改1**: 导入状态常量（第227行）
```javascript
import { getStateDesc, getStateType } from "@/constants/reportState"
```

**修改2**: 表格列修改（第81-96行）
```vue
<el-table-column label="任务状态" align="center" width="100">
  <template slot-scope="scope">
    <el-tag v-if="scope.row.status === '0'" type="warning" size="medium">未开始</el-tag>
    <el-tag v-else-if="scope.row.status === '1'" type="primary" size="medium">进行中</el-tag>
    <el-tag v-else-if="scope.row.status === '2'" type="success" size="medium">已结束</el-tag>
  </template>
</el-table-column>
<!-- 学生端显示报告状态 -->
<el-table-column v-if="!isTeacher" label="报告状态" align="center" width="100">
  <template slot-scope="scope">
    <el-tag v-if="scope.row.studentSubmitStatus === null" type="info" size="medium">未开始</el-tag>
    <el-tag v-else :type="getStateType(scope.row.studentSubmitStatus)" size="medium">
      {{ getStateDesc(scope.row.studentSubmitStatus) }}
    </el-tag>
  </template>
</el-table-column>
```

**修改3**: 添加状态处理方法（第473-480行）
```javascript
/** 获取报告状态描述 */
getStateDesc(state) {
  return getStateDesc(state)
},
/** 获取报告状态标签类型 */
getStateType(state) {
  return getStateType(state)
}
```

## 显示效果

### 教师端
```
| 任务名称 | 课程名称 | 发布时间 | 截止时间 | 提交情况 | 任务状态 | 操作 |
| Java实验1 | Java编程 | 2024-01-01 | 2024-01-15 12:00 | 15/30 | 进行中 | ... |
```

### 学生端
```
| 任务名称 | 课程名称 | 发布时间 | 截止时间 | 提交情况 | 任务状态 | 报告状态 | 操作 |
| Java实验1 | Java编程 | 2024-01-01 | 2024-01-15 12:00 | 15/30 | 进行中 | 已提交 | ... |
| Python实验 | Python | 2024-01-05 | 2024-01-20 18:00 | 10/30 | 进行中 | 未开始 | ... |
| 数据库实验 | 数据库 | 2024-01-10 | 2024-01-25 23:59 | 20/30 | 进行中 | 已批阅 | ... |
```

## 状态说明

### 任务状态（基于时间计算）
- **未开始** (0): 当前时间 < 任务创建时间
- **进行中** (1): 任务创建时间 ≤ 当前时间 < 截止时间
- **已结束** (2): 当前时间 ≥ 截止时间

任务状态由后端 `ExpTaskServiceImpl.updateTaskStatus()` 方法动态计算。

### 报告状态（基于提交记录）
- **未开始** (null): 学生未打开过编辑器，没有提交记录
- **草稿** (0): 学生打开了编辑器，但未提交
- **已提交** (1): 学生已提交，等待教师批阅
- **批阅中** (2): 教师已开始批阅
- **已批阅** (3): 教师已完成批阅并给出分数
- **已打回** (4): 教师要求学生修改后重新提交
- **重新提交** (5): 学生修改后重新提交，等待教师再次批阅

报告状态存储在 `exp_task_submit` 表的 `status` 字段中。

## 典型场景示例

### 场景1: 任务进行中，学生未开始
```
任务状态: 进行中 (蓝色)
报告状态: 未开始 (灰色)
操作: 显示"在线完成"按钮
```

### 场景2: 任务进行中，学生已提交
```
任务状态: 进行中 (蓝色)
报告状态: 已提交 (蓝色)
操作: 显示"查看"按钮
```

### 场景3: 任务已结束，学生已批阅
```
任务状态: 已结束 (绿色)
报告状态: 已批阅 (绿色)
操作: 显示"查看"按钮
```

### 场景4: 任务进行中，报告被打回
```
任务状态: 进行中 (蓝色)
报告状态: 已打回 (红色)
操作: 显示"在线完成"按钮（修改后重新提交）
```

### 场景5: 任务已结束，学生未提交
```
任务状态: 已结束 (绿色)
报告状态: 未开始/草稿 (灰色)
操作: 不显示"在线完成"按钮（已过期）
```

## 优势

### 信息清晰
- **任务状态**: 告诉学生任务的时间安排（还有多久截止）
- **报告状态**: 告诉学生自己的完成进度（是否需要操作）

### 逻辑分离
- 任务状态与学生个人行为无关，是任务本身的属性
- 报告状态是学生个人的提交记录，每个学生不同

### 用户体验
学生可以同时看到：
1. 这个任务是否还在进行中（能否提交）
2. 我自己的报告处于什么状态（下一步该做什么）

## 测试步骤

### 1. 教师端测试

**步骤1**: 教师登录，查看任务列表
```
预期: 只显示"任务状态"列，不显示"报告状态"列
```

**步骤2**: 检查任务状态
```
操作: 查看不同时间的任务
预期:
- 未到创建时间: 未开始
- 创建时间到截止时间之间: 进行中
- 超过截止时间: 已结束
```

### 2. 学生端测试

**步骤1**: 学生登录，查看任务列表
```
预期: 同时显示"任务状态"和"报告状态"两列
```

**步骤2**: 测试未开始的任务
```
操作: 查看从未打开过的任务
预期: 报告状态显示"未开始"（灰色）
```

**步骤3**: 测试草稿状态
```
操作:
1. 点击"在线完成"，打开编辑器
2. 不做任何修改，直接返回任务列表
预期: 报告状态显示"草稿"（灰色）
```

**步骤4**: 测试已提交状态
```
操作:
1. 打开编辑器，编辑文档
2. 点击"提交"
3. 返回任务列表
预期: 报告状态显示"已提交"（蓝色）
```

**步骤5**: 测试已批阅状态
```
前置: 教师已批改
操作: 学生查看任务列表
预期: 报告状态显示"已批阅"（绿色）
```

**步骤6**: 测试已打回状态
```
前置: 教师已打回报告
操作: 学生查看任务列表
预期: 报告状态显示"已打回"（红色）
```

**步骤7**: 测试重新提交状态
```
前置: 学生修改后重新提交
操作: 学生查看任务列表
预期: 报告状态显示"重新提交"（蓝色）
```

### 3. 状态颜色验证

```sql
-- 查看学生的提交状态
SELECT
    t.task_name,
    t.status AS task_status,
    s.status AS submit_status,
    s.submit_time,
    s.score
FROM exp_task t
LEFT JOIN exp_task_submit s ON t.task_id = s.task_id AND s.user_id = 100
WHERE t.dept_id = 103
ORDER BY t.create_time DESC;
```

预期结果验证：
- `task_status`: 任务状态（0/1/2）
- `submit_status`: 学生报告状态（0/1/2/3/4/5/null）
- 前端显示的标签颜色和文字应与数据库一致

## 相关文件

- `ruoyi-system/src/main/java/com/ruoyi/system/domain/ExpTask.java` - 添加 studentSubmitStatus 字段
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java` - 查询学生报告状态逻辑
- `ruoyi-ui/src/views/task/index.vue` - 前端显示逻辑
- `ruoyi-ui/src/constants/reportState.js` - 状态常量定义

## 总结

### 修改前
- ❌ 只有一个"状态"列，混淆了任务状态和报告状态
- ❌ 学生无法区分任务是否截止 vs 自己是否已提交

### 修改后
- ✅ 分开显示"任务状态"和"报告状态"
- ✅ 教师端只显示任务状态（不需要报告状态）
- ✅ 学生端同时显示任务状态和自己的报告状态
- ✅ 状态颜色区分清晰（使用reportState.js统一管理）
- ✅ 信息更加清晰，用户体验更好

现在学生可以一目了然地看到：
1. 这个任务是否还能提交（任务状态）
2. 我的报告处于什么阶段（报告状态）
