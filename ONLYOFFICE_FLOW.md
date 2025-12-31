# OnlyOffice 在线编辑完整流程说明

## 一、整体流程概览

```
用户点击"在线完成" 
  ↓
前端路由跳转到 /task/edit
  ↓
前端调用 /Task/config 接口获取编辑器配置
  ↓
后端生成配置（包含文档URL、回调地址等）
  ↓
前端初始化 OnlyOffice 编辑器
  ↓
OnlyOffice 文档服务器下载文档
  ↓
文档加载完成，触发 onDocumentReady 事件
  ↓
用户编辑文档
  ↓
OnlyOffice 自动保存，调用回调接口 /Task/callback
  ↓
后端处理回调，保存文档到 MinIO
  ↓
用户点击"提交任务"
  ↓
前端调用 /Task/submit 接口
```

## 二、详细流程说明

### 1. 前端初始化阶段

**文件**: `ruoyi-ui/src/views/task/edit.vue`

#### 1.1 页面加载 (`created` 钩子)
- 从路由参数获取 `taskId`、`taskName`、`fileUrl`
- 验证参数完整性
- 验证文件URL格式

#### 1.2 编辑器初始化 (`mounted` 钩子)
- 调用 `initEditor()` 方法
- 动态加载 OnlyOffice API 脚本
- 脚本加载成功后调用 `createEditor()`

### 2. 获取编辑器配置

**前端请求**:
```javascript
GET /dev-api/Task/config?fileUrl={fileUrl}&mode=EDIT
```

**后端处理** (`ExpTaskController.getConfig`):
- 接收 `fileUrl` 和 `mode` 参数
- 调用 `ConfigService.createConfig()` 生成配置

**配置生成过程** (`ConfigServiceImpl.createConfig`):

1. **确定文档类型** (`getDocumentType`)
   - 从 URL 或 MinIO 获取文件扩展名
   - 判断文档类型：WORD / SLIDE / CELL

2. **生成文档配置** (`getDocument`)
   - 提取文档名称
   - **生成文档 Key**（重要！）
     - 从 MinIO 获取文件最后修改时间
     - 使用 `SHA256(objectName + timestamp)` 生成唯一 Key
     - Key 用于 OnlyOffice 缓存机制
   - 设置文档下载 URL（MinIO 文件地址）
   - 设置文档权限

3. **生成编辑器配置** (`getEditorConfig`)
   - 设置编辑模式（EDIT / VIEW）
   - 设置用户信息
   - **设置回调地址**（重要！）
     ```
     callbackUrl = http://localhost:8080/Task/callback?fileUrl={encodedFileUrl}
     ```
   - 设置界面自定义选项

**返回的配置结构**:
```json
{
  "width": "100%",
  "height": "100%",
  "type": "desktop",
  "documentType": "word",
  "document": {
    "fileType": "docx",
    "key": "sha256_hash_value",
    "title": "文件名.docx",
    "url": "http://47.115.163.152:10001/winter/2025/12/27/xxx.docx",
    "info": {...},
    "permissions": {...}
  },
  "editorConfig": {
    "mode": "edit",
    "callbackUrl": "http://localhost:8080/Task/callback?fileUrl=...",
    "user": {...},
    "customization": {...}
  }
}
```

### 3. 创建编辑器实例

**前端处理** (`createEditor` 方法):

1. **验证 API 已加载**
   ```javascript
   if (!window.DocsAPI || !window.DocsAPI.DocEditor) {
     // 错误处理
   }
   ```

2. **获取配置**
   ```javascript
   getConfig(this.fileUrl, 'edit').then(response => {
     const config = response.data
   })
   ```

3. **初始化事件监听** (`initEvents`)
   - `onAppReady`: 应用程序加载完成
   - `onDocumentReady`: **文档加载完成**（关键事件）
   - `onDocumentStateChange`: 文档状态改变
   - `onError`: 错误处理
   - `onOutdatedVersion`: 版本过期处理
   - `onMetaChange`: 元信息改变
   - `onDownloadAs`: 下载事件

4. **创建编辑器**
   ```javascript
   this.editor = new window.DocsAPI.DocEditor('onlyoffice', config)
   ```

### 4. 文档加载过程

1. **OnlyOffice 文档服务器接收配置**
   - 解析 `document.url`（文档下载地址）
   - 使用 `document.key` 检查缓存

2. **下载文档**
   - OnlyOffice 服务器从 MinIO 下载文档
   - URL: `http://47.115.163.152:10001/winter/.../xxx.docx`

3. **文档转换**
   - OnlyOffice 将文档转换为可编辑格式
   - 加载到浏览器编辑器

4. **触发事件**
   - `onAppReady`: 应用程序就绪
   - `onDocumentReady`: **文档加载完成** ← 此时 `loading = false`

### 5. 文档编辑和保存

