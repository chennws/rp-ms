# 批改列表状态转换优化

## 问题描述

**错误日志**：
```
当前状态: REVIEWING, 触发器: START_REVIEW
不允许的状态转换: REVIEWING -> START_REVIEW
```

**问题场景**：
1. 教师点击"开始批改"某个报告
2. 报告状态变为 REVIEWING（批阅中）
3. 教师关闭页面，回到批改列表
4. 再次点击"开始批改"同一个报告
5. **错误**：系统尝试触发 START_REVIEW，但当前状态已经是 REVIEWING

**根本原因**：
- 批改列表中，无论报告状态如何，点击"开始批改"都会触发 `startReview()` 状态机操作
- 如果报告已经处于"批阅中"状态，再次触发 START_REVIEW 会导致状态转换失败

---

## 解决方案

### 1. 智能判断是否需要触发状态机

**修改文件**：`ruoyi-ui/src/views/task/review-list.vue`

**优化逻辑**：
```javascript
handleStartReview(row) {
  // 只有在"已提交"或"重新提交"状态时，才触发状态机
  const needStartReview = row.status === ReportState.SUBMITTED ||
                          row.status === ReportState.RESUBMITTED

  if (needStartReview) {
    // 触发状态机：已提交/重新提交 -> 批阅中
    startReview(row.submitId).then(() => {
      this.$router.push({
        path: `/task/review/${this.taskId}/${row.submitId}`
      })
    }).catch(() => {
      // 失败也允许查看
      this.$router.push({
        path: `/task/review/${this.taskId}/${row.submitId}`
      })
    })
  } else {
    // 状态已经是批阅中，直接跳转，不触发状态机
    this.$router.push({
      path: `/task/review/${this.taskId}/${row.submitId}`
    })
  }
}
```

---

### 2. 根据状态显示不同的按钮文本

**优化前**：
```vue
<el-button @click="handleStartReview(scope.row)">开始批改</el-button>
```
- 所有待批改的报告都显示"开始批改"
- 无法区分是首次批改还是继续批改

**优化后**：
```vue
<!-- 已提交、重新提交：显示"开始批改" -->
<el-button
  v-if="scope.row.status === ReportState.SUBMITTED ||
        scope.row.status === ReportState.RESUBMITTED"
  type="primary"
  @click="handleStartReview(scope.row)"
>开始批改</el-button>

<!-- 批阅中：显示"继续批改" -->
<el-button
  v-if="scope.row.status === ReportState.REVIEWING"
  type="warning"
  @click="handleStartReview(scope.row)"
>继续批改</el-button>

<!-- 已批阅、已归档：显示"查看报告" -->
<el-button
  v-if="scope.row.status === ReportState.REVIEWED ||
        scope.row.status === ReportState.ARCHIVED"
  type="text"
  @click="handleViewReport(scope.row)"
>查看报告</el-button>
```

---

## 状态与操作对应关系

| 报告状态 | 状态码 | 按钮文本 | 按钮颜色 | 是否触发状态机 | 状态转换 |
|---------|-------|---------|---------|--------------|----------|
| 已提交 | 1 | 开始批改 | primary（蓝色） | ✅ 是 | 1 → 2 |
| 批阅中 | 2 | 继续批改 | warning（橙色） | ❌ 否 | 直接跳转 |
| 已批阅 | 3 | 查看报告 | text（文本） | ❌ 否 | 只读模式 |
| 已打回 | 4 | - | - | - | - |
| 重新提交 | 5 | 开始批改 | primary（蓝色） | ✅ 是 | 5 → 2 |
| 已归档 | 6 | 查看报告 | text（文本） | ❌ 否 | 只读模式 |

---

## 用户体验优化

### 场景1：首次批改

**操作流程**：
```
1. 学生提交报告（状态：已提交 1）
   ↓
2. 批改列表显示："开始批改"（蓝色按钮）
   ↓
3. 教师点击"开始批改"
   ↓
4. 触发状态机：已提交(1) → 批阅中(2)
   ↓
5. 跳转到批改页面
```

---

### 场景2：继续批改（中途保存后返回）

**操作流程**：
```
1. 教师批改中（状态：批阅中 2）
   ↓
2. 教师点击"保存"（未点击"保存并下一个"）
   ↓
3. 教师返回批改列表
   ↓
4. 批改列表显示："继续批改"（橙色按钮）
   ↓
5. 教师点击"继续批改"
   ↓
6. ✅ 不触发状态机，直接跳转
   ↓
7. 跳转到批改页面（状态保持为2）
```

