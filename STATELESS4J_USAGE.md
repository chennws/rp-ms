# Stateless4j 状态机使用文档

## 📋 概述

本项目使用 **stateless4j** 实现实验报告的状态流转管理，提供清晰的状态转换逻辑和防止非法操作。

## 🎯 状态定义

### 状态列表（ReportState）

| 状态代码 | 状态名称 | 说明 |
|---------|---------|------|
| 0 | 草稿（DRAFT） | 学生尚未提交 |
| 1 | 已提交（SUBMITTED） | 学生已提交，待教师批阅 |
| 2 | 批阅中（REVIEWING） | 教师正在批阅 |
| 3 | 已批阅（REVIEWED） | 教师批阅通过 |
| 4 | 已打回（REJECTED） | 教师打回，需要学生修改 |
| 5 | 重新提交（RESUBMITTED） | 学生修改后重新提交 |
| 6 | 已归档（ARCHIVED） | 报告已归档（终态） |

### 触发器列表（ReportTrigger）

| 触发器 | 说明 | 执行者 |
|--------|------|--------|
| SUBMIT | 提交报告 | 学生 |
| START_REVIEW | 开始批阅 | 教师 |
| APPROVE | 批阅通过 | 教师 |
| REJECT | 打回报告 | 教师 |
| RESUBMIT | 重新提交 | 学生 |
| ARCHIVE | 归档 | 教师/系统 |

## 📊 状态流转图

```
[草稿]
  ↓ (SUBMIT - 学生提交)
[已提交]
  ↓ (START_REVIEW - 教师开始批阅)
[批阅中]
  ├─→ (APPROVE - 批阅通过) → [已批阅] → (ARCHIVE) → [已归档]
  └─→ (REJECT - 打回) → [已打回] → (RESUBMIT - 重新提交) → [重新提交] → (START_REVIEW) → [批阅中]
```

## 💻 使用方式

### 1. 后端接口

#### 打回报告
```http
POST /Task/submit/reject/{submitId}?reason=打回原因
```

**参数**：
- `submitId`: 提交记录ID
- `reason`: 打回原因（必填）

**返回**：
```json
{
  "code": 200,
  "msg": "已打回"
}
```

#### 获取允许的操作列表
```http
GET /Task/submit/actions/{submitId}
```

**返回**：
```json
{
  "code": 200,
  "data": ["START_REVIEW", "REJECT"]
}
```

#### 检查是否允许操作
```http
GET /Task/submit/canFire/{submitId}/{trigger}
```

**示例**：`GET /Task/submit/canFire/1/REJECT`

**返回**：
```json
{
  "code": 200,
  "data": true
}
```

### 2. 前端集成

#### 在批改详情页添加打回按钮

```vue
<template>
  <div class="review-actions">
    <!-- 现有的保存、下一个按钮 -->

    <!-- 新增：打回按钮 -->
    <el-button
      v-if="canReject"
      type="warning"
      icon="el-icon-refresh-left"
      @click="handleReject"
    >打回报告</el-button>
  </div>
</template>

<script>
export default {
  data() {
    return {
      canReject: false,
      rejectDialogVisible: false,
      rejectReason: ''
    }
  },
  mounted() {
    this.checkPermissions()
  },
  methods: {
    // 检查是否可以打回
    async checkPermissions() {
      const res = await this.$http.get(
        `/Task/submit/canFire/${this.submitId}/REJECT`
      )
      this.canReject = res.data
    },

    // 打回报告
    handleReject() {
      this.$prompt('请输入打回原因', '打回报告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '打回原因不能为空'
      }).then(({ value }) => {
        this.rejectReport(value)
      })
    },

    // 调用打回接口
    async rejectReport(reason) {
      try {
        await this.$http.post(
          `/Task/submit/reject/${this.submitId}?reason=${reason}`
        )
        this.$message.success('已打回')
        this.$router.back()
      } catch (error) {
        this.$message.error('打回失败：' + error.message)
      }
    }
  }
}
</script>
```

#### 在列表页显示状态

```vue
<el-table-column label="状态" align="center">
  <template slot-scope="scope">
    <el-tag v-if="scope.row.status === '0'" type="info">草稿</el-tag>
    <el-tag v-else-if="scope.row.status === '1'" type="primary">已提交</el-tag>
    <el-tag v-else-if="scope.row.status === '2'" type="warning">批阅中</el-tag>
    <el-tag v-else-if="scope.row.status === '3'" type="success">已批阅</el-tag>
    <el-tag v-else-if="scope.row.status === '4'" type="danger">已打回</el-tag>
    <el-tag v-else-if="scope.row.status === '5'" type="primary">重新提交</el-tag>
    <el-tag v-else-if="scope.row.status === '6'" type="info">已归档</el-tag>
  </template>
</el-table-column>
```

