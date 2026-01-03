<template>
  <div class="app-container">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator=">" style="margin-bottom: 20px;">
      <el-breadcrumb-item :to="{ path: '/task' }">任务列表</el-breadcrumb-item>
      <el-breadcrumb-item>{{ taskName }} - 批改列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 任务信息卡片 -->
    <el-card shadow="never" style="margin-bottom: 20px;">
      <div slot="header" class="clearfix">
        <span style="font-weight: bold;">📋 任务信息</span>
      </div>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="info-item">
            <span class="label">任务名称：</span>
            <span class="value">{{ taskInfo.taskName }}</span>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="info-item">
            <span class="label">截止时间：</span>
            <span class="value">{{ parseTime(taskInfo.deadline, '{y}-{m}-{d} {h}:{i}') }}</span>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="info-item">
            <span class="label">课程名称：</span>
            <span class="value">{{ taskInfo.courseName }}</span>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 15px;">
        <el-col :span="6">
          <el-statistic title="提交情况" :value="statistics.submitted">
            <template slot="suffix">/ {{ statistics.total }}</template>
            <template slot="prefix">
              <i class="el-icon-upload" style="color: #409EFF;"></i>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="批改进度" :value="statistics.reviewed">
            <template slot="suffix">/ {{ statistics.submitted }}</template>
            <template slot="prefix">
              <i class="el-icon-check" style="color: #67C23A;"></i>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="待批改" :value="statistics.pending">
            <template slot="prefix">
              <i class="el-icon-time" style="color: #E6A23C;"></i>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="平均分" :value="statistics.avgScore" :precision="1">
            <template slot="prefix">
              <i class="el-icon-trophy" style="color: #F56C6C;"></i>
            </template>
          </el-statistic>
        </el-col>
      </el-row>
    </el-card>

    <!-- 筛选和搜索 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item label="状态筛选">
        <el-radio-group v-model="queryParams.status" @change="handleQuery">
          <el-radio-button label="">全部({{ statistics.total }})</el-radio-button>
          <el-radio-button :label="ReportState.SUBMITTED">待批改({{ statistics.pending }})</el-radio-button>
          <el-radio-button :label="ReportState.REVIEWED">已批改({{ statistics.reviewed }})</el-radio-button>
          <el-radio-button label="unsubmit">未提交({{ statistics.unsubmitted }})</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="搜索" style="float: right;">
        <el-input
          v-model="queryParams.keyword"
          placeholder="搜索学号或姓名"
          clearable
          style="width: 250px;"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item style="float: right;">
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 提交列表 -->
    <el-table v-loading="loading" :data="submitList" stripe>
      <el-table-column label="学号" prop="userName" width="120" />
      <el-table-column label="姓名" prop="nickName" width="120" />
      <el-table-column label="提交时间" align="center" width="180">
        <template slot-scope="scope">
          <span v-if="scope.row.submitTime">
            {{ parseTime(scope.row.submitTime, '{y}-{m}-{d} {h}:{i}') }}
          </span>
          <span v-else style="color: #999;">未提交</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="120">
        <template slot-scope="scope">
          <el-tag v-if="!scope.row.submitTime" type="info" size="small">❌ 未提交</el-tag>
          <el-tag v-else :type="getStateType(scope.row.status)" size="small">{{ getStateDesc(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分数" align="center" width="100">
        <template slot-scope="scope">
          <span v-if="scope.row.score !== null && scope.row.score !== undefined" style="font-weight: bold; color: #409EFF;">
            {{ scope.row.score }}
          </span>
          <span v-else style="color: #999;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="教师评语" prop="teacherRemark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="150">
        <template slot-scope="scope">
          <!-- 已提交、重新提交：显示"开始批改" -->
          <el-button
            v-if="scope.row.submitTime && (scope.row.status === ReportState.SUBMITTED || scope.row.status === ReportState.RESUBMITTED)"
            size="mini"
            type="primary"
            @click="handleStartReview(scope.row)"
          >开始批改</el-button>
          <!-- 批阅中：显示"继续批改" -->
          <el-button
            v-if="scope.row.submitTime && scope.row.status === ReportState.REVIEWING"
            size="mini"
            type="warning"
            @click="handleStartReview(scope.row)"
          >继续批改</el-button>
          <!-- 已批阅、已归档：显示"查看报告" -->
          <el-button
            v-if="scope.row.submitTime && (scope.row.status === ReportState.REVIEWED || scope.row.status === ReportState.ARCHIVED)"
            size="mini"
            type="text"
            @click="handleViewReport(scope.row)"
          >查看报告</el-button>
          <!-- 未提交 -->
          <span v-if="!scope.row.submitTime" style="color: #999;">-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 底部操作 -->
    <div style="margin-top: 20px;">
      <el-button type="default" icon="el-icon-back" @click="handleBack">返回任务列表</el-button>
      <el-button type="success" icon="el-icon-download" @click="handleExportGrades">批量导出成绩</el-button>
    </div>
  </div>
</template>

<script>
import { getSubmitList, getTaskInfo, exportGrades } from "@/api/task/review"
import { startReview } from "@/api/task/stateMachine"
import { ReportState, getStateDesc, getStateType } from "@/constants/reportState"

export default {
  name: "ReviewList",
  data() {
    return {
      // 引入状态常量
      ReportState,
      // 任务ID
      taskId: undefined,
      // 任务名称
      taskName: '',
      // 任务信息
      taskInfo: {},
      // 提交列表
      submitList: [],
      // 统计信息
      statistics: {
        total: 0,
        submitted: 0,
        reviewed: 0,
        pending: 0,
        unsubmitted: 0,
        avgScore: 0
      },
      // 加载状态
      loading: false,
      // 总条数
      total: 0,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        status: '', // 状态筛选：''全部, '0'待批改, '1'已批改, 'unsubmit'未提交
        keyword: '' // 搜索关键字
      }
    }
  },
  created() {
    // 从路由参数获取任务ID和名称
    this.taskId = this.$route.params.taskId
    this.taskName = this.$route.query.taskName || ''

    if (!this.taskId) {
      this.$modal.msgError("任务ID不能为空")
      this.$router.back()
      return
    }

    // 加载数据
    this.loadTaskInfo()
    this.getList()
  },
  activated() {
    // 页面激活时刷新列表（从批改详情页面返回时）
    this.loadTaskInfo()
    this.getList()
  },
  methods: {
    /** 加载任务信息 */
    loadTaskInfo() {
      getTaskInfo(this.taskId).then(response => {
        this.taskInfo = response.data
        this.taskName = this.taskInfo.taskName
      }).catch(() => {
        this.$modal.msgError("获取任务信息失败")
      })
    },
    /** 加载提交列表 */
    getList() {
      this.loading = true
      const params = {
        pageNum: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize,
        status: this.queryParams.status,
        keyword: this.queryParams.keyword
      }

      getSubmitList(this.taskId, params).then(response => {
        this.submitList = response.rows
        this.total = response.total
        this.loading = false

        // 计算统计信息
        this.calculateStatistics(response.rows)
      }).catch(() => {
        this.loading = false
        this.$modal.msgError("获取提交列表失败")
      })
    },
    /** 计算统计信息 */
    calculateStatistics(list) {
      // 注意：这里的统计是基于当前页的数据，实际应该从后端返回全局统计
      // 为了简化，这里先使用当前页数据
      this.statistics.total = this.total
      this.statistics.submitted = list.filter(item => item.submitTime).length
      this.statistics.reviewed = list.filter(item => item.status === ReportState.REVIEWED || item.status === ReportState.ARCHIVED).length
      this.statistics.pending = list.filter(item => item.submitTime && (item.status === ReportState.SUBMITTED || item.status === ReportState.REVIEWING || item.status === ReportState.RESUBMITTED)).length
      this.statistics.unsubmitted = list.filter(item => !item.submitTime).length

      // 计算平均分
      const scores = list.filter(item => item.score !== null && item.score !== undefined).map(item => parseFloat(item.score))
      if (scores.length > 0) {
        this.statistics.avgScore = scores.reduce((a, b) => a + b, 0) / scores.length
      } else {
        this.statistics.avgScore = 0
      }
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        status: '',
        keyword: ''
      }
      this.getList()
    },
    /** 开始批改 */
    handleStartReview(row) {
      // 如果状态是已提交(1)或重新提交(5)，需要触发状态机转换为批阅中(2)
      // 如果状态已经是批阅中(2)，直接跳转不触发状态机
      const needStartReview = row.status === ReportState.SUBMITTED || row.status === ReportState.RESUBMITTED

      if (needStartReview) {
        // 触发状态机的开始批阅操作
        startReview(row.submitId).then(() => {
          this.$router.push({
            path: `/task/review/${this.taskId}/${row.submitId}`
          })
        }).catch(() => {
          // 如果状态转换失败，仍然允许查看
          this.$router.push({
            path: `/task/review/${this.taskId}/${row.submitId}`
          })
        })
      } else {
        // 状态已经是批阅中或其他状态，直接跳转
        this.$router.push({
          path: `/task/review/${this.taskId}/${row.submitId}`
        })
      }
    },
    /** 查看报告 */
    handleViewReport(row) {
      this.$router.push({
        path: `/task/review/${this.taskId}/${row.submitId}`,
        query: {
          viewOnly: true
        }
      })
    },
    /** 返回任务列表 */
    handleBack() {
      this.$router.push('/task')
    },
    /** 批量导出成绩 */
    handleExportGrades() {
      this.$modal.confirm('确认导出该任务的所有成绩？').then(() => {
        return exportGrades(this.taskId)
      }).then(response => {
        // 下载文件
        const blob = new Blob([response], { type: 'application/vnd.ms-excel' })
        const link = document.createElement('a')
        link.href = window.URL.createObjectURL(blob)
        link.download = `${this.taskName}-成绩单.xlsx`
        link.click()
        this.$modal.msgSuccess("导出成功")
      }).catch(() => {})
    },
    /** 获取状态描述 */
    getStateDesc(status) {
      return getStateDesc(status)
    },
    /** 获取状态类型 */
    getStateType(status) {
      return getStateType(status)
    },
    /** 判断是否可以开始批改 */
    canStartReview(status) {
      // 已提交、重新提交状态可以开始批改
      return status === ReportState.SUBMITTED || status === ReportState.RESUBMITTED
    },
    /** 判断是否可以查看报告 */
    canViewReport(status) {
      // 批阅中、已批阅、已归档状态可以查看
      return status === ReportState.REVIEWING || status === ReportState.REVIEWED || status === ReportState.ARCHIVED
    }
  }
}
</script>

<style scoped>
.info-item {
  line-height: 32px;
}

.info-item .label {
  color: #909399;
  font-size: 14px;
}

.info-item .value {
  color: #303133;
  font-size: 14px;
  font-weight: 500;
}

.el-statistic {
  text-align: center;
}
</style>
