# 学生端报告状态筛选功能

## 功能概述

在学生端的实验任务管理页面，添加按报告状态分类查看任务的功能。学生可以通过点击不同的状态标签（草稿、已提交、批阅中等），快速筛选查看对应状态的任务。

## 功能特性

### ✨ 核心功能

1. **状态标签页** - 显示8个状态分类标签
   - 📄 全部 - 显示所有任务
   - ⭕ 未开始 - 未打开编辑器的任务
   - ✏️ 草稿 - 已打开编辑器但未提交
   - 📤 已提交 - 学生已提交，等待批阅
   - 👀 批阅中 - 教师正在批阅
   - ✅ 已批阅 - 教师已完成批阅
   - ⚠️ 已打回 - 教师要求重新提交
   - 🔄 重新提交 - 学生修改后重新提交

2. **数量徽章** - 每个标签显示对应状态的任务数量
   - 数量为0时自动隐藏徽章
   - 不同状态使用不同颜色的徽章

3. **实时筛选** - 点击标签立即筛选任务列表
   - 无需刷新页面
   - 响应速度快

4. **仅学生端显示** - 教师端不显示状态筛选功能
   - 教师查看自己发布的所有任务
   - 学生查看自己的任务并按状态筛选

## 界面展示

### 学生端界面

```
┌─────────────────────────────────────────────────────────┐
│ 📚 我的实验任务                                          │
│ 查看所有实验任务，点击"在线完成"按钮开始做实验...        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 🔍 搜索任务...                     [搜索] [重置] [+发布] │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ [📄 全部 15] [⭕ 未开始 3] [✏️ 草稿 2] [📤 已提交 5]    │
│ [👀 批阅中 1] [✅ 已批阅 3] [⚠️ 已打回 1] [🔄 重新提交 0]│
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 任务名称    课程  截止时间  提交情况  任务状态  报告状态 │
├─────────────────────────────────────────────────────────┤
│ Java实验1   Java  01-15    15/30    进行中    已提交    │
│ Python实验  Py    01-20    10/30    进行中    未开始    │
│ 数据库实验  DB    01-25    20/30    进行中    已批阅    │
└─────────────────────────────────────────────────────────┘
```

### 状态徽章颜色

| 状态 | 徽章颜色 | Element UI 类型 |
|------|---------|----------------|
| 全部 | 灰色 | default |
| 未开始 | 灰色 | info |
| 草稿 | 灰色 | info |
| 已提交 | 蓝色 | primary |
| 批阅中 | 橙色 | warning |
| 已批阅 | 绿色 | success |
| 已打回 | 红色 | danger |
| 重新提交 | 蓝色 | primary |

## 实现细节

### 1. 前端组件结构

**文件**: `ruoyi-ui/src/views/task/index.vue`

#### 新增DOM结构 (第42-101行)

```vue
<!-- 学生端：报告状态筛选标签页 -->
<el-card v-if="!isTeacher" shadow="never" class="filter-card">
  <el-tabs v-model="activeStatusTab" @tab-click="handleStatusTabClick">
    <!-- 8个状态标签页 -->
    <el-tab-pane label="全部" name="all">
      <span slot="label">
        <i class="el-icon-document"></i>
        全部
        <el-badge :value="statusCounts.all" :hidden="statusCounts.all === 0" />
      </span>
    </el-tab-pane>
    <!-- ... 其他7个标签 ... -->
  </el-tabs>
</el-card>
```

**关键点**:
- `v-if="!isTeacher"` - 仅学生端显示
- `v-model="activeStatusTab"` - 绑定当前选中的标签
- `@tab-click="handleStatusTabClick"` - 标签点击事件
- `:value="statusCounts.all"` - 显示任务数量
- `:hidden="statusCounts.all === 0"` - 数量为0时隐藏徽章

#### 新增Data属性 (第322-334行)

```javascript
data() {
  return {
    // ... 其他属性 ...

    // 学生端：当前选中的状态标签
    activeStatusTab: 'all',

    // 学生端：各状态的任务数量
    statusCounts: {
      all: 0,
      null: 0,    // 未开始
      '0': 0,     // 草稿
      '1': 0,     // 已提交
      '2': 0,     // 批阅中
      '3': 0,     // 已批阅
      '4': 0,     // 已打回
      '5': 0      // 重新提交
    }
  }
}
```

#### 新增Computed属性 (第362-379行)

```javascript
computed: {
  /** 过滤后的任务列表（根据选中的状态标签） */
  filteredTaskList() {
    // 教师端或选择"全部"，返回完整列表
    if (this.isTeacher || this.activeStatusTab === 'all') {
      return this.taskList
    }

    // 学生端根据状态筛选
    return this.taskList.filter(task => {
      if (this.activeStatusTab === 'null') {
        // 未开始：studentSubmitStatus 为 null 或 undefined
        return !task.studentSubmitStatus
      } else {
        // 其他状态：精确匹配
        return task.studentSubmitStatus === this.activeStatusTab
      }
    })
  }
}
```

