<template>
  <div class="app-container task-index-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" ref="queryForm" :inline="true" class="search-form" v-show="showSearch">
        <el-form-item label="搜索" prop="keyword">
          <el-input
            v-model="queryParams.keyword"
            placeholder="搜索任务名称或课程名称..."
            clearable
            style="width: 300px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right">
          <el-button type="success" icon="el-icon-plus" @click="handleAdd" v-hasPermi="['task:task:add']">发布新任务</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 学生端：报告状态筛选标签页 -->
    <el-card v-if="!isTeacher" shadow="never" class="filter-card">
      <el-tabs v-model="activeStatusTab" @tab-click="handleStatusTabClick">
        <el-tab-pane label="全部" name="all">
          <span slot="label">
            <i class="el-icon-document"></i>
            全部
            <el-badge :value="statusCounts.all" :hidden="statusCounts.all === 0" class="status-badge" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="未开始" name="null">
          <span slot="label">
            <i class="el-icon-circle-close"></i>
            未开始
            <el-badge :value="statusCounts.null" :hidden="statusCounts.null === 0" class="status-badge" type="info" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="草稿" name="0">
          <span slot="label">
            <i class="el-icon-edit-outline"></i>
            草稿
            <el-badge :value="statusCounts['0']" :hidden="statusCounts['0'] === 0" class="status-badge" type="info" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="已提交" name="1">
          <span slot="label">
            <i class="el-icon-upload"></i>
            已提交
            <el-badge :value="statusCounts['1']" :hidden="statusCounts['1'] === 0" class="status-badge" type="primary" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="批阅中" name="2">
          <span slot="label">
            <i class="el-icon-view"></i>
            批阅中
            <el-badge :value="statusCounts['2']" :hidden="statusCounts['2'] === 0" class="status-badge" type="warning" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="已批阅" name="3">
          <span slot="label">
            <i class="el-icon-circle-check"></i>
            已批阅
            <el-badge :value="statusCounts['3']" :hidden="statusCounts['3'] === 0" class="status-badge" type="success" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="已打回" name="4">
          <span slot="label">
            <i class="el-icon-warning"></i>
            已打回
            <el-badge :value="statusCounts['4']" :hidden="statusCounts['4'] === 0" class="status-badge" type="danger" />
          </span>
        </el-tab-pane>
        <el-tab-pane label="重新提交" name="5">
          <span slot="label">
            <i class="el-icon-refresh"></i>
            重新提交
            <el-badge :value="statusCounts['5']" :hidden="statusCounts['5'] === 0" class="status-badge" type="primary" />
          </span>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 任务列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredTaskList" stripe class="task-table">
        <el-table-column label="任务名称" prop="taskName" :show-overflow-tooltip="true" min-width="200">
          <template slot-scope="scope">
            <div class="task-name">
              <i class="el-icon-document"></i>
              <span>{{ scope.row.taskName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="课程" prop="courseName" :show-overflow-tooltip="true" width="120" />
        <el-table-column label="发布时间" align="center" prop="createTime" width="105">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" align="center" prop="deadline" width="145">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.deadline, '{m}-{d} {h}:{i}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提交情况" align="center" width="120">
          <template slot-scope="scope">
            <div class="submit-info">
              <el-progress
                :percentage="getSubmitRateValue(scope.row)"
                :color="getSubmitColor(scope.row)"
                :stroke-width="14"
              >
                <template slot="default">
                  <span class="progress-text">{{ scope.row.submitCount || 0 }}/{{ scope.row.totalCount || 0 }}</span>
                </template>
              </el-progress>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="任务状态" align="center" width="85">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.status === '0'" type="warning" size="small">未开始</el-tag>
            <el-tag v-else-if="scope.row.status === '1'" type="primary" size="small">进行中</el-tag>
            <el-tag v-else-if="scope.row.status === '2'" type="success" size="small">已结束</el-tag>
          </template>
        </el-table-column>
        <!-- 学生端显示报告状态 -->
        <el-table-column v-if="!isTeacher" label="报告状态" align="center" width="85">
          <template slot-scope="scope">
            <el-tag v-if="!scope.row.studentSubmitStatus" type="info" size="small">未开始</el-tag>
            <el-tag v-else :type="getStateType(scope.row.studentSubmitStatus)" size="small">
              {{ getStateDesc(scope.row.studentSubmitStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" :width="isTeacher ? 280 : 340" fixed="right">
          <template slot-scope="scope">
            <el-button
              size="mini"
              type="primary"
              icon="el-icon-view"
              @click="handleView(scope.row)"
              plain
            >查看</el-button>
            <!-- 学生端：查看批改结果（仅已批阅状态显示） -->
            <el-button
              v-if="!isTeacher && scope.row.studentSubmitStatus === '3'"
              size="mini"
              type="success"
              icon="el-icon-star-on"
              @click="handleViewGrade(scope.row)"
            >查看成绩</el-button>
            <!-- 学生端：在线完成 -->
            <el-button
              v-if="!isTeacher && scope.row.status === '1'"
              size="mini"
              type="success"
              icon="el-icon-edit-outline"
              @click="handleOnlineComplete(scope.row)"
            >在线完成</el-button>
            <el-button
              v-if="scope.row.status !== '2'"
              size="mini"
              type="warning"
              icon="el-icon-edit"
              @click="handleUpdate(scope.row)"
              v-hasPermi="['task:task:edit']"
              plain
            >编辑</el-button>
            <el-button
              v-if="scope.row.status !== '2'"
              size="mini"
              type="danger"
              icon="el-icon-delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['task:task:remove']"
              plain
            >删除</el-button>
            <el-button
              v-if="scope.row.status === '2'"
              size="mini"
              type="info"
              icon="el-icon-data-analysis"
              @click="handleStatistics(scope.row)"
            >统计</el-button>
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

    <!-- 添加或修改实验任务对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称，例如：实验一、实验二" />
          <span class="form-tip">建议格式：实验一、实验二、实验三...</span>
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="发布部门" prop="deptId">
          <treeselect v-model="form.deptId" :options="deptOptions" :show-count="true" placeholder="请选择发布部门" />
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            placeholder="选择截止时间"
            value-format="yyyy-MM-dd HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="实验报告">
          <file-upload v-model="form.reportFileUrl" :limit="1" :fileSize="10" :fileType="['doc', 'docx', 'pdf', 'txt']" action="/Task/upload" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog title="任务详情" :visible.sync="viewOpen" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="任务名称">{{ viewForm.taskName }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ viewForm.courseName }}</el-descriptions-item>
        <el-descriptions-item label="发布部门">{{ viewForm.deptName }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ parseTime(viewForm.createTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ parseTime(viewForm.deadline, '{y}-{m}-{d} {h}:{i}:{s}') }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="viewForm.status === '0'" type="warning" size="small">未开始</el-tag>
          <el-tag v-else-if="viewForm.status === '1'" type="primary" size="small">进行中</el-tag>
          <el-tag v-else-if="viewForm.status === '2'" type="success" size="small">已结束</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="提交人数">{{ viewForm.submitCount || 0 }}/{{ viewForm.totalCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewForm.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="实验报告" v-if="viewForm.reportFileUrl">
          <el-button
            type="primary"
            size="mini"
            icon="el-icon-download"
            @click="handleDownloadReport"
          >下载实验报告</el-button>
        </el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="viewOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- 学生端：查看批改结果对话框 -->
    <el-dialog title="批改结果" :visible.sync="gradeOpen" width="600px" append-to-body>
      <div v-if="gradeForm.score !== null && gradeForm.score !== undefined" class="grade-content">
        <!-- 成绩卡片 -->
        <el-card shadow="hover" class="grade-card">
          <div class="grade-header">
            <i class="el-icon-trophy grade-icon"></i>
            <span class="grade-label">成绩</span>
          </div>
          <div class="grade-score">{{ gradeForm.score }}</div>
          <div class="grade-footer">满分100分</div>
        </el-card>

        <!-- 评语区域 -->
        <el-card shadow="hover" class="remark-card" v-if="gradeForm.teacherRemark">
          <div class="remark-header">
            <i class="el-icon-chat-dot-round remark-icon"></i>
            <span class="remark-label">教师评语</span>
          </div>
          <div class="remark-content">{{ gradeForm.teacherRemark }}</div>
        </el-card>

        <!-- 如果没有评语 -->
        <el-card shadow="hover" class="remark-card" v-else>
          <div class="remark-header">
            <i class="el-icon-chat-dot-round remark-icon"></i>
            <span class="remark-label">教师评语</span>
          </div>
          <div class="remark-content empty">暂无评语</div>
        </el-card>

        <!-- 提交信息 -->
        <el-descriptions :column="1" border class="submit-info">
          <el-descriptions-item label="提交时间">
            {{ parseTime(gradeForm.submitTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
          </el-descriptions-item>
          <el-descriptions-item label="批阅状态">
            <el-tag type="success" size="small">已批阅</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <div v-else class="no-grade">
        <i class="el-icon-warning"></i>
        <p>暂未评分</p>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="gradeOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTask, getTask, delTask, addTask, updateTask, downloadReport, getMySubmitDetail } from "@/api/task/task"
import { listDeptForTask } from "@/api/system/dept"
import { handleTree } from "@/utils/ruoyi"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import FileUpload from "@/components/FileUpload"
import { getStateDesc, getStateType } from "@/constants/reportState"

export default {
  name: "Task",
  components: { Treeselect, FileUpload },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 任务表格数据
      taskList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示查看详情弹出层
      viewOpen: false,
      // 是否显示查看成绩弹出层
      gradeOpen: false,
      // 部门树选项
      deptOptions: undefined,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        keyword: undefined
      },
      // 表单参数
      form: {},
      // 查看详情表单
      viewForm: {},
      // 查看成绩表单
      gradeForm: {},
      // 学生端：当前选中的状态标签
      activeStatusTab: 'all',
      // 学生端：各状态的任务数量
      statusCounts: {
        all: 0,
        null: 0,
        '0': 0,
        '1': 0,
        '2': 0,
        '3': 0,
        '4': 0,
        '5': 0
      },
      // 表单校验
      rules: {
        taskName: [
          { required: true, message: "任务名称不能为空", trigger: "blur" },
          {
            pattern: /^实验[一二三四五六七八九十\d]+$/,
            message: "任务名称建议格式：实验一、实验二、实验三...",
            trigger: "blur"
          }
        ],
        courseName: [
          { required: true, message: "课程名称不能为空", trigger: "blur" }
        ],
        deptId: [
          { required: true, message: "发布部门不能为空", trigger: "change" }
        ],
        deadline: [
          { required: true, message: "截止时间不能为空", trigger: "change" }
        ]
      }
    }
  },
  computed: {
    /** 判断是否为教师 */
    isTeacher() {
      // 检查是否有新增任务权限，如果有则说明是教师
      const permissions = this.$store.getters && this.$store.getters.permissions
      if (!permissions || permissions.length === 0) {
        return false
      }
      return permissions.some(permission => permission === 'task:task:add' || permission === '*:*:*')
    },
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
  },
  created() {
    this.getList()
  },
  activated() {
    // 页面激活时刷新列表（从编辑页面返回时）
    this.getList()
  },
  methods: {
    /** 查询任务列表 */
    getList() {
      this.loading = true
      const params = {
        pageNum: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize
      }
      if (this.queryParams.keyword) {
        params.taskName = this.queryParams.keyword
        params.courseName = this.queryParams.keyword
      }
      listTask(params).then(response => {
        this.taskList = response.rows
        this.total = response.total
        this.loading = false

        // 学生端：统计各状态的任务数量
        if (!this.isTeacher) {
          this.updateStatusCounts()
        }

        // 调试：查看学生报告状态
        if (!this.isTeacher && this.taskList.length > 0) {
          console.log('学生端任务列表数据:', this.taskList)
          console.log('第一个任务的报告状态:', this.taskList[0].studentSubmitStatus)
        }
      })
    },
    /** 更新状态统计数量 */
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
    },
    /** 状态标签页点击事件 */
    handleStatusTabClick(tab) {
      console.log('切换到状态标签:', tab.name)
      // 标签页切换时，filteredTaskList 会自动更新
    },
    /** 查询部门下拉树结构 */
    getDeptTree() {
      // 如果已经加载过部门树，直接返回
      if (this.deptOptions && this.deptOptions.length > 0) {
        return
      }
      listDeptForTask().then(response => {
        const deptTree = handleTree(response.data, "deptId", "parentId")
        this.deptOptions = this.convertDeptTree(deptTree)
      }).catch(error => {
        // 权限不足时静默失败，不影响页面使用
        console.warn('加载部门列表失败，可能没有权限:', error)
        this.deptOptions = []
      })
    },
    /** 转换部门数据结构为treeselect格式 */
    convertDeptTree(tree) {
      return tree.map(node => {
        const item = {
          id: node.deptId,
          label: node.deptName
        }
        if (node.children && node.children.length > 0) {
          item.children = this.convertDeptTree(node.children)
        }
        return item
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        taskId: undefined,
        taskName: undefined,
        courseName: undefined,
        deptId: undefined,
        deadline: undefined,
        status: "0",
        remark: undefined,
        reportFileUrl: undefined
      }
      this.resetForm("form")
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
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.getDeptTree() // 在打开新增对话框时加载部门树
      this.open = true
      this.title = "发布新任务"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      this.getDeptTree() // 在打开编辑对话框时加载部门树
      const taskId = row.taskId || this.ids
      getTask(taskId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改任务"
      })
    },
    /** 查看详情按钮操作 */
    handleView(row) {
      getTask(row.taskId).then(response => {
        this.viewForm = response.data
        this.viewOpen = true
      })
    },
    /** 学生端：查看批改结果 */
    handleViewGrade(row) {
      getMySubmitDetail(row.taskId).then(response => {
        this.gradeForm = response.data
        this.gradeOpen = true
      }).catch(err => {
        this.$modal.msgError("获取批改结果失败")
      })
    },
    /** 下载实验报告 */
    handleDownloadReport() {
      if (!this.viewForm.reportFileUrl) {
        this.$modal.msgWarning("该任务没有实验报告")
        return
      }
      downloadReport(this.viewForm.reportFileUrl)
    },
    /** 成绩统计按钮操作 */
    handleStatistics(row) {
      this.$router.push({ path: '/task/statistics', query: { taskId: row.taskId } })
    },
    /** 在线完成按钮操作 */
    handleOnlineComplete(row) {
      // 获取任务详情以获取文件URL
      getTask(row.taskId).then(response => {
        const task = response.data
        if (!task.reportFileUrl) {
          this.$modal.msgWarning("该任务没有实验报告模板，无法在线完成")
          return
        }
        // 跳转到在线编辑页面
        this.$router.push({
          path: '/task/edit',
          query: {
            taskId: task.taskId,
            taskName: task.taskName,
            fileUrl: task.reportFileUrl
          }
        })
      }).catch(() => {
        this.$modal.msgError("获取任务信息失败")
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.taskId != undefined) {
            updateTask(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addTask(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const taskIds = row.taskId || this.ids
      this.$modal.confirm('是否确认删除任务编号为"' + taskIds + '"的数据项？').then(function() {
        return delTask(taskIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
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
    /** 获取提交颜色 */
    getSubmitColor(row) {
      const rate = this.getSubmitRateValue(row)
      if (rate >= 80) return '#67C23A'
      if (rate >= 50) return '#E6A23C'
      return '#F56C6C'
    },
    /** 获取报告状态描述 */
    getStateDesc(state) {
      const desc = getStateDesc(state)
      // 调试：如果返回未知，打印实际值
      if (desc === '未知') {
        console.warn('未知的报告状态:', state, '类型:', typeof state)
      }
      return desc
    },
    /** 获取报告状态标签类型 */
    getStateType(state) {
      return getStateType(state)
    }
  }
}
</script>

<style scoped>
.task-index-container {
  background-color: #f0f2f5;
  min-height: calc(100vh - 84px);
}

/* 搜索卡片 */
.search-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

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

.task-table {
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
  font-size: 11px;
  font-weight: 600;
}

/* 分页 */
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 进度条样式优化 */
.task-table ::v-deep .el-progress__text {
  font-size: 11px !important;
}

.task-table ::v-deep .el-progress-bar__inner {
  border-radius: 7px;
}

.task-table ::v-deep .el-progress-bar__outer {
  border-radius: 7px;
  background-color: #e4e7ed;
}

/* 标签样式 */
.task-table ::v-deep .el-tag {
  font-weight: 500;
}

/* 操作按钮样式 */
.task-table .el-button--mini {
  padding: 5px 10px;
  font-size: 12px;
  margin: 1px;
}

.task-table .el-button {
  margin: 1px;
}

/* 表单提示 */
.form-tip {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

/* 批改结果对话框样式 */
.grade-content {
  padding: 10px 0;
}

.grade-card {
  margin-bottom: 20px;
  text-align: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 12px;
}

.grade-card ::v-deep .el-card__body {
  padding: 30px;
}

.grade-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 15px;
  font-size: 16px;
  font-weight: 500;
}

.grade-icon {
  font-size: 24px;
  margin-right: 8px;
  color: #ffd700;
}

.grade-score {
  font-size: 56px;
  font-weight: bold;
  margin: 20px 0;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}

.grade-footer {
  font-size: 14px;
  opacity: 0.9;
}

.remark-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.remark-header {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.remark-icon {
  font-size: 20px;
  margin-right: 8px;
  color: #409EFF;
}

.remark-content {
  font-size: 15px;
  line-height: 1.8;
  color: #606266;
  background: #f5f7fa;
  padding: 15px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.remark-content.empty {
  color: #909399;
  font-style: italic;
  text-align: center;
}

.submit-info {
  margin-top: 10px;
}

.no-grade {
  text-align: center;
  padding: 60px 0;
  color: #909399;
}

.no-grade i {
  font-size: 64px;
  margin-bottom: 20px;
  display: block;
}

.no-grade p {
  font-size: 16px;
  margin: 0;
}
</style>

