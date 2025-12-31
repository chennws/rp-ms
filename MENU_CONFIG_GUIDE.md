# 报告批改菜单配置说明

## 问题：点击批改报告跳转404

**原因**：批改相关的路由没有在路由配置中注册。

**解决方案**：已在 `router/index.js` 中添加路由配置。

## ✅ 已添加的路由

在 `constantRoutes` 中已添加：

```javascript
{
  path: '/task/review',
  component: Layout,
  hidden: true,
  children: [
    {
      path: ':taskId',
      component: () => import('@/views/task/review-list'),
      name: 'ReviewList',
      meta: { title: '批改列表', activeMenu: '/review' }
    },
    {
      path: ':taskId/:submitId',
      component: () => import('@/views/task/review-detail'),
      name: 'ReviewDetail',
      meta: { title: '批改详情', activeMenu: '/review' }
    }
  ]
}
```

## 📋 现在需要配置菜单

### 方式一：后台菜单管理配置（推荐）

1. **登录后台管理**
2. **进入菜单管理**：系统管理 > 菜单管理
3. **添加菜单**：点击"新增"按钮

**菜单配置**：

```
菜单名称：报告批改
父级菜单：主类目
菜单类型：菜单
菜单图标：edit
路由地址：review
组件路径：review/index
权限标识：task:task:add
菜单状态：正常
显示状态：显示
```

**详细字段说明**：

| 字段 | 值 | 说明 |
|------|-----|------|
| 菜单名称 | 报告批改 | 显示在侧边栏的名称 |
| 菜单类型 | 菜单 | 选择"菜单"类型 |
| 路由地址 | review | 不要加斜杠，系统会自动处理 |
| 组件路径 | review/index | 对应 `views/review/index.vue` |
| 权限标识 | task:task:add | 教师权限（有发布任务权限） |
| 显示排序 | 2 | 可以放在实验任务管理之后 |

4. **保存菜单**
5. **刷新页面**

### 方式二：直接修改数据库（不推荐）

如果后台配置不生效，可以直接在数据库中添加：

```sql
INSERT INTO sys_menu (
  menu_name,
  parent_id,
  order_num,
  path,
  component,
  is_frame,
  is_cache,
  menu_type,
  visible,
  status,
  perms,
  icon,
  create_by,
  create_time,
  update_by,
  update_time,
  remark
) VALUES (
  '报告批改',      -- 菜单名称
  0,              -- 父菜单ID（0表示主菜单）
  2,              -- 显示顺序
  'review',       -- 路由地址
  'review/index', -- 组件路径
  1,              -- 是否为外链（1否 0是）
  0,              -- 是否缓存（0缓存 1不缓存）
  'C',            -- 菜单类型（M目录 C菜单 F按钮）
  '0',            -- 显示状态（0显示 1隐藏）
  '0',            -- 菜单状态（0正常 1停用）
  'task:task:add',-- 权限标识
  'edit',         -- 菜单图标
  'admin',        -- 创建者
  NOW(),          -- 创建时间
  '',             -- 更新者
  NULL,           -- 更新时间
  '教师批改报告'   -- 备注
);
```

## 🔍 验证步骤

### 1. 检查路由是否生效

在浏览器开发者工具控制台输入：

```javascript
// 查看路由配置
console.log(this.$router.options.routes)

// 尝试手动跳转
this.$router.push('/task/review/1')
```

如果路由配置正确，应该能看到 `/task/review` 相关的路由。

### 2. 检查菜单权限

确保登录的教师账号有以下权限：
- `task:task:add` - 发布任务权限（用于批改报告菜单）
- `task:task:list` - 任务列表权限

### 3. 测试完整流程

**教师端测试**：

1. **刷新页面**（Ctrl+F5 强制刷新）
2. **查看左侧菜单**，应该能看到"报告批改"菜单
3. **点击"报告批改"**，进入任务列表页
4. **点击某个任务的"批改报告"按钮**
5. **检查URL**：
   - 批改列表页：`/task/review/1`（1是任务ID）
   - 批改详情页：`/task/review/1/2`（1是任务ID，2是提交ID）

## ⚠️ 常见问题

### Q1: 刷新页面后还是404？

**解决方案**：
1. 清除浏览器缓存（Ctrl+Shift+Delete）
2. 重新编译前端项目：
```bash
cd ruoyi-ui
npm run dev
```

### Q2: 菜单显示了，但点击后还是404？

**检查**：
1. 路由路径是否正确（不要多加斜杠）
2. 组件路径是否正确：`review/index`
3. 文件是否存在：`ruoyi-ui/src/views/review/index.vue`

### Q3: 提示"没有权限"？

**解决方案**：
1. 在角色管理中给教师角色分配"报告批改"菜单权限
2. 确保权限标识为：`task:task:add`

### Q4: 批改列表能打开，但批改详情404？

**检查**：
1. 确认路由参数路径：`/task/review/:taskId/:submitId`
2. 确认组件文件存在：`views/task/review-detail.vue`

## 📁 完整的路由结构

```
/ (主页)
├── /review (报告批改入口) ← 需要在菜单中配置
│   └── index.vue
├── /task (实验任务管理) ← 动态菜单
│   ├── index.vue
│   ├── /edit (在线完成) ← hidden路由
│   └── /review (批改子页面) ← hidden路由
│       ├── /:taskId (批改列表)
│       └── /:taskId/:submitId (批改详情)
```

## 🎯 最终效果

配置成功后，教师登录应该看到：

```
侧边栏菜单：
├── 首页
├── 实验任务管理
│   └── 任务列表
├── 报告批改 ← 新增菜单
└── 系统管理
    └── ...
```

点击"报告批改"后，能看到所有任务列表，点击"批改报告"进入批改流程。

---

**提示**：如果按照以上步骤配置后还是404，请检查：
1. 前端项目是否重新编译（npm run dev）
2. 浏览器缓存是否清除
3. 路由文件修改是否保存
4. 组件文件是否存在

如果还有问题，请提供：
1. 浏览器控制台的错误信息
2. 跳转时的完整URL
3. 网络请求的响应内容