**筛选逻辑**:
- 选择"全部"：返回所有任务
- 选择"未开始"：筛选 `studentSubmitStatus` 为 `null` 或 `undefined` 的任务
- 选择其他状态：精确匹配 `studentSubmitStatus` 的值

#### 新增方法

**1. updateStatusCounts() - 统计各状态数量** (第413-444行)

```javascript
updateStatusCounts() {
  // 重置计数
  this.statusCounts = {
    all: 0,
    null: 0,
    '0': 0,
    '1': 0,
    '2': 0,
    '3': 0,
    '4': 0,
    '5': 0
  }

  // 统计各状态数量
  this.taskList.forEach(task => {
    this.statusCounts.all++

    if (!task.studentSubmitStatus) {
      // 未开始
      this.statusCounts.null++
    } else {
      // 其他状态
      const status = task.studentSubmitStatus
      if (this.statusCounts.hasOwnProperty(status)) {
        this.statusCounts[status]++
      }
    }
  })

  console.log('状态统计:', this.statusCounts)
}
```

**调用时机**: 每次获取任务列表后（`getList()` 方法中）

**2. handleStatusTabClick() - 标签点击事件** (第445-449行)

```javascript
handleStatusTabClick(tab) {
  console.log('切换到状态标签:', tab.name)
  // 标签页切换时，filteredTaskList 会自动更新
}
```

**说明**: 由于使用了 computed 属性，标签切换时数据会自动过滤，无需手动操作。

#### 修改getList() - 添加统计逻辑 (第401-404行)

```javascript
listTask(params).then(response => {
  this.taskList = response.rows
  this.total = response.total
  this.loading = false

  // 学生端：统计各状态的任务数量
  if (!this.isTeacher) {
    this.updateStatusCounts()
  }
})
```

#### 修改表格数据源 (第105行)

```vue
<!-- 修改前 -->
<el-table :data="taskList">

<!-- 修改后 -->
<el-table :data="filteredTaskList">
```

**说明**: 表格显示过滤后的任务列表，而不是原始的 `taskList`。

### 2. CSS样式 (第654-684行)

```css
/* 状态筛选卡片 */
.filter-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.filter-card ::v-deep .el-tabs__header {
  margin-bottom: 0;
}

.filter-card ::v-deep .el-tabs__item {
  font-size: 14px;
  padding: 0 24px;
  height: 50px;
  line-height: 50px;
}

.filter-card ::v-deep .el-tabs__item i {
  margin-right: 6px;
}

.filter-card ::v-deep .el-badge {
  margin-left: 8px;
}

.filter-card ::v-deep .el-badge__content {
  font-size: 11px;
  padding: 0 5px;
  height: 16px;
  line-height: 16px;
}
```

**样式说明**:
- 标签页高度设为50px，更易点击
- 图标和徽章添加适当间距
- 徽章字体和尺寸调整为更小巧

## 使用场景

### 场景1: 查看未完成的任务

**操作**: 学生点击"未开始"标签

**结果**: 只显示还未打开编辑器的任务

**用途**: 学生快速找到还没开始做的实验

### 场景2: 查看待批阅的任务

**操作**: 学生点击"已提交"标签

**结果**: 只显示已提交但还未被批阅的任务

**用途**: 学生了解哪些任务正在等待教师批阅

### 场景3: 查看成绩

**操作**: 学生点击"已批阅"标签

**结果**: 只显示教师已完成批阅的任务

**用途**: 学生查看已获得分数的任务

### 场景4: 查看需要修改的任务

**操作**: 学生点击"已打回"标签

**结果**: 只显示被教师打回的任务

**用途**: 学生快速找到需要修改并重新提交的任务

### 场景5: 统计任务完成情况

**操作**: 查看各标签的数量徽章

**示例**:
```
全部 15    未开始 3    草稿 2    已提交 5
批阅中 1   已批阅 3    已打回 1   重新提交 0
```

**分析**:
- 总共15个任务
- 已完成（已批阅）: 3个
- 待办（未开始+草稿+已打回）: 6个
- 等待中（已提交+批阅中+重新提交）: 6个

## 数据流程

```
用户登录（学生）
    ↓
进入任务列表页面
    ↓
调用 /Task/list API
    ↓
后端返回任务列表（包含 studentSubmitStatus）
    ↓
前端接收数据存入 taskList
    ↓
调用 updateStatusCounts() 统计各状态数量
    ↓
显示状态标签页和徽章数量
    ↓
用户点击某个状态标签
    ↓
activeStatusTab 更新
    ↓
computed: filteredTaskList 自动重新计算
    ↓
表格显示过滤后的任务
```

## 兼容性说明

### 1. 教师端不受影响

- 教师端不显示状态筛选标签页（`v-if="!isTeacher"`）
- 教师端表格仍然显示所有任务
- 教师端的提交列表功能不受影响

### 2. 向后兼容

- 如果后端未返回 `studentSubmitStatus` 字段，所有任务默认显示在"未开始"标签
- 不影响已有功能（搜索、分页、查看、编辑、删除）

### 3. 性能优化

- 使用 computed 属性实现筛选，性能高效
- 状态统计只在数据加载时执行一次
- 标签切换无需重新请求数据

