import request from '@/utils/request'
import { saveAs } from 'file-saver'
import { blobValidate } from '@/utils/ruoyi'
import { Message, Loading } from 'element-ui'

let downloadLoadingInstance

// 查询实验任务列表
export function listTask(query) {
  return request({
    url: '/Task/list',
    method: 'get',
    params: query
  })
}

// 查询实验任务详细
export function getTask(taskId) {
  return request({
    url: '/Task/' + taskId,
    method: 'get'
  })
}

// 新增实验任务
export function addTask(data) {
  return request({
    url: '/Task',
    method: 'post',
    data: data
  })
}

// 修改实验任务
export function updateTask(data) {
  return request({
    url: '/Task',
    method: 'put',
    data: data
  })
}

// 删除实验任务
export function delTask(taskId) {
  return request({
    url: '/Task/' + taskId,
    method: 'delete'
  })
}

// 下载实验报告
export function downloadReport(url) {
  // 从URL中提取文件名
  const fileName = url.substring(url.lastIndexOf('/') + 1) || '实验报告'
  downloadLoadingInstance = Loading.service({ 
    text: "正在下载文件，请稍候", 
    spinner: "el-icon-loading", 
    background: "rgba(0, 0, 0, 0.7)" 
  })
  return request({
    url: '/Task/download',
    method: 'get',
    params: { url: url },
    responseType: 'blob'
  }).then(data => {
    const isBlob = blobValidate(data)
    if (isBlob) {
      const blob = new Blob([data])
      saveAs(blob, fileName)
    } else {
      Message.error('下载文件失败')
    }
    downloadLoadingInstance.close()
  }).catch(() => {
    Message.error('下载文件出现错误，请联系管理员！')
    downloadLoadingInstance.close()
  })
}

// 获取文档编辑器配置
// 接口地址: GET /Task/config
// 参数:
//   - fileUrl: 文件URL (例如: http://47.115.163.152:10001/winter/xxx.docx)
//   - mode: 编辑模式 (EDIT 或 VIEW，不区分大小写)
export function getConfig(fileUrl, mode) {
  // 确保 mode 参数格式正确（转换为大写，与API工具保持一致）
  const modeValue = (mode || 'EDIT').toUpperCase()

  console.log('调用 /Task/config 接口')
  console.log('参数:', {
    fileUrl: fileUrl,
    mode: modeValue
  })

  return request({
    url: '/Task/config',
    method: 'get',
    params: { 
      fileUrl: fileUrl,
      mode: modeValue  // EDIT 或 VIEW
    }
  })
}

// 提交任务
export function submitTask(taskId, fileUrl) {
  return request({
    url: '/Task/submit',
    method: 'post',
    data: {
      taskId: taskId,
      fileUrl: fileUrl
    }
  })
}

