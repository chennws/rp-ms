# 批改流程优化：只显示待批改报告

## 优化内容

### 1. 只加载待批改的报告到批改列表 ✅

**问题**：
- 原来的批改导航包含所有已提交的报告（包括已批改的）
- 教师批改时会看到已经批改过的报告
- 容易混淆，不知道哪些需要批改

**解决方案**：
修改后端 `getSubmitIdList` 接口，只返回待批改的报告：
- ✅ 已提交（状态=1）
- ✅ 批阅中（状态=2）
- ✅ 重新提交（状态=5）

排除已批改的报告：
- ❌ 已批阅（状态=3）
- ❌ 已打回（状态=4）
- ❌ 已归档（状态=6）

**代码修改**：

**后端** - `ExpTaskController.java:984-1007`
```java
// 只返回待批改的记录的ID列表（已提交1、批阅中2、重新提交5）
List<Long> idList = list.stream()
    .filter(submit -> submit.getSubmitTime() != null)  // 已提交
    .filter(submit -> {
        String status = submit.getStatus();
        // 只包含：已提交(1)、批阅中(2)、重新提交(5)
        return "1".equals(status) || "2".equals(status) || "5".equals(status);
    })
    .map(ExpTaskSubmit::getSubmitId)
    .collect(java.util.stream.Collectors.toList());

logger.info("任务{}的待批改报告数量: {}", taskId, idList.size());
```

---

### 2. 批改完成后自动返回列表 ✅

**问题**：
- 批改完最后一个报告后，点击"保存并下一个"会提示"全部批改完成"
- 但用户需要手动点击"返回列表"才能回到列表页面
- 操作步骤多余

**解决方案**：
- 批改保存成功后，重新获取待批改列表
- 如果列表为空（所有待批改的都批改完了），**自动返回批改列表**
- 显示友好的完成提示："🎉 恭喜！所有报告批改完成！正在返回批改列表..."

**代码修改**：

**前端** - `review-detail.vue:handleSaveAndNext`
```javascript
saveReview(data).then(() => {
  this.saving = false

  // 重新加载待批改列表，因为当前报告已批改完成
  return getSubmitIdList(this.taskId)
}).then(response => {
  const newSubmitIdList = response.data || []
  console.log('批改后的待批改列表:', newSubmitIdList)

  // 检查是否还有待批改的报告
  if (newSubmitIdList.length === 0) {
    // ✅ 已经没有待批改的报告了，全部批改完成
    this.$modal.msgSuccess("🎉 恭喜！所有报告批改完成！正在返回批改列表...")
    setTimeout(() => {
      this.$router.push(`/task/review/${this.taskId}`)
    }, 2000)
    return
  }

  // 找到下一个待批改的报告（直接取第一个）
  const nextSubmitId = newSubmitIdList[0]

  if (nextSubmitId) {
    this.$modal.msgSuccess("批改保存成功，正在跳转到下一个...")
    setTimeout(() => {
      // 跳转到下一个待批改报告
      this.$router.replace({
        path: `/task/review/${this.taskId}/${nextSubmitId}`
      })
      // 重新加载数据
      this.submitId = nextSubmitId
      this.loadSubmitDetail()
      this.loadNextPrevInfo()
    }, 500)
  }
})
```

---

### 3. 保存后实时更新导航列表 ✅

**优化点**：
- 点击"保存"按钮后，也会重新加载待批改列表
- 如果当前报告已批改完成，导航按钮会实时更新
- 确保导航信息始终准确

**代码修改**：

**前端** - `review-detail.vue:handleSave`
```javascript
saveReview(data).then(() => {
  this.$modal.msgSuccess("批改保存成功")
  this.saving = false
  // 刷新提交信息
  this.loadSubmitDetail()
  // ✅ 重新加载待批改列表（因为当前报告状态可能已变化）
  this.loadNextPrevInfo()
})
```