#### 5.1 用户编辑文档
- 在浏览器中直接编辑
- OnlyOffice 自动保存（根据配置）

#### 5.2 自动保存触发回调

**OnlyOffice 调用回调接口**:
```
POST /Task/callback?fileUrl={encodedFileUrl}
Content-Type: application/json

{
  "key": "文档key",
  "status": 2,  // 2=保存完成
  "url": "http://onlyoffice-server/temp/xxx.docx",  // 保存后的文档URL
  "changesurl": "...",  // 变更记录URL
  "history": {...},
  "users": [...],
  "actions": [...]
}
```

**后端处理** (`ExpTaskController.callback`):

1. **接收回调数据**
   ```java
   @PostMapping("/callback")
   public String callback(
       @RequestParam("fileUrl") String fileUrl,
       @RequestBody Callback body
   )
   ```

2. **处理回调** (`CallbackService.processCallback`)
   - 解析回调数据
   - 从 OnlyOffice 服务器下载保存后的文档
   - 上传到 MinIO，替换原文件
   - 更新文件版本

3. **返回响应**
   ```json
   {"error": "0"}  // 成功
   ```

### 6. 提交任务

**前端调用**:
```javascript
POST /dev-api/Task/submit
{
  "taskId": 13,
  "fileUrl": "http://..."
}
```

**后端处理**:
- 更新任务状态
- 记录提交时间
- 关联文档文件

## 三、关键配置说明

### 1. 文档服务器地址
- **前端**: `http://47.115.163.152:9001/web-apps/apps/api/documents/api.js`
- **后端**: `http://47.115.163.152:9000/` (docservice.url)

⚠️ **注意**: 前端和后端配置的端口可能不同，需要保持一致！

### 2. 回调地址
- **配置位置**: `application.yml` → `docservice.callback`
- **当前值**: `http://localhost:8080/Task/callback`
- **注意**: 生产环境需要修改为实际部署地址

### 3. 文档 Key 生成策略
- **目的**: 用于 OnlyOffice 缓存机制
- **生成方式**: `SHA256(objectName + lastModifiedTimestamp)`
- **重要性**: 
  - 相同文件相同版本 → 相同 Key → 使用缓存
  - 文件更新后 → Key 改变 → 重新下载

## 四、常见问题排查

### 1. 文档加载不出来

**检查项**:
1. ✅ OnlyOffice API 脚本是否加载成功
2. ✅ `/Task/config` 接口是否返回正确配置
3. ✅ 文档 URL 是否可访问（MinIO 文件是否存在）
4. ✅ 文档服务器是否正常运行
5. ✅ 浏览器控制台是否有错误信息
6. ✅ `onDocumentReady` 事件是否触发

**调试方法**:
```javascript
// 在浏览器控制台检查
console.log('DocsAPI:', window.DocsAPI)
console.log('DocEditor:', window.DocsAPI?.DocEditor)
console.log('编辑器实例:', this.editor)
```

### 2. 回调接口不工作

**检查项**:
1. ✅ 回调地址配置是否正确
2. ✅ OnlyOffice 服务器是否能访问回调地址
3. ✅ 后端日志是否有回调请求记录
4. ✅ 文件上传到 MinIO 是否成功

### 3. 事件不触发

**可能原因**:
- 事件配置不正确
- 编辑器创建失败
- 文档加载失败
- 网络问题

**解决方法**:
- 检查浏览器控制台日志
- 确认所有事件都已配置
- 检查 `onError` 事件是否有错误信息

## 五、代码修复说明

### 修复内容

1. **完善事件处理** (`initEvents` 方法)
   - 添加了所有必要的事件监听
   - 改进了错误处理逻辑
   - 添加了详细的日志输出

2. **修复容器显示问题**
   - 将 `v-show` 改为 `:style` 动态控制
   - 确保容器元素始终存在（DOM 中）

3. **改进编辑器创建流程**
   - 添加容器元素验证
   - 添加超时检查机制
   - 改进错误提示信息

4. **添加调试日志**
   - 所有关键步骤都有日志输出
   - 便于排查问题

### 关键代码位置

- **前端事件处理**: `ruoyi-ui/src/views/task/edit.vue` → `initEvents()`
- **后端配置生成**: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/ConfigServiceImpl.java`
- **回调处理**: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/ExpTaskController.java` → `callback()`

## 六、测试步骤

1. **启动后端服务**
   ```bash
   cd ruoyi-admin
   mvn spring-boot:run
   ```

2. **启动前端服务**
   ```bash
   cd ruoyi-ui
   npm run dev
   ```

3. **测试流程**
   - 登录系统
   - 进入任务列表
   - 点击"在线完成"按钮
   - 检查浏览器控制台日志
   - 确认文档是否加载
   - 编辑文档并保存
   - 检查回调是否触发
   - 提交任务

4. **检查日志**
   - 前端控制台：查看事件触发情况
   - 后端日志：查看配置生成和回调处理

