<template>
  <div class="app-container review-index-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" ref="queryForm" :inline="true" class="search-form">
        <el-form-item label="任务名称">
          <el-input
            v-model="queryParams.taskName"
            placeholder="请输入任务名称"
            clearable
            style="width: 220px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input
            v-model="queryParams.courseName"
            placeholder="请输入课程名称"
            clearable
            style="width: 220px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right">
          <el-button
            type="success"
            icon="el-icon-download"
            :disabled="selectedTasks.length === 0"
            @click="handleBatchExport"
          >批量导出成绩 ({{ selectedTasks.length }})</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="taskList"
        stripe
        class="review-table"
        @selection-change="handleSelectionChange"
        ref="taskTable"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务名称" prop="taskName" :show-overflow-tooltip="true" min-width="200">
          <template slot-scope="scope">
            <div class="task-name">
              <i class="el-icon-document"></i>
              <span>{{ scope.row.taskName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="课程名称" prop="courseName" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="发布时间" align="center" prop="createTime" width="120">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" align="center" prop="deadline" width="160">
          <template slot-scope="scope">
            <div class="deadline-cell">
              <i class="el-icon-time"></i>
              <span>{{ parseTime(scope.row.deadline, '{y}-{m}-{d} {h}:{i}') }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交情况" align="center" width="140">
          <template slot-scope="scope">
            <div class="submit-info">
              <el-progress
                :percentage="getSubmitRateValue(scope.row)"
                :color="getSubmitColor(scope.row)"
                :stroke-width="16"
              >
                <template slot="default">
                  <span class="progress-text">{{ scope.row.submitCount || 0 }}/{{ scope.row.totalCount || 0 }}</span>
                </template>
              </el-progress>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="批改进度" align="center" width="140">
          <template slot-scope="scope">
            <div class="review-progress">
              <span class="pending-count">待批改：{{ scope.row.pendingCount || 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.status === '0'" type="warning" size="medium">未开始</el-tag>
            <el-tag v-else-if="scope.row.status === '1'" type="primary" size="medium">进行中</el-tag>
            <el-tag v-else-if="scope.row.status === '2'" type="success" size="medium">已结束</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template slot-scope="scope">
            <el-button
              type="primary"
              size="medium"
              icon="el-icon-edit-outline"
              @click="handleReview(scope.row)"
              class="review-btn"
            >批改报告</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <pagination
          v-show="total>0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="getList"
        />
      </div>
    </el-card>
  </div>
</template>

<script>
import { listTask } from "@/api/task/task"

export default {
  name: "ReviewIndex",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 总条数
      total: 0,
      // 任务列表
      taskList: [],
      // 选中的任务列表
      selectedTasks: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        taskName: undefined,
        courseName: undefined
      }
    }
  },
  created() {
    this.getList()
  },
  activated() {
    // 页面激活时刷新列表（从批改详情页面返回时）
    this.getList()
  },
  methods: {
    /** 查询任务列表 */
    getList() {
      this.loading = true
      listTask(this.queryParams).then(response => {
        this.taskList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 批改报告 */
    handleReview(row) {
      this.$router.push({
        path: `/task/review/${row.taskId}`,
        query: {
          taskName: row.taskName
        }
      })
    },
    /** 多选框选中数据 */
    handleSelectionChange(selection) {
      this.selectedTasks = selection
    },
    /** 批量导出成绩 */
    handleBatchExport() {
      if (this.selectedTasks.length === 0) {
        this.$modal.msgWarning('请至少选择一个任务')
        return
      }

      // 验证：检查是否为同一部门
      const deptIds = [...new Set(this.selectedTasks.map(task => task.deptId))]
      if (deptIds.length > 1) {
        this.$modal.msgError('只能导出同一部门的任务成绩')
        return
      }

      // 验证：检查是否为同一课程
      const courseNames = [...new Set(this.selectedTasks.map(task => task.courseName))]
      if (courseNames.length > 1) {
        this.$modal.msgError('只能导出同一课程的任务成绩')
        return
      }

      // 确认导出
      const taskNames = this.selectedTasks.map(t => t.taskName).join('、')
      const deptName = this.selectedTasks[0].deptName
      const courseName = this.selectedTasks[0].courseName

      this.$confirm(
        `将导出以下任务的成绩：<br/><br/>` +
        `<strong>部门：</strong>${deptName}<br/>` +
        `<strong>课程：</strong>${courseName}<br/>` +
        `<strong>任务：</strong>${taskNames}<br/><br/>` +
        `是否继续？`,
        '确认导出',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true
        }
      ).then(() => {
        // 获取选中的任务ID列表
        const taskIds = this.selectedTasks.map(t => t.taskId).join(',')

        // 调用导出接口
        this.download(
          '/Task/submit/batchExport',
          { taskIds: taskIds },
          `${courseName}-成绩登记表.xlsx`
        )

        this.$modal.msgSuccess('导出成功')
      }).catch(() => {})
    },
    /** 获取提交标签类型 */
    getSubmitTagType(row) {
      const rate = this.getSubmitRateValue(row)
      if (rate >= 80) return 'success'
      if (rate >= 50) return 'warning'
      return 'danger'
    },
    /** 获取提交颜色 */
    getSubmitColor(row) {
      const rate = this.getSubmitRateValue(row)
      if (rate >= 80) return '#67C23A'
      if (rate >= 50) return '#E6A23C'
      return '#F56C6C'
    },
    /** 获取提交率 */
    getSubmitRate(row) {
      const rate = this.getSubmitRateValue(row)
      return `${rate}%`
    },
    /** 获取提交率数值 */
    getSubmitRateValue(row) {
      const total = row.totalCount || 0
      const submitted = row.submitCount || 0
      return total > 0 ? Math.round((submitted / total) * 100) : 0
    },
    /** 获取批改进度 */
    getReviewProgress(row) {
      const submitted = row.submitCount || 0
      const reviewed = row.reviewedCount || 0
      return submitted > 0 ? Math.round((reviewed / submitted) * 100) : 0
    },
    /** 获取进度条颜色 */
    getProgressColor(row) {
      const progress = this.getReviewProgress(row)
      if (progress >= 100) return '#67C23A'
      if (progress >= 50) return '#E6A23C'
      return '#F56C6C'
    }
  }
}
</script>

<style scoped>
.review-index-container {
  background-color: #f0f2f5;
  min-height: calc(100vh - 84px);
}

/* 搜索卡片 */
.search-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.search-form {
  margin-bottom: -18px;
}

.search-form .el-form-item {
  margin-bottom: 18px;
}

/* 表格卡片 */
.table-card {
  border-radius: 8px;
}

.review-table {
  font-size: 14px;
}

/* 任务名称 */
.task-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-name i {
  color: #409EFF;
  font-size: 16px;
}

.task-name span {
  font-weight: 500;
}

/* 截止时间 */
.deadline-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.deadline-cell i {
  color: #E6A23C;
}

/* 提交情况 */
.submit-info {
  padding: 4px 0;
}

.progress-text {
  font-size: 12px;
  font-weight: 600;
}

/* 批改进度 */
.review-progress {
  padding: 4px 0;
}

.pending-count {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
}

/* 操作按钮 */
.review-btn {
  font-weight: 500;
}

/* 分页 */
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 进度条样式优化 */
.review-table ::v-deep .el-progress__text {
  font-size: 12px !important;
}

.review-table ::v-deep .el-progress-bar__inner {
  border-radius: 9px;
}

.review-table ::v-deep .el-progress-bar__outer {
  border-radius: 9px;
  background-color: #e4e7ed;
}

/* 标签样式 */
.review-table ::v-deep .el-tag {
  font-weight: 500;
}
</style>
