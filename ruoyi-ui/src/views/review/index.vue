<template>
  <div class="app-container review-index-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" ref="queryForm" :inline="true" class="search-form">
        <el-form-item label="部门">
          <el-input
            v-model="queryParams.deptName"
            placeholder="请输入部门名称"
            clearable
            style="width: 200px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="课程">
          <el-input
            v-model="queryParams.courseName"
            placeholder="请输入课程名称"
            clearable
            style="width: 200px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="任务">
          <el-input
            v-model="queryParams.taskName"
            placeholder="请输入任务名称"
            clearable
            style="width: 200px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right">
          <el-button
            type="warning"
            icon="el-icon-download"
            :disabled="selectedTasks.length === 0"
            @click="handleHorizontalSummaryExport"
          >汇总导出成绩 ({{ selectedTasks.length }})</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 树形任务列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="treeData"
        stripe
        row-key="id"
        :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
        :default-expand-all="false"
        class="review-table"
        @select="handleSelect"
        @select-all="handleSelectAll"
        ref="taskTable"
      >
        <!-- 选择框：所有层级都显示 -->
        <el-table-column type="selection" width="55" align="center" :selectable="canSelect" reserve-selection />

        <!-- 名称列 -->
        <el-table-column label="名称" min-width="300">
          <template slot-scope="scope">
            <!-- 部门级别 -->
            <div v-if="scope.row.type === 'dept'" class="dept-row">
              <i class="el-icon-office-building dept-icon"></i>
              <span class="dept-name">{{ scope.row.name }}</span>
              <el-tag type="info" size="small" class="stat-tag">{{ scope.row.totalTasks }}个任务</el-tag>
              <el-tag v-if="scope.row.pendingCount > 0" type="warning" size="small" class="stat-tag">待批改{{ scope.row.pendingCount }}</el-tag>
            </div>

            <!-- 课程级别 -->
            <div v-else-if="scope.row.type === 'course'" class="course-row">
              <i class="el-icon-notebook-2 course-icon"></i>
              <span class="course-name">{{ scope.row.name }}</span>
              <el-tag type="primary" size="small" class="stat-tag">{{ scope.row.totalTasks }}个任务</el-tag>
              <el-tag v-if="scope.row.pendingCount > 0" type="danger" size="small" class="stat-tag">待批改{{ scope.row.pendingCount }}</el-tag>
            </div>

            <!-- 任务级别 -->
            <div v-else class="task-row">
              <i class="el-icon-document task-icon"></i>
              <span class="task-name">{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <!-- 发布时间 -->
        <el-table-column label="发布时间" align="center" width="120">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'task'">{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>

        <!-- 截止时间 -->
        <el-table-column label="截止时间" align="center" width="160">
          <template slot-scope="scope">
            <div v-if="scope.row.type === 'task'" class="deadline-cell">
              <i class="el-icon-time"></i>
              <span>{{ parseTime(scope.row.deadline, '{y}-{m}-{d} {h}:{i}') }}</span>
            </div>
          </template>
        </el-table-column>

        <!-- 提交情况 -->
        <el-table-column label="提交情况" align="center" width="140">
          <template slot-scope="scope">
            <div v-if="scope.row.type === 'task'" class="submit-info">
              <el-progress
                :percentage="getSubmitRateValue(scope.row)"
                :color="getSubmitColor(scope.row)"
                :stroke-width="16"
              >
                <template slot="default">
                  <span class="progress-text">{{ scope.row.submitCount }}/{{ scope.row.totalCount }}</span>
                </template>
              </el-progress>
            </div>
          </template>
        </el-table-column>

        <!-- 待批改 -->
        <el-table-column label="待批改" align="center" width="100">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'task'" class="pending-count">
              <el-tag v-if="scope.row.pendingCount > 0" type="warning" size="medium">{{ scope.row.pendingCount }}</el-tag>
              <el-tag v-else type="success" size="medium">0</el-tag>
            </span>
          </template>
        </el-table-column>

        <!-- 状态 -->
        <el-table-column label="状态" align="center" width="100">
          <template slot-scope="scope">
            <div v-if="scope.row.type === 'task'">
              <el-tag v-if="scope.row.status === '0'" type="warning" size="medium">未开始</el-tag>
              <el-tag v-else-if="scope.row.status === '1'" type="primary" size="medium">进行中</el-tag>
              <el-tag v-else-if="scope.row.status === '2'" type="success" size="medium">已结束</el-tag>
            </div>
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column label="操作" align="center" width="160" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.type === 'task'"
              type="primary"
              size="medium"
              icon="el-icon-edit-outline"
              @click="handleReview(scope.row)"
              class="review-btn"
            >批改报告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { listTask } from "@/api/task/task"
import request from '@/utils/request'

export default {
  name: "ReviewIndex",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 树形数据
      treeData: [],
      // 原始数据（用于搜索）
      rawTreeData: [],
      // 选中的任务列表
      selectedTasks: [],
      // 查询参数
      queryParams: {
        deptName: undefined,
        courseName: undefined,
        taskName: undefined
      }
    }
  },
  created() {
    this.getTreeData()
  },
  activated() {
    // 页面激活时刷新列表（从批改详情页面返回时）
    this.getTreeData()
  },
  methods: {
    /** 查询树形数据 */
    getTreeData() {
      this.loading = true
      request({
        url: '/Task/tree',
        method: 'get',
        params: this.queryParams
      }).then(response => {
        this.rawTreeData = response.data
        this.treeData = this.filterTree(response.data)
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },

    /** 过滤树形数据（根据搜索条件） */
    filterTree(treeData) {
      if (!this.queryParams.deptName && !this.queryParams.courseName && !this.queryParams.taskName) {
        return treeData
      }

      const filtered = []
      for (const dept of treeData) {
        // 部门名称过滤
        if (this.queryParams.deptName && !dept.name.includes(this.queryParams.deptName)) {
          continue
        }

        const filteredCourses = []
        for (const course of (dept.children || [])) {
          // 课程名称过滤
          if (this.queryParams.courseName && !course.name.includes(this.queryParams.courseName)) {
            continue
          }

          const filteredTasks = []
          for (const task of (course.children || [])) {
            // 任务名称过滤
            if (this.queryParams.taskName && !task.name.includes(this.queryParams.taskName)) {
              continue
            }
            filteredTasks.push(task)
          }

          if (filteredTasks.length > 0) {
            filteredCourses.push({
              ...course,
              children: filteredTasks
            })
          }
        }

        if (filteredCourses.length > 0) {
          filtered.push({
            ...dept,
            children: filteredCourses
          })
        }
      }

      return filtered
    },

    /** 搜索按钮操作 */
    handleQuery() {
      this.treeData = this.filterTree(this.rawTreeData)
    },

    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.treeData = this.rawTreeData
    },

    /** 判断行是否可选（所有行都可选） */
    canSelect(row) {
      return true
    },

    /** 处理单行选择 */
    handleSelect(selection, row) {
      const isSelected = selection.includes(row)

      if (row.type === 'dept') {
        // 选中/取消部门，联动所有课程和任务
        this.toggleDeptSelection(row, isSelected)
      } else if (row.type === 'course') {
        // 选中/取消课程，联动所有任务
        this.toggleCourseSelection(row, isSelected)
      } else if (row.type === 'task') {
        // 任务被选中，更新selectedTasks
        this.updateSelectedTasks()
      }
    },

    /** 处理全选 */
    handleSelectAll(selection) {
      // Element UI的全选只会影响当前可见的根节点
      // 我们需要手动处理子节点
      const isSelectAll = selection.length > 0

      this.treeData.forEach(dept => {
        if (isSelectAll) {
          this.$refs.taskTable.toggleRowSelection(dept, true)
          this.toggleDeptSelection(dept, true)
        } else {
          this.$refs.taskTable.toggleRowSelection(dept, false)
          this.toggleDeptSelection(dept, false)
        }
      })
    },

    /** 切换部门选择状态 */
    toggleDeptSelection(dept, isSelected) {
      if (!dept.children) return

      dept.children.forEach(course => {
        this.$refs.taskTable.toggleRowSelection(course, isSelected)
        this.toggleCourseSelection(course, isSelected)
      })
    },

    /** 切换课程选择状态 */
    toggleCourseSelection(course, isSelected) {
      if (!course.children) return

      course.children.forEach(task => {
        this.$refs.taskTable.toggleRowSelection(task, isSelected)
      })

      // 更新选中的任务列表
      this.updateSelectedTasks()
    },

    /** 更新选中的任务列表 */
    updateSelectedTasks() {
      // 延迟执行，等待Element UI更新选中状态
      this.$nextTick(() => {
        const selection = this.$refs.taskTable.selection || []
        this.selectedTasks = selection.filter(item => item.type === 'task')
      })
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
      // 只保留任务级别的选中项
      this.selectedTasks = selection.filter(item => item.type === 'task')
    },

    /** 获取提交率数值 */
    getSubmitRateValue(row) {
      const total = row.totalCount || 0
      const submitted = row.submitCount || 0
      return total > 0 ? Math.round((submitted / total) * 100) : 0
    },

    /** 获取提交颜色 */
    getSubmitColor(row) {
      const rate = this.getSubmitRateValue(row)
      if (rate >= 80) return '#67C23A'
      if (rate >= 50) return '#E6A23C'
      return '#F56C6C'
    },
    /** 横向汇总导出成绩(只导出成绩,不导出评语,根据选择的实验只填写对应的列) */
    handleHorizontalSummaryExport() {
      if (this.selectedTasks.length === 0) {
        this.$modal.msgWarning('请至少选择一个任务')
        return
      }

      // 验证：检查是否为同一班级（同一部门）
      const deptIds = [...new Set(this.selectedTasks.map(task => task.deptId))]
      if (deptIds.length > 1) {
        this.$modal.msgError('只能导出同一班级的任务成绩')
        return
      }

      // 验证：检查是否为同一课程
      const courseNames = [...new Set(this.selectedTasks.map(task => task.courseName))]
      if (courseNames.length > 1) {
        this.$modal.msgError('只能导出同一课程的任务成绩')
        return
      }

      // 验证：最多选择6个任务
      if (this.selectedTasks.length > 6) {
        this.$modal.msgError('最多只能选择6个任务进行汇总导出')
        return
      }

      // 确认导出
      const taskNames = this.selectedTasks.map(t => t.taskName).join('、')
      const deptName = this.selectedTasks[0].deptName
      const courseName = this.selectedTasks[0].courseName

      this.$confirm(
        `将导出以下任务的成绩汇总表：<br/><br/>` +
        `<strong>班级：</strong>${deptName}<br/>` +
        `<strong>课程：</strong>${courseName}<br/>` +
        `<strong>任务：</strong>${taskNames}<br/><br/>` +
        `<strong style="color: #E6A23C;">注意：汇总导出只包含成绩,不包含评语</strong><br/><br/>` +
        `是否继续？`,
        '确认汇总导出',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true
        }
      ).then(() => {
        // 获取选中的任务ID列表
        const taskIds = this.selectedTasks.map(t => t.taskId).join(',')

        // 调用导出接口（download方法会自动处理成功和失败的提示）
        this.download(
          '/Task/submit/horizontalSummaryExport',
          { taskIds: taskIds },
          `${courseName}-成绩汇总表.xlsx`
        )
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

/* 树形表格样式 */

/* 部门行 */
.dept-row {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-weight: bold;
  font-size: 15px;
  color: #303133;
  vertical-align: middle;
}

.dept-icon {
  font-size: 20px;
  color: #409EFF;
}

.dept-name {
  font-weight: 600;
}

/* 课程行 */
.course-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 14px;
  color: #606266;
  vertical-align: middle;
}

.course-icon {
  font-size: 18px;
  color: #67C23A;
}

.course-name {
  font-weight: 500;
}

/* 任务行 */
.task-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
  vertical-align: middle;
}

.task-icon {
  font-size: 16px;
  color: #909399;
}

.task-name {
  font-weight: normal;
}

/* 统计标签 */
.stat-tag {
  margin-left: 8px;
  font-size: 12px;
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

/* 待批改数量 */
.pending-count {
  font-size: 14px;
  font-weight: 600;
}

/* 操作按钮 */
.review-btn {
  font-weight: 500;
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

/* 树形表格行高优化 */
.review-table ::v-deep .el-table__row {
  height: 50px;
}

/* 部门和课程行背景色 */
.review-table ::v-deep .el-table__row[class*="dept"] {
  background-color: #f5f7fa;
}

.review-table ::v-deep .el-table__row[class*="course"] {
  background-color: #fafafa;
}

/* 树形展开图标样式 */
.review-table ::v-deep .el-table__expand-icon {
  font-size: 14px;
  color: #409EFF;
  margin-right: 8px;
}

.review-table ::v-deep .el-table__expand-icon--expanded {
  transform: rotate(90deg);
}

/* 树形缩进样式 - 确保层级清晰 */
.review-table ::v-deep .el-table__indent {
  padding-left: 20px;
}

/* 确保展开图标和内容之间有适当间距 */
.review-table ::v-deep .el-table__placeholder {
  display: inline-block;
  width: 20px;
}

/* 调整名称列的内边距，为展开图标留出空间 */
.review-table ::v-deep td:first-child .cell {
  padding-left: 10px;
}
</style>
