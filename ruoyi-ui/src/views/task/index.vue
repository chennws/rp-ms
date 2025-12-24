<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
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
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item style="float: right">
        <el-button type="success" icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['task:task:add']">发布新任务</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="taskList">
      <el-table-column label="任务名称" prop="taskName" :show-overflow-tooltip="true" width="300" />
      <el-table-column label="课程名称" prop="courseName" :show-overflow-tooltip="true" width="150" />
      <el-table-column label="发布时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" align="center" prop="deadline" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.deadline, '{y}-{m}-{d} {h}:{i}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交人数" align="center" width="120">
        <template slot-scope="scope">
          <span>{{ scope.row.submitCount || 0 }}/{{ scope.row.totalCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.status === '0'" type="warning" size="small">未开始</el-tag>
          <el-tag v-else-if="scope.row.status === '1'" type="primary" size="small">进行中</el-tag>
          <el-tag v-else-if="scope.row.status === '2'" type="success" size="small">已结束</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="250">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleView(scope.row)"
          >查看详情</el-button>
          <el-button
            v-if="scope.row.status !== '2'"
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['task:task:edit']"
          >编辑</el-button>
          <el-button
            v-if="scope.row.status !== '2'"
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['task:task:remove']"
          >删除</el-button>
          <el-button
            v-if="scope.row.status === '2'"
            size="mini"
            type="text"
            icon="el-icon-data-analysis"
            @click="handleStatistics(scope.row)"
          >成绩统计</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改实验任务对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
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
  </div>
</template>

<script>
import { listTask, getTask, delTask, addTask, updateTask, downloadReport } from "@/api/task/task"
import { listDeptForTask } from "@/api/system/dept"
import { handleTree } from "@/utils/ruoyi"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import FileUpload from "@/components/FileUpload"

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
      // 表单校验
      rules: {
        taskName: [
          { required: true, message: "任务名称不能为空", trigger: "blur" }
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
  created() {
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
      })
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
    }
  }
}
</script>

<style scoped>
</style>