## 测试步骤

### 1. 准备测试数据

创建不同状态的任务提交记录：

```sql
-- 假设有3个任务，当前学生 user_id = 100

-- 任务1：已提交
INSERT INTO exp_task_submit (task_id, user_id, status) VALUES (1, 100, '1');

-- 任务2：已批阅
INSERT INTO exp_task_submit (task_id, user_id, status, score) VALUES (2, 100, '3', 85);

-- 任务3：已打回
INSERT INTO exp_task_submit (task_id, user_id, status, reject_reason) VALUES (3, 100, '4', '需要修改');

-- 任务4：草稿
INSERT INTO exp_task_submit (task_id, user_id, status) VALUES (4, 100, '0');

-- 任务5：未开始（没有提交记录）
-- 不插入数据
```

### 2. 学生端测试

**步骤1**: 使用学生账号登录

**步骤2**: 进入"实验任务管理"页面

**预期**:
- 看到状态筛选标签页
- 各标签显示对应的数量徽章

**步骤3**: 点击"全部"标签

**预期**: 显示所有5个任务

**步骤4**: 点击"已提交"标签

**预期**: 只显示任务1

**步骤5**: 点击"已批阅"标签

**预期**: 只显示任务2

**步骤6**: 点击"已打回"标签

**预期**: 只显示任务3

**步骤7**: 点击"草稿"标签

**预期**: 只显示任务4

**步骤8**: 点击"未开始"标签

**预期**: 只显示任务5

**步骤9**: 打开浏览器控制台

**预期**: 看到状态统计日志
```javascript
状态统计: {
  all: 5,
  null: 1,
  '0': 1,
  '1': 1,
  '2': 0,
  '3': 1,
  '4': 1,
  '5': 0
}
```

### 3. 教师端测试

**步骤1**: 使用教师账号登录

**步骤2**: 进入"实验任务管理"页面

**预期**:
- 不显示状态筛选标签页
- 直接显示任务列表
- 功能与之前一致

### 4. 边界测试

**测试1**: 没有任何任务
```
预期: 所有标签的徽章都隐藏（数量为0）
```

**测试2**: 所有任务都是"未开始"
```
预期:
- 全部: 显示数量
- 未开始: 显示数量
- 其他标签: 徽章隐藏
```

**测试3**: 快速切换标签
```
预期: 表格内容立即更新，无延迟
```

## 调试日志

代码中添加了多处调试日志，方便排查问题：

```javascript
// 1. 任务列表数据
console.log('学生端任务列表数据:', this.taskList)
console.log('第一个任务的报告状态:', this.taskList[0].studentSubmitStatus)

// 2. 状态统计
console.log('状态统计:', this.statusCounts)

// 3. 标签切换
console.log('切换到状态标签:', tab.name)

// 4. 未知状态警告
console.warn('未知的报告状态:', state, '类型:', typeof state)
```

**生产环境建议**: 移除或注释掉这些日志。

## 常见问题

### Q1: 所有任务都显示在"未开始"？

**原因**: 后端未返回 `studentSubmitStatus` 字段

**解决**:
1. 检查后端 `ExpTaskController.list()` 方法是否正确查询
2. 检查网络请求响应是否包含 `studentSubmitStatus`
3. 重启后端服务

### Q2: 数量徽章不显示或显示错误？

**原因**: `updateStatusCounts()` 统计逻辑错误

**解决**:
1. 打开浏览器控制台
2. 查看"状态统计"日志
3. 检查 `taskList` 中的 `studentSubmitStatus` 值

### Q3: 点击标签后任务列表不更新？

**原因**: `filteredTaskList` computed 属性未生效

**解决**:
1. 检查 `activeStatusTab` 是否正确绑定
2. 检查 `@tab-click` 事件是否触发
3. 清除浏览器缓存重试

### Q4: 教师端也显示了筛选标签？

**原因**: `isTeacher` 计算属性判断错误

**解决**:
1. 检查教师账号是否有 `task:task:add` 权限
2. 在控制台执行 `console.log(this.isTeacher)`
3. 检查角色权限配置

## 相关文件

- `ruoyi-ui/src/views/task/index.vue` - 主要修改文件
- `ruoyi-ui/src/constants/reportState.js` - 状态常量定义
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java` - 后端接口
- `TASK_STATUS_AND_SUBMIT_STATUS_SEPARATION.md` - 任务状态与报告状态分离文档

## 总结

### 功能优势

- ✅ **高效筛选**: 一键查看特定状态的任务
- ✅ **直观统计**: 徽章数量一目了然
- ✅ **用户友好**: 无需刷新，响应迅速
- ✅ **分类清晰**: 8个状态覆盖所有场景
- ✅ **性能优异**: 使用computed属性，无额外请求

### 用户价值

对学生：
- 快速找到需要完成的任务（未开始、草稿）
- 及时查看批阅结果（已批阅）
- 清楚了解待办事项（已打回）
- 掌握整体进度（数量统计）

对系统：
- 提升用户体验
- 减少用户操作步骤
- 降低认知负担
- 增强系统易用性
