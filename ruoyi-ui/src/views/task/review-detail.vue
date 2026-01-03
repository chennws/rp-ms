<template>
  <div class="app-container review-detail-container">
    <!-- 头部导航栏 -->
    <div class="header-bar">
      <div class="left-section">
        <el-button icon="el-icon-back" size="small" @click="handleBack">返回列表</el-button>
        <span class="title">📝 {{ submitInfo.nickName }}的报告</span>
      </div>
      <div class="center-section">
        <span class="progress-text">已批改 {{ progress.reviewed }}/{{ progress.total }}</span>
      </div>
      <div class="right-section">
        <el-button
          size="small"
          icon="el-icon-arrow-left"
          :disabled="!prevSubmitId"
          @click="handlePrev"
        >上一个</el-button>
        <el-button
          size="small"
          :disabled="!nextSubmitId"
          @click="handleNext"
        >下一个<i class="el-icon-arrow-right el-icon--right"></i></el-button>
      </div>
    </div>

    <!-- 主体内容区 -->
    <div class="main-content">
      <!-- 左侧：OnlyOffice编辑器 -->
      <div class="editor-section">
        <div v-if="loading" class="loading-container" v-loading="loading" element-loading-text="正在加载文档编辑器...">
        </div>
        <div v-if="error" class="error-container">
          <el-result icon="error" :title="error">
            <template slot="subTitle">
              <p v-if="error.includes('容器')">页面初始化异常，请尝试以下操作：</p>
              <p v-else-if="error.includes('服务')">OnlyOffice文档服务连接失败，可能的原因：</p>
              <p v-else-if="error.includes('配置')">无法获取文档配置信息，请检查：</p>
              <p v-else>文档编辑器加载失败</p>
              <ul style="text-align: left; margin: 10px auto; max-width: 400px; color: #606266;">
                <li v-if="error.includes('容器')">刷新页面重新加载</li>
                <li v-if="error.includes('容器')">清除浏览器缓存后重试</li>
                <li v-if="error.includes('服务')">检查网络连接是否正常</li>
                <li v-if="error.includes('服务')">确认OnlyOffice服务是否运行</li>
                <li v-if="error.includes('服务')">联系管理员检查服务配置</li>
                <li v-if="error.includes('配置')">检查网络连接</li>
                <li v-if="error.includes('配置')">确认文件是否存在</li>
                <li v-if="!error.includes('容器') && !error.includes('服务') && !error.includes('配置')">刷新页面重试</li>
                <li v-if="!error.includes('容器') && !error.includes('服务') && !error.includes('配置')">检查浏览器控制台错误信息</li>
              </ul>
            </template>
            <template slot="extra">
              <el-button type="primary" size="small" icon="el-icon-refresh" @click="retryInit">刷新重试</el-button>
              <el-button size="small" icon="el-icon-back" @click="handleBack">返回列表</el-button>
            </template>
          </el-result>
        </div>
        <div id="onlyoffice-review" class="onlyoffice-container" :style="{ display: loading || error ? 'none' : 'block' }"></div>
      </div>

      <!-- 右侧：批改表单 -->
      <div class="form-section">
        <div class="form-scroll">
          <el-card shadow="never" class="info-card">
            <div slot="header" class="card-header">
              <i class="el-icon-user"></i>
              <span>学生信息</span>
            </div>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="姓名" label-class-name="desc-label">
                <span class="desc-value">{{ submitInfo.nickName }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="学号" label-class-name="desc-label">
                <span class="desc-value">{{ submitInfo.userName }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="提交时间" label-class-name="desc-label">
                <span class="desc-value">{{ parseTime(submitInfo.submitTime, '{y}-{m}-{d} {h}:{i}') }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="状态" label-class-name="desc-label">
                <el-tag :type="getStatusType(submitInfo.status)" size="mini">{{ getStatusText(submitInfo.status) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="submitInfo.rejectReason" label="打回原因" label-class-name="desc-label">
                <span class="desc-value reject-reason">{{ submitInfo.rejectReason }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="never" class="score-card">
            <div slot="header" class="card-header">
              <i class="el-icon-edit-outline"></i>
              <span>评分</span>
            </div>
            <el-form ref="form" :model="form" :rules="rules" label-width="60px" size="small">
              <el-form-item label="分数" prop="score">
                <el-input-number
                  v-model="form.score"
                  :min="0"
                  :max="100"
                  :precision="1"
                  :disabled="viewOnly"
                  controls-position="right"
                  style="width: 100%;"
                  placeholder="请输入分数"
                />
              </el-form-item>
            </el-form>
            <div class="score-tips">
              <el-tag size="mini" type="success">优秀90+</el-tag>
              <el-tag size="mini" type="primary">良好80+</el-tag>
              <el-tag size="mini" type="warning">中等70+</el-tag>
              <el-tag size="mini" type="info">及格60+</el-tag>
            </div>
          </el-card>

          <el-card shadow="never" class="remark-card">
            <div slot="header" class="card-header">
              <i class="el-icon-chat-line-square"></i>
              <span>教师评语</span>
            </div>
            <el-form ref="remarkForm" :model="form" label-width="0">
              <el-form-item>
                <el-input
                  v-model="form.teacherRemark"
                  type="textarea"
                  :rows="5"
                  :disabled="viewOnly"
                  placeholder="请输入教师评语..."
                  maxlength="500"
                  show-word-limit
                />
              </el-form-item>
            </el-form>
            <div v-if="!viewOnly" class="remark-templates">
              <el-button size="mini" plain @click="insertTemplate('优秀')">优秀</el-button>
              <el-button size="mini" plain @click="insertTemplate('良好')">良好</el-button>
              <el-button size="mini" plain @click="insertTemplate('需改进')">需改进</el-button>
            </div>
          </el-card>

          <el-card shadow="never" class="action-card">
            <div slot="header" class="card-header">
              <i class="el-icon-finished"></i>
              <span>操作</span>
            </div>
            <div class="action-buttons">
              <el-button
                type="primary"
                icon="el-icon-check"
                :loading="saving"
                :disabled="viewOnly"
                @click="handleSave"
                size="small"
                class="action-btn"
              >{{ viewOnly ? '只读模式' : '保存' }}</el-button>
              <el-button
                type="warning"
                icon="el-icon-refresh-left"
                :disabled="viewOnly"
                @click="handleReject"
                size="small"
                class="action-btn"
              >打回</el-button>
              <el-button
                icon="el-icon-close"
                @click="handleBack"
                size="small"
                class="action-btn"
              >返回列表</el-button>
            </div>
            <div v-if="viewOnly" class="readonly-notice">
              <i class="el-icon-view"></i>
              <span>当前为只读模式，不可编辑</span>
            </div>
            <div class="shortcut-tips">
              <div class="tips-content-compact">
                <span v-if="!viewOnly"><i class="el-icon-info"></i> Ctrl+S 保存 | Ctrl+← 上一个 | Ctrl+→ 下一个</span>
                <span v-else><i class="el-icon-info"></i> Ctrl+← 上一个 | Ctrl+→ 下一个</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 打回对话框 -->
    <el-dialog title="打回报告" :visible.sync="rejectDialogVisible" width="500px" append-to-body>
      <el-form ref="rejectForm" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="打回原因" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="5"
            placeholder="请输入打回原因..."
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="rejectDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitReject" :loading="rejecting">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getReviewDetail, saveReview, getSubmitIdList } from "@/api/task/review"
import { getConfig } from "@/api/task/task"
import { rejectReport, getPermittedActions } from "@/api/task/stateMachine"
import { getToken } from "@/utils/auth"
import { ReportState, ReportTrigger, getStateDesc, getStateType } from "@/constants/reportState"

export default {
  name: "ReviewDetail",
  data() {
    return {
      taskId: undefined,
      submitId: undefined,
      submitInfo: {},
      viewOnly: false, // 只读模式标志
      form: {
        score: null,
        teacherRemark: ''
      },
      rules: {
        score: [
          { required: true, message: "请输入分数", trigger: "blur" },
          { type: 'number', min: 0, max: 100, message: "分数范围为0-100", trigger: "blur" }
        ]
      },
      // OnlyOffice相关
      editor: null,
      documentKey: '',
      fileUrl: '',
      loading: true,
      error: null,
      documentServerUrl: process.env.VUE_APP_DOCUMENT_SERVER_URL || 'http://47.115.163.152:9001/web-apps/apps/api/documents/api.js',
      editorInitRetryCount: 0, // 编辑器初始化重试次数
      maxRetryCount: 10, // 最大重试次数
      // 导航相关
      submitIdList: [],
      prevSubmitId: null,
      nextSubmitId: null,
      progress: {
        current: 0,
        total: 0,
        reviewed: 0
      },
      // 保存状态
      saving: false,
      // 打回相关
      rejectDialogVisible: false,
      rejectForm: {
        reason: ''
      },
      rejectRules: {
        reason: [
          { required: true, message: "请输入打回原因", trigger: "blur" },
          { min: 5, message: "打回原因至少5个字符", trigger: "blur" }
        ]
      },
      rejecting: false
    }
  },
  watch: {
    // 监听路由参数变化（当从一个报告跳转到另一个报告时）
    '$route'(to, from) {
      // 必须确保两个路由都有 submitId 参数，且属于同一个任务
      if (!to.params.submitId || !from.params.submitId) {
        console.log('路由缺少 submitId 参数，跳过处理')
        return
      }

      // 检查是否是同一个任务的不同报告
      if (to.params.taskId === from.params.taskId && to.params.submitId !== from.params.submitId) {
        console.log('路由参数变化，从报告', from.params.submitId, '跳转到', to.params.submitId)

        // 验证新的 submitId 是否有效
        const newSubmitId = parseInt(to.params.submitId)
        if (isNaN(newSubmitId)) {
          console.error('无效的 submitId:', to.params.submitId)
          this.$modal.msgError("报告ID无效")
          return
        }

        // 先销毁当前编辑器
        if (this.editor) {
          console.log('销毁旧的编辑器实例')
          try {
            this.editor.destroyEditor()
          } catch (e) {
            console.warn('销毁编辑器时出错:', e)
          }
          this.editor = null
        }

        // 重置状态
        this.loading = true
        this.error = null
        this.editorInitRetryCount = 0

        // 更新 submitId
        this.submitId = newSubmitId

        // 重新加载数据
        this.loadSubmitDetail()
        this.loadNextPrevInfo()
      }
    }
  },
  created() {
    this.taskId = this.$route.params.taskId
    this.submitId = parseInt(this.$route.params.submitId)
    this.viewOnly = this.$route.query.viewOnly === 'true' // 读取只读模式参数

    if (!this.taskId || !this.submitId) {
      this.$modal.msgError("参数错误")
      this.$router.back()
      return
    }

    // 加载数据
    this.loadSubmitDetail()
    this.loadNextPrevInfo()

    // 绑定快捷键
    this.bindKeyboardShortcuts()
  },
  mounted() {
    // 初始化编辑器会在loadSubmitDetail中完成
  },
  beforeDestroy() {
    // 销毁编辑器
    if (this.editor) {
      this.editor.destroyEditor()
    }
    // 解绑快捷键
    this.unbindKeyboardShortcuts()
  },
  methods: {
    /** 加载提交详情 */
    loadSubmitDetail() {
      getReviewDetail(this.submitId).then(response => {
        this.submitInfo = response.data
        this.form.score = response.data.score
        this.form.teacherRemark = response.data.teacherRemark || ''
        this.fileUrl = response.data.fileUrl
        this.documentKey = response.data.documentKey

        console.log('提交详情:', response.data)
        console.log('fileUrl:', this.fileUrl)
        console.log('documentKey:', this.documentKey)

        // 初始化OnlyOffice
        this.initEditor()
      }).catch(() => {
        this.$modal.msgError("获取批改详情失败")
        this.$router.back()
      })
    },
    /** 加载上一个/下一个信息 */
    loadNextPrevInfo() {
      getSubmitIdList(this.taskId).then(response => {
        this.submitIdList = response.data || []
        const currentIndex = this.submitIdList.indexOf(this.submitId)

        console.log('待批改报告ID列表:', this.submitIdList)
        console.log('当前报告ID:', this.submitId, '索引:', currentIndex)

        if (currentIndex >= 0) {
          this.prevSubmitId = currentIndex > 0 ? this.submitIdList[currentIndex - 1] : null
          this.nextSubmitId = currentIndex < this.submitIdList.length - 1 ? this.submitIdList[currentIndex + 1] : null

          this.progress.current = currentIndex + 1
          this.progress.total = this.submitIdList.length
          this.progress.reviewed = currentIndex + 1
        } else {
          // 当前报告不在待批改列表中（可能已经批改完成）
          console.warn('当前报告不在待批改列表中，可能已批改完成')
          this.prevSubmitId = null
          this.nextSubmitId = null
          this.progress.current = 0
          this.progress.total = this.submitIdList.length
          this.progress.reviewed = 0
        }

        console.log('上一个:', this.prevSubmitId, '下一个:', this.nextSubmitId)
        console.log('进度:', this.progress.reviewed, '/', this.progress.total)
      }).catch(() => {
        console.error("获取提交ID列表失败")
      })
    },
    /** 初始化OnlyOffice编辑器 */
    initEditor() {
      this.loading = true
      this.error = null

      console.log('开始初始化 OnlyOffice 编辑器')
      console.log('文档服务器地址:', this.documentServerUrl)

      this.$nextTick(() => {
        const container = document.getElementById('onlyoffice-review')
        if (!container) {
          // 检查重试次数
          if (this.editorInitRetryCount >= this.maxRetryCount) {
            console.error('容器元素初始化失败，已达最大重试次数')
            this.error = '编辑器加载失败，请刷新页面重试'
            this.loading = false
            this.editorInitRetryCount = 0 // 重置计数器
            return
          }

          console.warn(`容器元素暂时未找到，等待DOM渲染... (重试 ${this.editorInitRetryCount + 1}/${this.maxRetryCount})`)
          this.editorInitRetryCount++
          // 继续保持加载状态，而不是显示错误
          // 延迟重试
          setTimeout(() => {
            this.initEditor()
          }, 100)
          return
        }

        // 容器找到了，重置重试计数器
        this.editorInitRetryCount = 0

        // 动态加载 OnlyOffice API
        if (window.DocsAPI && window.DocsAPI.DocEditor) {
          console.log('OnlyOffice API 已加载，直接创建编辑器')
          this.createEditor()
        } else {
          console.log('开始加载 OnlyOffice API 脚本')
          const script = document.createElement('script')
          script.type = 'text/javascript'
          script.src = this.documentServerUrl
          script.onload = () => {
            console.log('OnlyOffice API 脚本加载成功')
            if (window.DocsAPI && window.DocsAPI.DocEditor) {
              this.createEditor()
            } else {
              this.error = '文档编辑器服务未响应'
              this.loading = false
            }
          }
          script.onerror = () => {
            this.error = '无法连接到文档编辑器服务'
            this.loading = false
          }
          document.head.appendChild(script)
        }
      })
    },
    /** 创建编辑器 */
    createEditor() {
      if (!window.DocsAPI || !window.DocsAPI.DocEditor) {
        this.error = "文档编辑器API未加载"
        this.loading = false
        return
      }

      // 调用后端接口获取编辑器配置
      console.log('获取编辑器配置, fileUrl:', this.fileUrl, 'documentKey:', this.documentKey)

      getConfig(this.fileUrl, 'edit', this.documentKey).then(response => {
        if (!response.data) {
          throw new Error('获取编辑器配置失败')
        }

        const config = response.data
        console.log('获取到编辑器配置:', config)

        // 修改用户信息为教师
        if (config.editorConfig && config.editorConfig.user) {
          config.editorConfig.user.id = this.getUserId()
          config.editorConfig.user.name = this.getUserName()
        }

        // 根据只读模式配置权限
        if (this.viewOnly) {
          // 只读模式：只允许查看和评论
          if (config.document) {
            config.document.permissions = {
              comment: true,
              download: false,
              edit: false,
              print: false,
              review: false
            }
          }
          if (config.editorConfig) {
            config.editorConfig.mode = 'view'
          }
        } else {
          // 编辑模式：配置为审阅模式
          if (config.document) {
            config.document.permissions = {
              comment: true,
              download: true,
              edit: true,
              print: true,
              review: true
            }
          }

          if (config.editorConfig) {
            config.editorConfig.customization = {
              ...config.editorConfig.customization,
              reviewDisplay: 'markup',
              trackChanges: true,
              comments: true
            }
          }
        }

        // 事件处理
        config.events = {
          onDocumentReady: () => {
            console.log('[OnlyOffice] 文档已准备就绪')
            this.loading = false
            if (this.viewOnly) {
              this.$message({
                message: '文档已加载（只读模式）',
                type: 'info',
                duration: 2000
              })
            }
          },
          onError: (error) => {
            console.error('[OnlyOffice] 编辑器错误:', error)
            this.error = `文档加载失败 (错误代码: ${error.errorCode || '未知'})`
            this.loading = false
          }
        }

        // 创建编辑器实例
        try {
          console.log('开始创建 OnlyOffice 编辑器实例')
          // 检查容器是否还存在
          const container = document.getElementById('onlyoffice-review')
          if (!container) {
            // 检查重试次数
            if (this.editorInitRetryCount >= this.maxRetryCount) {
              console.error('创建编辑器时容器丢失，已达最大重试次数')
              this.error = '编辑器加载失败，请刷新页面重试'
              this.loading = false
              this.editorInitRetryCount = 0
              return
            }

            console.warn(`创建编辑器时容器丢失，等待重新加载... (重试 ${this.editorInitRetryCount + 1}/${this.maxRetryCount})`)
            this.editorInitRetryCount++
            // 继续保持加载状态，延迟重试
            setTimeout(() => {
              this.initEditor()
            }, 200)
            return
          }

          this.editor = new window.DocsAPI.DocEditor('onlyoffice-review', config)
          console.log('编辑器实例创建成功')
        } catch (error) {
          console.error('创建编辑器失败:', error)
          this.error = `创建文档编辑器失败: ${error.message}`
          this.loading = false
        }
      }).catch(error => {
        console.error('获取编辑器配置失败:', error)
        this.error = '无法获取文档配置'
        this.loading = false
      })
    },
    /** 获取错误消息 */
    getErrorMessage(errorCode) {
      const errorMessages = {
        '-1': '未知错误',
        '-2': '转换超时错误',
        '-3': '转换错误',
        '-4': '下载文档文件错误',
        '-5': '格式不支持',
        '-6': '上传文件错误',
        '-7': '保存文档错误',
        '-8': '文档被其他用户编辑'
      }
      return errorMessages[errorCode] || `错误代码: ${errorCode}`
    },
    /** 重试初始化 */
    retryInit() {
      this.error = null
      this.loading = true
      this.editorInitRetryCount = 0 // 重置重试计数器
      this.initEditor()
    },
    /** 获取用户ID */
    getUserId() {
      return String(this.$store.getters.id || this.$store.state.user.id || 'teacher')
    },
    /** 获取用户名 */
    getUserName() {
      return this.$store.getters.nickName || this.$store.getters.name || '教师'
    },
    /** 获取状态文本 */
    getStatusText(status) {
      return getStateDesc(status)
    },
    /** 获取状态标签类型 */
    getStatusType(status) {
      return getStateType(status)
    },
    /** 插入评语模板 */
    insertTemplate(template) {
      const templates = {
        '优秀': '实验完成质量优秀，思路清晰，代码规范，充分理解了实验要求。',
        '良好': '实验完成质量良好，基本达到要求，建议进一步优化细节。',
        '需改进': '实验存在一些不足，需要进一步改进和完善。'
      }
      if (this.form.teacherRemark) {
        this.form.teacherRemark += '\n' + templates[template]
      } else {
        this.form.teacherRemark = templates[template]
      }
    },
    /** 保存批改 */
    handleSave() {
      // 只读模式提示
      if (this.viewOnly) {
        this.$modal.msgWarning("当前为只读模式，无法保存")
        return
      }

      this.$refs.form.validate(valid => {
        if (!valid) {
          return false
        }

        this.saving = true
        const data = {
          submitId: this.submitId,
          score: this.form.score,
          teacherRemark: this.form.teacherRemark
        }

        // ✅ saveReview 后端方法已包含状态转换逻辑，无需前端再调用状态机
        saveReview(data).then(() => {
          this.$modal.msgSuccess("批改保存成功")
          this.saving = false

          // ✅ 保存成功后，检测是否有下一个待批改报告
          return getSubmitIdList(this.taskId)
        }).then(response => {
          const newSubmitIdList = response.data || []
          console.log('批改后的待批改列表:', newSubmitIdList)

          // 检查是否还有待批改的报告
          if (newSubmitIdList.length === 0) {
            // 没有待批改的了，跳转回批改列表
            this.$message({
              message: '所有报告已批改完成！',
              type: 'success',
              duration: 2000,
              onClose: () => {
                this.$router.push('/review')
              }
            })
            // 延迟跳转，让用户看到提示信息
            setTimeout(() => {
              this.$router.push('/review')
            }, 1500)
          } else {
            // 还有待批改的，跳转到下一个
            const nextId = newSubmitIdList[0]

            // 验证 nextId 是否有效
            if (!nextId || isNaN(parseInt(nextId))) {
              console.error('无效的下一个报告ID:', nextId)
              this.$modal.msgError("获取下一个报告失败，返回批改列表")
              setTimeout(() => {
                this.$router.push('/review')
              }, 1500)
              return
            }

            this.$message({
              message: `自动跳转到下一个待批改报告...`,
              type: 'success',
              duration: 1500
            })
            // 延迟跳转，路由 watch 会自动加载数据
            setTimeout(() => {
              this.$router.replace({
                path: `/task/review/${this.taskId}/${nextId}`
              })
            }, 1000)
          }
        }).catch((error) => {
          console.error('保存批改失败或获取列表失败:', error)
          this.saving = false
        })
      })
    },
    /** 上一个 */
    handlePrev() {
      if (this.prevSubmitId) {
        this.$router.replace({
          path: `/task/review/${this.taskId}/${this.prevSubmitId}`
        })
        // 路由 watch 会自动加载数据
      }
    },
    /** 下一个 */
    handleNext() {
      if (this.nextSubmitId) {
        this.$router.replace({
          path: `/task/review/${this.taskId}/${this.nextSubmitId}`
        })
        // 路由 watch 会自动加载数据
      }
    },
    /** 返回列表 */
    handleBack() {
      this.$router.push(`/task/review/${this.taskId}`)
    },
    /** 打开打回对话框 */
    handleReject() {
      this.rejectForm.reason = ''
      this.rejectDialogVisible = true
      this.$nextTick(() => {
        this.$refs.rejectForm.clearValidate()
      })
    },
    /** 提交打回 */
    submitReject() {
      this.$refs.rejectForm.validate(valid => {
        if (!valid) {
          return false
        }

        this.rejecting = true
        rejectReport(this.submitId, this.rejectForm.reason).then(() => {
          this.$modal.msgSuccess("已打回报告")
          this.rejecting = false
          this.rejectDialogVisible = false

          // 刷新页面或跳转（路由 watch 会自动加载数据）
          if (this.nextSubmitId) {
            this.$router.replace({
              path: `/task/review/${this.taskId}/${this.nextSubmitId}`
            })
          } else {
            this.$router.push(`/task/review/${this.taskId}`)
          }
        }).catch(() => {
          this.rejecting = false
        })
      })
    },
    /** 绑定键盘快捷键 */
    bindKeyboardShortcuts() {
      this.keyHandler = (e) => {
        // Ctrl+S: 保存（只读模式下禁用）
        if (e.ctrlKey && e.key === 's') {
          e.preventDefault()
          if (!this.viewOnly) {
            this.handleSave()
          }
        }
        // Ctrl+→: 下一个
        if (e.ctrlKey && e.key === 'ArrowRight') {
          e.preventDefault()
          this.handleNext()
        }
        // Ctrl+←: 上一个
        if (e.ctrlKey && e.key === 'ArrowLeft') {
          e.preventDefault()
          this.handlePrev()
        }
      }
      document.addEventListener('keydown', this.keyHandler)
    },
    /** 解绑键盘快捷键 */
    unbindKeyboardShortcuts() {
      if (this.keyHandler) {
        document.removeEventListener('keydown', this.keyHandler)
      }
    }
  }
}
</script>

<style scoped>
.review-detail-container {
  height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;
  padding: 0 !important;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.left-section {
  display: flex;
  align-items: center;
  gap: 15px;
}

.left-section .title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.center-section .progress-text {
  font-size: 14px;
  color: #606266;
}

.right-section {
  display: flex;
  gap: 10px;
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.editor-section {
  flex: 1;
  position: relative;
  background: #f5f5f5;
}

.onlyoffice-container {
  width: 100%;
  height: 100%;
}

.loading-container,
.error-container {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
  padding: 20px;
}

.form-section {
  width: 420px;
  background: #f5f7fa;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.form-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

/* 卡片样式 */
.form-section .el-card {
  margin-bottom: 8px;
  border-radius: 8px;
}

.form-section .el-card:last-child {
  margin-bottom: 0;
}

.form-section ::v-deep .el-card__header {
  padding: 10px 14px;
}

.form-section ::v-deep .el-card__body {
  padding: 10px 14px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.card-header i {
  font-size: 15px;
  color: #409EFF;
}

/* 学生信息卡片 */
.info-card ::v-deep .el-descriptions-item__label {
  width: 70px;
  font-weight: 500;
  background-color: #fafafa;
}

.info-card ::v-deep .el-descriptions-item__content {
  padding: 8px 12px;
}

.desc-value {
  color: #303133;
  font-weight: 500;
  font-size: 13px;
}

.reject-reason {
  color: #F56C6C;
  font-style: italic;
}

/* 评分卡片 */
.score-card ::v-deep .el-form-item {
  margin-bottom: 8px;
}

.score-tips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.score-tips .el-tag {
  flex: 1;
  min-width: 65px;
  text-align: center;
  font-size: 11px;
}

/* 评语卡片 */
.remark-card ::v-deep .el-textarea__inner {
  font-size: 13px;
  line-height: 1.5;
  border-radius: 4px;
}

.remark-card ::v-deep .el-form-item {
  margin-bottom: 8px;
}

.remark-templates {
  padding-top: 8px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 6px;
}

.remark-templates .el-button {
  flex: 1;
  padding: 6px 8px;
}

/* 操作卡片 */
.action-buttons {
  display: flex;
  flex-direction: row;
  gap: 8px;
}

.action-btn {
  flex: 1;
  min-width: 0;
  height: 32px;
  font-size: 13px;
  padding: 0;
}

.readonly-notice {
  margin-top: 8px;
  padding: 6px 10px;
  background-color: #FDF6EC;
  border: 1px solid #F5DAB1;
  border-radius: 4px;
  color: #E6A23C;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.readonly-notice i {
  font-size: 14px;
}

.shortcut-tips {
  margin-top: 8px;
}

.tips-content-compact {
  font-size: 11px;
  color: #909399;
  text-align: center;
  padding: 5px 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
  line-height: 1.5;
}

.tips-content-compact i {
  color: #409EFF;
  margin-right: 4px;
}

/* 滚动条美化 */
.form-scroll::-webkit-scrollbar {
  width: 6px;
}

.form-scroll::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.form-scroll::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.form-scroll::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
