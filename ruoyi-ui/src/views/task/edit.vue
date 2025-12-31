<template>
  <div class="app-container">
    <div class="editor-header">
      <div class="header-info">
        <h3>{{ taskName || '在线编辑' }}</h3>
        <el-button
          type="primary"
          icon="el-icon-check"
          @click="handleSubmit"
          :loading="submitting"
          :disabled="isReadOnly"
        >{{ isReadOnly ? '只读模式' : '提交任务' }}</el-button>
      </div>
    </div>
    <div v-if="loading" class="loading-container" v-loading="loading" element-loading-text="正在加载文档编辑器...">
    </div>
    <div v-if="error" class="error-container">
      <el-alert :title="error" type="error" :closable="false" show-icon></el-alert>
      <el-button type="primary" @click="retryInit" style="margin-top: 10px;">重试</el-button>
    </div>
    <!-- 编辑器容器必须始终存在，不能使用 v-show 隐藏，否则编辑器无法初始化 -->
    <div id="onlyoffice" class="editor-container" :style="{ display: loading || error ? 'none' : 'block' }"></div>
  </div>
</template>

<script>
import { getTask, submitTask, getConfig, createCopy, checkSubmitStatus } from "@/api/task/task"
import { getToken } from "@/utils/auth"

export default {
  name: "TaskEdit",
  data() {
    return {
      taskId: undefined,
      taskName: '',
      fileUrl: '', // 副本文件URL
      templateUrl: '', // 模板文件URL（仅用于参考）
      editor: null,
      submitting: false,
      documentKey: '',
      loading: true,
      error: null,
      // 轮询相关
      pollTimer: null,
      pollCount: 0,
      maxPollCount: 60, // 最多轮询60次（60秒）
      savingLoading: null, // 保存中的loading实例
      // 防止重复初始化
      isInitializing: false,
      // OnlyOffice 文档服务器地址，需要根据实际情况配置
      // 默认使用 http://47.115.163.152:9000 (与后端配置一致)
      documentServerUrl: process.env.VUE_APP_DOCUMENT_SERVER_URL || 'http://47.115.163.152:9001/web-apps/apps/api/documents/api.js',
      // 报告状态相关
      submitStatus: null, // 报告状态
      isReadOnly: false // 是否只读模式
    }
  },
  created() {
    this.taskId = this.$route.query.taskId
    this.taskName = this.$route.query.taskName || ''
    this.templateUrl = this.$route.query.fileUrl || ''

    if (!this.taskId) {
      this.$modal.msgError("任务ID不能为空")
      this.$router.go(-1)
      return
    }

    // documentKey 将在创建编辑器时从 config 中获取
  },
  mounted() {
    // 先创建副本，然后再初始化编辑器
    this.createCopyAndInitEditor()
  },
  beforeDestroy() {
    // 清理定时器
    if (this.pollTimer) {
      clearInterval(this.pollTimer)
      this.pollTimer = null
    }
    // 关闭loading
    if (this.savingLoading) {
      this.savingLoading.close()
      this.savingLoading = null
    }
    // 销毁编辑器
    if (this.editor) {
      this.editor.destroyEditor()
    }
  },
  methods: {
    /** 创建副本并初始化编辑器 */
    createCopyAndInitEditor() {
      this.loading = true
      this.error = null

      console.log('=== 开始创建实验报告副本 ===')
      console.log('taskId:', this.taskId)
      console.log('templateUrl:', this.templateUrl)

      createCopy(this.taskId).then(response => {
        if (response.code === 200 || response.code === '200') {
          // 获取副本文件URL
          this.fileUrl = response.data || response.msg
          // 获取documentKey（重要：用于保持文档版本一致性）
          this.documentKey = response.documentKey
          // 获取报告状态
          this.submitStatus = response.submitStatus || '0'

          console.log('=== 副本创建成功 ===')
          console.log('fileUrl:', this.fileUrl)
          console.log('documentKey:', this.documentKey)
          console.log('submitStatus:', this.submitStatus)

          // 验证数据
          if (!this.documentKey) {
            console.error('⚠️ 警告：documentKey为空，可能导致版本冲突！')
          }
          if (!this.fileUrl || this.fileUrl === '') {
            this.error = '副本文件URL为空'
            this.loading = false
            return
          }

          // ✅ 根据状态决定是否为只读模式
          // 只有草稿(0)和已打回(4)状态允许编辑，其他状态为只读
          if (this.submitStatus === '0' || this.submitStatus === '4') {
            this.isReadOnly = false
            console.log('✅ 允许编辑（草稿或已打回状态）')
          } else {
            this.isReadOnly = true
            console.log('⚠️ 只读模式（状态:', this.submitStatus, '）')
            this.$message({
              message: '当前报告状态不允许编辑，仅可查看',
              type: 'warning',
              duration: 3000
            })
          }

          // 使用副本URL初始化编辑器
          this.initEditor()
        } else {
          throw new Error(response.msg || response.message || '创建副本失败')
        }
      }).catch(error => {
        console.error('创建副本失败:', error)
        this.error = `创建副本失败: ${error.message || error.toString()}`
        this.loading = false
        this.$modal.msgError(this.error)
      })
    },

    /** 初始化编辑器 */
    initEditor() {
      // 防止重复初始化
      if (this.isInitializing) {
        console.warn('编辑器正在初始化中，跳过重复调用')
        return
      }

      this.isInitializing = true
      this.loading = true
      this.error = null

      console.log('开始初始化 OnlyOffice 编辑器')
      console.log('文档服务器地址:', this.documentServerUrl)
      console.log('任务ID:', this.taskId)
      console.log('文件URL:', this.fileUrl)

      // 检查容器元素是否存在
      this.$nextTick(() => {
        const container = document.getElementById('onlyoffice')
        if (!container) {
          this.error = '找不到编辑器容器元素'
          this.loading = false
          this.isInitializing = false
          console.error('找不到编辑器容器元素 #onlyoffice')
          return
        }

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
              this.error = 'OnlyOffice API 加载失败：未找到 DocsAPI.DocEditor'
              this.loading = false
              this.isInitializing = false
              console.error('OnlyOffice API 加载失败：未找到 DocsAPI.DocEditor')
            }
          }
          script.onerror = (error) => {
            this.error = `加载文档编辑器失败，请检查文档服务器配置。\n服务器地址: ${this.documentServerUrl}\n\n请确保：\n1. OnlyOffice 文档服务器已启动\n2. 服务器地址配置正确\n3. 网络连接正常`
            this.loading = false
            this.isInitializing = false
            console.error('加载 OnlyOffice API 脚本失败:', error)
            console.error('请检查文档服务器地址:', this.documentServerUrl)
          }
          document.head.appendChild(script)
        }
      })
    },
    /** 重试初始化 */
    retryInit() {
      this.error = null
      this.loading = true
      this.initEditor()
    },
    /** 初始化事件处理 */
    initEvents(config) {
      const self = this
      
      config.events = {
        // 应用程序被加载到浏览器中
        onAppReady: () => {
          console.log('[OnlyOffice] 应用程序已准备就绪')
        },
        
        // 文档被加载到文档编辑器中
        onDocumentReady: () => {
          console.log('[OnlyOffice] 文档已准备就绪')
          self.loading = false
          self.isInitializing = false // 初始化完成

          // 可选：获取当前文档内容（用于调试）
          try {
            if (self.editor && self.editor.createConnector) {
              const connector = self.editor.createConnector()
              if (connector && connector.executeMethod) {
                connector.executeMethod("GetCurrentWord", [], (word) => {
                  console.log(`[OnlyOffice] GetCurrentWord: ${word}`)
                })
              }
            }
          } catch (e) {
            console.warn('[OnlyOffice] 无法获取文档内容:', e)
          }
        },
        
        // 修改文档时调用的函数
        onDocumentStateChange: (event) => {
          console.log('[OnlyOffice] 文档状态改变:', event)
          // event.data 为 true 表示当前用户正在编辑文档
          if (event && event.data) {
            console.log('[OnlyOffice] 文档正在被编辑')
          }
        },
        
        // 发生错误时调用的函数
        onError: (error) => {
          console.error('[OnlyOffice] 编辑器错误:', error)
          const errorMsg = error?.message || error?.data || JSON.stringify(error)
          self.error = `编辑器错误: ${errorMsg}`
          self.loading = false
          self.$modal.msgError(`编辑器错误: ${errorMsg}`)
        },
        
        // 当使用旧的 document.key 值打开文档进行编辑时调用
        onOutdatedVersion: (event) => {
          console.error('⚠️⚠️⚠️ [OnlyOffice] 文档版本过期！⚠️⚠️⚠️')
          console.error('事件详情:', event)
          console.error('当前 documentKey:', self.documentKey)
          console.error('当前 fileUrl:', self.fileUrl)

          // 如果正在初始化，跳过（防止重复处理）
          if (self.isInitializing) {
            console.warn('编辑器正在初始化中，跳过版本过期处理')
            return
          }

          // 不要立即重新初始化，先给用户提示
          self.$modal.msgWarning('检测到文档版本冲突，正在尝试解决...')

          // 先彻底销毁当前编辑器
          console.log('开始销毁当前编辑器...')
          if (self.editor) {
            try {
              self.editor.destroyEditor()
            } catch (e) {
              console.warn('销毁编辑器时出现错误:', e)
            }
            self.editor = null
          }

          // 清空容器
          const container = document.getElementById('onlyoffice')
          if (container) {
            container.innerHTML = ''
          }

          // 保存当前的documentKey和fileUrl
          const currentKey = self.documentKey
          const currentFileUrl = self.fileUrl

          // 等待3秒后重新加载（给OnlyOffice时间清理缓存）
          console.log('等待3秒后重新加载，保持相同的documentKey:', currentKey)

          setTimeout(() => {
            // 恢复documentKey和fileUrl
            self.documentKey = currentKey
            self.fileUrl = currentFileUrl
            self.isInitializing = false // 确保可以重新初始化

            console.log('保持 documentKey 不变:', self.documentKey)
            console.log('重新初始化编辑器...')

            // 重新初始化
            self.initEditor()
          }, 3000)
        },
        
        // 通过 meta 命令更改文档的元信息时调用
        onMetaChange: (event) => {
          console.log('[OnlyOffice] 文档元信息改变:', event)
        },
        
        // 监听下载事件
        onDownloadAs: (event) => {
          console.log('[OnlyOffice] 下载文档:', event)
          const data = event?.data || {}
          console.log('下载格式:', data.format)
          console.log('下载URL:', data.url)
        }
      }
    },
    
    /** 创建编辑器 */
    createEditor() {
      if (!window.DocsAPI || !window.DocsAPI.DocEditor) {
        this.error = "文档编辑器API未加载"
        this.loading = false
        this.isInitializing = false
        console.error('文档编辑器API未加载')
        return
      }

      // 调用后端接口获取编辑器配置
      console.log('=== 开始获取编辑器配置 ===')
      console.log('fileUrl:', this.fileUrl)
      console.log('documentKey:', this.documentKey)
      console.log('调用 getConfig(fileUrl, mode, documentKey)')

      getConfig(this.fileUrl, 'edit', this.documentKey).then(response => {
        // Response类的code是字符串类型，需要兼容处理
        const code = response.code || response.data?.code
        const isSuccess = code === 200 || code === '200' || response.code === '200'
        if (!isSuccess || !response.data) {
          throw new Error(response.message || response.msg || '获取编辑器配置失败')
        }

        const config = response.data
        console.log('=== 获取到编辑器配置 ===')
        console.log('config.document.key:', config.document?.key)
        console.log('config.document.url:', config.document?.url)
        console.log('config.editorConfig.mode:', config.editorConfig?.mode)

        // 验证documentKey是否一致
        if (this.documentKey && config.document && config.document.key !== this.documentKey) {
          console.error('⚠️⚠️⚠️ 严重警告：documentKey不一致！⚠️⚠️⚠️')
          console.error('前端保存的documentKey:', this.documentKey)
          console.error('后端返回的documentKey:', config.document.key)
          console.error('这会导致"文档版本已过期"错误！')
        }

        // 保存documentKey，用于提交任务时触发保存
        if (config.document && config.document.key) {
          const returnedKey = config.document.key
          console.log('后端返回的 documentKey:', returnedKey)

          // 如果前端已有documentKey且不同，记录警告
          if (this.documentKey && this.documentKey !== returnedKey) {
            console.warn('⚠️ documentKey变化：', this.documentKey, ' -> ', returnedKey)
          }

          this.documentKey = returnedKey
          console.log('更新本地 documentKey:', this.documentKey)
        }

        // 更新用户信息（从当前登录用户获取）
        if (config.editorConfig && config.editorConfig.user) {
          config.editorConfig.user.id = this.getUserId()
          config.editorConfig.user.name = this.getUserName()
          console.log('更新用户信息:', config.editorConfig.user)
        }

        // ✅ 根据isReadOnly设置编辑器权限
        if (this.isReadOnly) {
          console.log('设置为只读模式')
          // 只读模式
          if (config.document) {
            config.document.permissions = {
              comment: false,
              download: true,
              edit: false,
              print: true,
              review: false
            }
          }
          if (config.editorConfig) {
            config.editorConfig.mode = 'view'
          }
        } else {
          console.log('设置为编辑模式')
          // 编辑模式
          if (config.document) {
            config.document.permissions = {
              comment: true,
              download: true,
              edit: true,
              print: true,
              review: false
            }
          }
          if (config.editorConfig) {
            config.editorConfig.mode = 'edit'
          }
        }

        // 初始化完整的事件监听（必须在创建编辑器之前）
        this.initEvents(config)
        
        // 确保容器元素存在且可见
        this.$nextTick(() => {
          const container = document.getElementById('onlyoffice')
          if (!container) {
            this.error = '找不到编辑器容器元素 #onlyoffice'
            this.loading = false
            this.isInitializing = false
            console.error('找不到编辑器容器元素 #onlyoffice')
            return
          }

          // 销毁旧的编辑器实例（如果存在）
          if (this.editor) {
            console.warn('检测到旧的编辑器实例，先销毁...')
            try {
              this.editor.destroyEditor()
            } catch (e) {
              console.warn('销毁旧编辑器时出现错误:', e)
            }
            this.editor = null
          }

          // 清空容器内容
          container.innerHTML = ''

          // 确保容器可见
          container.style.display = 'block'
          container.style.width = '100%'
          container.style.height = '100%'

          try {
            console.log('开始创建 OnlyOffice 编辑器实例')
            console.log('容器元素:', container)
            console.log('配置对象:', config)

            // 创建编辑器实例
            this.editor = new window.DocsAPI.DocEditor('onlyoffice', config)
            console.log('编辑器实例创建成功')

            // 如果编辑器创建成功但没有触发 onDocumentReady，设置一个超时检查
            setTimeout(() => {
              if (this.loading) {
                console.warn('编辑器创建后长时间未触发 onDocumentReady，可能存在问题')
                console.warn('请检查：1. 文档服务器是否可访问 2. 文件URL是否正确 3. 网络连接是否正常')
              }
            }, 10000) // 10秒超时

          } catch (error) {
            console.error('创建编辑器失败:', error)
            console.error('错误堆栈:', error.stack)
            this.error = `创建文档编辑器失败: ${error.message || error.toString()}`
            this.loading = false
            this.isInitializing = false
            this.$modal.msgError("创建文档编辑器失败，请查看控制台获取详细信息")
          }
        })
      }).catch(error => {
        console.error('获取编辑器配置失败:', error)
        console.error('错误类型:', typeof error)
        console.error('错误详情:', {
          error: error,
          message: error?.message,
          response: error?.response,
          data: error?.response?.data,
          status: error?.response?.status,
          toString: error?.toString()
        })
        
        // 提取更详细的错误信息
        // 注意：request.js 拦截器可能返回字符串 'error'，需要特殊处理
        let errorMessage = '获取编辑器配置失败'
        
        // 如果 error 是字符串（来自 request.js 拦截器）
        if (typeof error === 'string') {
          if (error === 'error') {
            errorMessage = '获取编辑器配置失败: 服务器返回错误，请检查后端服务是否正常运行'
          } else {
            errorMessage = `获取编辑器配置失败: ${error}`
          }
        }
        // 如果 error 是 Error 对象
        else if (error instanceof Error) {
          if (error.response) {
            // axios 错误对象
            const status = error.response.status
            const data = error.response.data
            if (data && data.msg) {
              errorMessage = `获取编辑器配置失败: ${data.msg} (状态码: ${status})`
            } else if (data && data.message) {
              errorMessage = `获取编辑器配置失败: ${data.message} (状态码: ${status})`
            } else if (status === 404) {
              errorMessage = `获取编辑器配置失败: 接口不存在 (404)，请检查后端服务是否启动`
            } else {
              errorMessage = `获取编辑器配置失败: HTTP ${status}`
            }
          } else if (error.message) {
            errorMessage = `获取编辑器配置失败: ${error.message}`
          } else {
            errorMessage = `获取编辑器配置失败: ${error.toString()}`
          }
        }
        // 其他情况
        else {
          errorMessage = `获取编辑器配置失败: ${JSON.stringify(error)}`
        }
        
        console.error('最终错误信息:', errorMessage)
        this.error = errorMessage
        this.loading = false
        this.$modal.msgError(errorMessage)
      })
    },
    /** 验证文件URL是否完整 */
    isValidFileUrl(url) {
      if (!url) return false
      // 完整的文件URL应该包含文件路径和扩展名
      // 例如: http://47.115.163.152:10001/winter/2025/12/27/xxx.docx
      // 如果只有基础地址（如 http://47.115.163.152:10001），则视为不完整
      const urlWithoutQuery = url.split('?')[0]
      // 检查是否包含文件路径（至少包含一个斜杠后的路径）
      const pathMatch = urlWithoutQuery.match(/:\d+\/(.+)/)
      if (!pathMatch || pathMatch[1].length === 0) {
        return false
      }
      // 检查是否包含文件扩展名
      const hasExtension = /\.(docx?|pptx?|xlsx?|pdf)$/i.test(urlWithoutQuery)
      return hasExtension
    },
    /** 获取文件扩展名 */
    getFileExtension(url) {
      if (!url) return 'docx'
      const match = url.match(/\.([^.]+)$/)
      return match ? match[1].toLowerCase() : 'docx'
    },
    /** 获取文档类型 */
    getDocumentType(extension) {
      const wordTypes = ['doc', 'docx', 'docm', 'dot', 'dotx', 'dotm', 'odt', 'fodt', 'ott', 'rtf', 'txt', 'html', 'htm', 'mht', 'pdf', 'djvu', 'fb2', 'epub', 'xps']
      const cellTypes = ['xls', 'xlsx', 'xlsm', 'xlt', 'xltx', 'xltm', 'ods', 'fods', 'ots', 'csv']
      const slideTypes = ['pps', 'ppsx', 'ppsm', 'ppt', 'pptx', 'pptm', 'pot', 'potx', 'potm', 'odp', 'fodp', 'otp']
      
      if (wordTypes.includes(extension)) {
        return 'word'
      } else if (cellTypes.includes(extension)) {
        return 'cell'
      } else if (slideTypes.includes(extension)) {
        return 'slide'
      }
      return 'word'
    },
    /** 获取用户ID */
    getUserId() {
      // 从 store 中获取用户ID，必须转换为字符串（OnlyOffice要求）
      const id = this.$store.getters.id || this.$store.state.user.id || 'student'
      return String(id)
    },
    /** 获取用户名 */
    getUserName() {
      // 从 store 中获取用户名
      return this.$store.getters.nickName || this.$store.getters.name || this.$store.state.user.nickName || this.$store.state.user.name || '学生'
    },
    /** 提交任务 */
    handleSubmit() {
      this.$modal.confirm('确认提交任务？提交后将触发文档保存，并记录提交信息。').then(() => {
        this.submitting = true
        console.log('开始提交任务, taskId:', this.taskId, 'documentKey:', this.documentKey, 'fileUrl:', this.fileUrl)
        // 提交任务时，传递任务ID、文档key和副本URL
        submitTask(this.taskId, this.documentKey, this.fileUrl).then(response => {
          console.log('提交响应:', response)

          // 检查是否是立即成功（文档没有修改的情况）
          if (response.msg === '提交成功') {
            // 文档没有修改，直接提交成功
            this.$modal.msgSuccess("提交成功")
            this.submitting = false
            setTimeout(() => {
              this.$router.go(-1)
            }, 1500)
          } else {
            // 文档有修改，需要等待保存
            // 显示保存中的loading
            this.savingLoading = this.$loading({
              lock: true,
              text: '正在保存到服务器，请稍候...',
              spinner: 'el-icon-loading',
              background: 'rgba(0, 0, 0, 0.7)'
            })
            // 开始轮询检查保存状态
            this.startPolling()
          }
        }).catch(() => {
          this.submitting = false
        })
      }).catch(() => {})
    },
    /** 开始轮询检查提交状态 */
    startPolling() {
      this.pollCount = 0
      // 立即检查一次
      this.checkStatus()
      // 每隔1秒检查一次
      this.pollTimer = setInterval(() => {
        this.pollCount++
        if (this.pollCount >= this.maxPollCount) {
          // 超时
          this.stopPolling()
          this.$modal.msgWarning("保存超时，请稍后刷新页面查看提交状态")
          this.submitting = false
        } else {
          this.checkStatus()
        }
      }, 1000)
    },
    /** 停止轮询 */
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      // 关闭loading
      if (this.savingLoading) {
        this.savingLoading.close()
        this.savingLoading = null
      }
    },
    /** 检查提交状态 */
    checkStatus() {
      checkSubmitStatus(this.taskId).then(response => {
        console.log('提交状态:', response)
        if (response.code === 200 || response.code === '200') {
          const status = response.status
          if (status === 'success') {
            // 提交成功
            this.stopPolling()
            this.$modal.msgSuccess("提交成功")
            this.submitting = false
            setTimeout(() => {
              this.$router.go(-1)
            }, 1500)
          } else if (status === 'pending') {
            // 还在保存中，继续轮询
            console.log('正在保存中，继续等待...')
          } else {
            // 其他状态
            this.stopPolling()
            this.$modal.msgWarning(response.message || "提交状态异常")
            this.submitting = false
          }
        }
      }).catch(error => {
        console.error('检查提交状态失败:', error)
        // 继续轮询，不中断
      })
    }
  }
}
</script>

<style scoped>
.app-container {
  height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;
}

.editor-header {
  padding: 15px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.header-info h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.editor-container {
  flex: 1;
  width: 100%;
  background: #f5f5f5;
}

.loading-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
}

.error-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: #f5f5f5;
}
</style>