**之前的问题**：
- ❌ 显示"开始批改"，容易误解
- ❌ 再次触发状态机，导致错误

**现在的优化**：
- ✅ 显示"继续批改"，语义明确
- ✅ 不触发状态机，直接跳转
- ✅ 避免状态转换错误

---

### 场景3：查看已批改的报告

**操作流程**：
```
1. 报告已批改完成（状态：已批阅 3）
   ↓
2. 批改列表显示："查看报告"（文本按钮）
   ↓
3. 教师点击"查看报告"
   ↓
4. 跳转到批改页面（只读模式）
```

---

## 技术实现细节

### 1. 状态判断逻辑

```javascript
// 需要触发状态机的状态
const needStartReview =
  row.status === ReportState.SUBMITTED ||      // 已提交(1)
  row.status === ReportState.RESUBMITTED        // 重新提交(5)

// 已经在批阅中的状态（直接跳转）
const isReviewing = row.status === ReportState.REVIEWING  // 批阅中(2)

// 已完成批改的状态（只读查看）
const isCompleted =
  row.status === ReportState.REVIEWED ||        // 已批阅(3)
  row.status === ReportState.ARCHIVED           // 已归档(6)
```

### 2. 按钮显示逻辑

使用 `v-if` 条件渲染，根据状态显示不同的按钮：

```vue
<!-- 条件1: 已提交 OR 重新提交 -->
<el-button v-if="scope.row.status === '1' || scope.row.status === '5'">
  开始批改
</el-button>

<!-- 条件2: 批阅中 -->
<el-button v-if="scope.row.status === '2'">
  继续批改
</el-button>

<!-- 条件3: 已批阅 OR 已归档 -->
<el-button v-if="scope.row.status === '3' || scope.row.status === '6'">
  查看报告
</el-button>
```

### 3. 错误处理

即使状态机调用失败，也允许用户访问批改页面：

```javascript
startReview(row.submitId).then(() => {
  // 成功：跳转
  this.$router.push(...)
}).catch(() => {
  // ✅ 失败也允许跳转，不阻止用户操作
  this.$router.push(...)
})
```

---

## 状态机配置

确保状态机配置正确：

```java
// 已提交 -> 批阅中
config.configure(ReportState.SUBMITTED)
        .permit(ReportTrigger.START_REVIEW, ReportState.REVIEWING);

// 重新提交 -> 批阅中
config.configure(ReportState.RESUBMITTED)
        .permit(ReportTrigger.START_REVIEW, ReportState.REVIEWING);

// 批阅中 -> 已批阅 或 打回
config.configure(ReportState.REVIEWING)
        .permit(ReportTrigger.APPROVE, ReportState.REVIEWED)
        .permit(ReportTrigger.REJECT, ReportState.REJECTED);

// ⚠️ 注意：批阅中状态不允许再次 START_REVIEW
```

---

## 测试场景

### 测试1：首次批改
1. 学生提交报告
2. 访问批改列表
3. **验证**：显示"开始批改"（蓝色）
4. 点击"开始批改"
5. **验证**：成功跳转，状态变为"批阅中"

### 测试2：继续批改
1. 教师批改中途点击"保存"
2. 返回批改列表
3. **验证**：显示"继续批改"（橙色）
4. 点击"继续批改"
5. **验证**：成功跳转，状态仍为"批阅中"，无错误

### 测试3：查看已批改
1. 报告已批改完成
2. 访问批改列表
3. **验证**：显示"查看报告"（文本）
4. 点击"查看报告"
5. **验证**：以只读模式打开

### 测试4：重新提交后批改
1. 报告被打回，学生重新提交
2. 访问批改列表
3. **验证**：显示"开始批改"（蓝色）
4. 点击"开始批改"
5. **验证**：成功跳转，状态变为"批阅中"

---

## 相关文件

- `ruoyi-ui/src/views/task/review-list.vue` - 批改列表页面

---

## 总结

通过这次优化：
1. **避免状态转换错误** - 智能判断是否需要触发状态机
2. **语义更加明确** - 根据状态显示不同按钮文本
3. **用户体验更好** - "开始批改"vs"继续批改"，一目了然
4. **视觉差异化** - 不同颜色区分不同状态的操作

彻底解决了 REVIEWING -> START_REVIEW 的状态转换错误！