**前端** - `review-detail.vue:loadNextPrevInfo`
```javascript
loadNextPrevInfo() {
  getSubmitIdList(this.taskId).then(response => {
    this.submitIdList = response.data || []
    const currentIndex = this.submitIdList.indexOf(this.submitId)

    console.log('待批改报告ID列表:', this.submitIdList)
    console.log('当前报告ID:', this.submitId, '索引:', currentIndex)

    if (currentIndex >= 0) {
      // 当前报告在待批改列表中
      this.prevSubmitId = currentIndex > 0 ? this.submitIdList[currentIndex - 1] : null
      this.nextSubmitId = currentIndex < this.submitIdList.length - 1 ? this.submitIdList[currentIndex + 1] : null

      this.progress.current = currentIndex + 1
      this.progress.total = this.submitIdList.length
      this.progress.reviewed = currentIndex + 1
    } else {
      // ✅ 当前报告不在待批改列表中（可能已经批改完成）
      console.warn('当前报告不在待批改列表中，可能已批改完成')
      this.prevSubmitId = null
      this.nextSubmitId = null
      this.progress.current = 0
      this.progress.total = this.submitIdList.length
      this.progress.reviewed = 0
    }

    console.log('上一个:', this.prevSubmitId, '下一个:', this.nextSubmitId)
    console.log('进度:', this.progress.reviewed, '/', this.progress.total)
  })
}
```

---

## 用户体验流程

### 场景1：批改中途保存

**之前**：
```
1. 教师批改报告
2. 点击"保存"
3. 提示"保存成功"
4. 上一个/下一个按钮状态不变
```

**现在**：
```
1. 教师批改报告
2. 点击"保存"
3. 提示"保存成功"
4. ✅ 自动刷新待批改列表
5. ✅ 上一个/下一个按钮实时更新
```

---

### 场景2：批改并跳转到下一个

**之前**：
```
1. 教师批改报告
2. 点击"保存并下一个"
3. 如果有下一个：跳转到下一个报告（可能是已批改的）
4. 如果没有下一个：提示"全部批改完成"，需手动返回
```

**现在**：
```
1. 教师批改报告
2. 点击"保存并下一个"
3. ✅ 系统重新获取待批改列表
4. 如果还有待批改的：
   - 提示"批改保存成功，正在跳转到下一个..."
   - 0.5秒后自动跳转到下一个待批改报告
5. 如果没有待批改的了：
   - 提示"🎉 恭喜！所有报告批改完成！正在返回批改列表..."
   - 2秒后自动返回批改列表页面
```

---

### 场景3：批改完成自动返回

**之前**：
```
1. 教师批改最后一个待批改报告
2. 点击"保存并下一个"
3. 提示"全部批改完成"
4. ❌ 需要手动点击"返回列表"
5. 回到批改列表
```

**现在**：
```
1. 教师批改最后一个待批改报告
2. 点击"保存并下一个"
3. 提示"🎉 恭喜！所有报告批改完成！正在返回批改列表..."
4. ✅ 2秒后自动返回批改列表
5. 看到所有报告都已批改
```

---

## 状态说明

### 待批改的报告状态

| 状态码 | 状态名称 | 说明 | 是否包含在批改列表 |
|-------|---------|------|------------------|
| 1 | 已提交 | 学生提交后等待批改 | ✅ 是 |
| 2 | 批阅中 | 教师已开始批改但未完成 | ✅ 是 |
| 5 | 重新提交 | 学生打回后重新提交 | ✅ 是 |

### 不需要批改的报告状态

| 状态码 | 状态名称 | 说明 | 是否包含在批改列表 |
|-------|---------|------|------------------|
| 0 | 草稿 | 学生未提交 | ❌ 否（未提交） |
| 3 | 已批阅 | 教师批改完成 | ❌ 否（已完成） |
| 4 | 已打回 | 教师打回，等学生修改 | ❌ 否（待学生） |
| 6 | 已归档 | 已归档 | ❌ 否（已归档） |

---

## 技术实现细节

### 1. 后端过滤逻辑

**位置**：`ExpTaskController.java:984-1007`

```java
// 过滤条件
.filter(submit -> submit.getSubmitTime() != null)  // 必须已提交
.filter(submit -> {
    String status = submit.getStatus();
    return "1".equals(status) || "2".equals(status) || "5".equals(status);
})
```