#### 显示打回原因

```vue
<el-table-column label="打回原因" prop="rejectReason" :show-overflow-tooltip="true">
  <template slot-scope="scope">
    <span v-if="scope.row.rejectReason" style="color: #F56C6C;">
      {{ scope.row.rejectReason }}
    </span>
    <span v-else style="color: #909399;">-</span>
  </template>
</el-table-column>
```

### 3. Java 代码示例

#### 在 Service 中使用

```java
@Service
public class TaskSubmitService {

    @Autowired
    private ReportStateMachineService stateMachineService;

    // 提交报告
    public void submitReport(Long submitId) {
        stateMachineService.submitReport(submitId);
    }

    // 批阅通过
    public void approveReport(Long submitId) {
        stateMachineService.approve(submitId);
    }

    // 打回报告
    public void rejectReport(Long submitId, String reason) {
        stateMachineService.reject(submitId, reason);
    }

    // 检查是否可以打回
    public boolean canReject(Long submitId) {
        return stateMachineService.canFire(submitId, ReportTrigger.REJECT);
    }
}
```

## 🔧 配置说明

### Maven 依赖

已在 `ruoyi-system/pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.github.oxo42</groupId>
    <artifactId>stateless4j</artifactId>
    <version>2.6.0</version>
</dependency>
```

### 数据库配置

执行 `sql/add_report_state_machine.sql` 添加必要字段：

- `reject_reason` - 打回原因
- `submit_count` - 提交次数
- `status` 字段注释更新

## 📝 业务场景示例

### 场景1：教师批阅流程

```
1. 学生提交报告（状态: 草稿 → 已提交）
   触发器：SUBMIT

2. 教师开始批阅（状态: 已提交 → 批阅中）
   触发器：START_REVIEW

3. 教师批阅通过（状态: 批阅中 → 已批阅）
   触发器：APPROVE

4. 系统自动归档（状态: 已批阅 → 已归档）
   触发器：ARCHIVE
```

### 场景2：打回修改流程

```
1. 学生提交报告（状态: 草稿 → 已提交）
   触发器：SUBMIT

2. 教师开始批阅（状态: 已提交 → 批阅中）
   触发器：START_REVIEW

3. 教师打回报告（状态: 批阅中 → 已打回）
   触发器：REJECT
   原因：报告格式不规范，请重新提交

4. 学生修改后重新提交（状态: 已打回 → 重新提交）
   触发器：RESUBMIT

5. 教师再次批阅（状态: 重新提交 → 批阅中）
   触发器：START_REVIEW

6. 教师批阅通过（状态: 批阅中 → 已批阅）
   触发器：APPROVE
```

## ⚠️ 注意事项

### 1. 非法状态转换

状态机会自动阻止非法的状态转换，例如：

❌ **不允许**：
- 从"草稿"直接跳到"已批阅"
- 从"已归档"回退到任何状态
- 从"已提交"直接打回（需先进入"批阅中"）

✅ **允许**：
- 从"草稿"到"已提交"
- 从"批阅中"到"已批阅"或"已打回"
- 从"已打回"到"重新提交"

### 2. 异常处理

所有状态转换都包裹在事务中，如果转换失败会自动回滚：

```java
@Transactional(rollbackFor = Exception.class)
public boolean fire(Long submitId, ReportTrigger trigger) {
    // 转换逻辑
}
```

### 3. 日志记录

所有状态转换都会自动记录日志：

```
INFO  - 状态转换: REVIEWING --[REJECT]--> REJECTED
```

## 🎨 状态图导出

Stateless4j 支持导出 DOT 格式的状态图，可以使用 Graphviz 可视化：

```java
String dotGraph = UmlDotGraph.format(stateMachineConfig.toString());
System.out.println(dotGraph);
```

## 📚 参考资料

- [Stateless4j GitHub](https://github.com/oxo42/stateless4j)
- [状态机设计模式](https://refactoring.guru/design-patterns/state)

---

**版本**：v1.0
**更新时间**：2025-12-30
**作者**：Claude Code
