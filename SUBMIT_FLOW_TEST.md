# 提交流程测试指南

## 测试步骤

### 1. 准备工作
- ✅ 执行数据库迁移SQL（添加 document_key 和 submit_pending 字段）
- ✅ 重启后端服务
- ✅ 刷新前端页面

### 2. 测试提交流程

#### 步骤1: 学生打开任务
1. 使用学生账号登录
2. 点击任务的"在线完成"按钮
3. **预期效果**：
   - 创建副本文件
   - 编辑器正常加载
   - 可以编辑文档

#### 步骤2: 编辑文档
1. 在编辑器中进行一些修改
2. 等待自动保存（可以通过浏览器控制台查看网络请求）

#### 步骤3: 提交任务
1. 点击右上角"提交任务"按钮
2. 点击确认
3. **预期效果**：
   - ✅ 立即显示全屏loading："正在保存到服务器，请稍候..."
   - ✅ 按钮进入loading状态（防止重复点击）
   - ✅ 后端日志显示：
     ```
     OnlyOffice保存命令发送成功，设置提交中状态
     已标记为提交中, taskId: {taskId}, userId: {userId}
     ```

#### 步骤4: 等待callback保存
1. 等待1-3秒（取决于网络和文件大小）
2. **预期效果**：
   - ✅ 后端日志显示OnlyOffice回调：
     ```
     文档保存成功objectName:{objectName},bucket:{bucketName}
     从文件名解析出 taskId: {taskId}, userId: {userId}
     任务提交成功，已更新submit_time, taskId: {taskId}, userId: {userId}
     ```
   - ✅ 数据库中 `submit_pending` 变为 0，`submit_time` 有值
   - ✅ 前端检测到成功状态
   - ✅ loading关闭
   - ✅ 显示"提交成功"提示
   - ✅ 1.5秒后自动返回任务列表

#### 步骤5: 验证提交记录
1. 在任务列表查看提交状态
2. 教师端查看学生提交记录
3. **预期效果**：
   - ✅ 提交时间正确
   - ✅ 文件可以下载

### 3. 异常情况测试

#### 测试1: 网络延迟
- **模拟**：在浏览器开发者工具中设置网络节流
- **预期**：loading显示更长时间，但最终成功

#### 测试2: 超时
- **模拟**：停止OnlyOffice服务或断开网络
- **预期**：
  - 60秒后显示"保存超时，请稍后刷新页面查看提交状态"
  - loading关闭
  - 按钮恢复可点击

#### 测试3: 重复提交
- **操作**：快速多次点击"提交任务"
- **预期**：
  - 第一次提交后按钮disabled
  - 无法重复提交

#### 测试4: 提交后关闭页面
- **操作**：点击提交后立即关闭浏览器
- **预期**：
  - callback仍会执行
  - 文件仍会保存到MinIO
  - 提交时间会更新
  - 下次打开任务列表能看到已提交

### 4. 日志检查

#### 前端控制台日志（按顺序）：
```
开始提交任务, taskId: 1, documentKey: xxx, fileUrl: xxx
提交响应: {code: 200, msg: "正在保存，请稍候..."}
正在保存中，继续等待...
正在保存中，继续等待...
提交状态: {code: 200, status: "success", message: "提交成功"}
```

#### 后端日志（按顺序）：
```
[ExpTaskController] 开始触发OnlyOffice保存文档, documentKey: xxx
[ExpTaskController] OnlyOffice保存响应: OfficeResponse(error=0)
[ExpTaskController] OnlyOffice保存命令发送成功，设置提交中状态
[ExpTaskController] 已标记为提交中, taskId: 1, userId: 2

[CallbackServiceImpl] 文档保存成功objectName: /2025/12/30/submit_1_2_xxx.docx, bucket: winter
[CallbackServiceImpl] 从文件名解析出 taskId: 1, userId: 2
[CallbackServiceImpl] 任务提交成功，已更新submit_time, taskId: 1, userId: 2
```

### 5. 数据库验证

查询提交记录：
```sql
SELECT task_id, user_id, submit_pending, submit_time, status
FROM exp_task_submit
WHERE task_id = {taskId} AND user_id = {userId};
```

**预期结果**：
- `submit_pending = 0`
- `submit_time` 为最新时间
- `status = '0'`（待批阅）

## 常见问题排查

### 问题1: loading一直显示，不消失
**排查**：
1. 检查后端日志，看callback是否被调用
2. 检查NATAPP是否正常运行
3. 检查回调URL配置是否正确

### 问题2: 显示"提交成功"但文件未更新
**排查**：
1. 检查MinIO中文件的最后修改时间
2. 检查callback日志
3. 确认文件名格式是否正确

### 问题3: 超时但文件已保存
**说明**：这是正常的，可能是网络延迟或callback延迟
**解决**：刷新页面查看提交状态

## 成功标准

✅ 所有步骤顺利完成
✅ 日志输出符合预期
✅ 数据库状态正确
✅ 用户体验流畅
✅ 异常情况处理得当