### 2. 前端检测逻辑

**位置**：`review-detail.vue:handleSaveAndNext`

```javascript
// 批改保存后重新获取待批改列表
return getSubmitIdList(this.taskId)

// 检查列表是否为空
if (newSubmitIdList.length === 0) {
  // 全部批改完成，自动返回
  this.$modal.msgSuccess("🎉 恭喜！所有报告批改完成！正在返回批改列表...")
  setTimeout(() => {
    this.$router.push(`/task/review/${this.taskId}`)
  }, 2000)
  return
}
```

### 3. 实时更新导航

**位置**：`review-detail.vue:loadNextPrevInfo`

```javascript
// 检查当前报告是否还在待批改列表中
const currentIndex = this.submitIdList.indexOf(this.submitId)

if (currentIndex >= 0) {
  // 在列表中，正常更新导航
  this.prevSubmitId = ...
  this.nextSubmitId = ...
} else {
  // 不在列表中（已批改完成），清空导航
  this.prevSubmitId = null
  this.nextSubmitId = null
}
```

---

## 优化效果对比

### 批改效率

**之前**：
- 批改列表包含所有报告（已批改+未批改）
- 需要教师自己识别哪些需要批改
- 批改完成后需手动返回
- 可能重复批改已完成的报告

**现在**：
- ✅ 批改列表只包含待批改的报告
- ✅ 自动跳转到下一个待批改报告
- ✅ 全部完成后自动返回列表
- ✅ 避免重复批改

### 用户体验

**之前**：
- 不知道还有多少待批改
- 需要手动操作返回
- 批改流程不够流畅

**现在**：
- ✅ 清楚知道待批改数量（进度显示）
- ✅ 全自动流程，无需手动操作
- ✅ 流畅的批改体验
- ✅ 友好的完成提示

---

## 日志输出

系统会在控制台输出详细的调试信息：

```javascript
// 后端日志
logger.info("任务{}的待批改报告数量: {}", taskId, idList.size());

// 前端日志
console.log('待批改报告ID列表:', this.submitIdList)
console.log('当前报告ID:', this.submitId, '索引:', currentIndex)
console.log('批改后的待批改列表:', newSubmitIdList)
console.log('上一个:', this.prevSubmitId, '下一个:', this.nextSubmitId)
console.log('进度:', this.progress.reviewed, '/', this.progress.total)
```

---

## 测试建议

### 测试场景1：正常批改流程
1. 访问批改列表，有3个待批改报告
2. 点击"开始批改"第一个
3. 填写分数和评语，点击"保存并下一个"
4. 验证：
   - ✅ 自动跳转到第二个待批改报告
   - ✅ 进度显示正确（2/3）

### 测试场景2：批改完最后一个
1. 只剩1个待批改报告
2. 批改并点击"保存并下一个"
3. 验证：
   - ✅ 显示"🎉 恭喜！所有报告批改完成！"
   - ✅ 2秒后自动返回批改列表
   - ✅ 批改列表显示所有报告都已批改

### 测试场景3：中途保存
1. 批改报告，点击"保存"（不是"保存并下一个"）
2. 验证：
   - ✅ 提示"保存成功"
   - ✅ 上一个/下一个按钮状态更新
   - ✅ 如果当前报告已批改完成，导航按钮禁用

### 测试场景4：打回报告
1. 批改报告，点击"打回"
2. 填写打回原因并确认
3. 验证：
   - ✅ 报告状态变为"已打回"
   - ✅ 自动跳转到下一个待批改报告（如果有）
   - ✅ 如果没有下一个，返回列表

---

## 相关文件

**后端**：
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java`

**前端**：
- `ruoyi-ui/src/views/task/review-detail.vue`
- `ruoyi-ui/src/api/task/review.js`

---

## 总结

通过这次优化：
1. **批改列表更精准** - 只显示真正需要批改的报告
2. **批改流程更流畅** - 全自动跳转，无需手动操作
3. **完成提示更友好** - 表情符号+自动返回
4. **导航信息更准确** - 实时更新待批改列表

整体大幅提升了教师批改报告的效率和体验！
