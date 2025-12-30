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
//   - documentKey: 自定义文档key（可选，用于保持文档版本一致性）
export function getConfig(fileUrl, mode, documentKey) {
  // 确保 mode 参数格式正确（转换为大写，与API工具保持一致）
  const modeValue = (mode || 'EDIT').toUpperCase()

  const params = {
    fileUrl: fileUrl,
    mode: modeValue  // EDIT 或 VIEW
  }

  // 如果传入了documentKey则添加到参数中
  if (documentKey) {
    params.documentKey = documentKey
  }

  console.log('调用 /Task/config 接口')
  console.log('参数:', params)

  return request({
    url: '/Task/config',
    method: 'get',
    params: params
  })
}

// 提交任务
// 接口地址: POST /Task/submit
// 参数:
//   - taskId: 任务ID
//   - documentKey: OnlyOffice文档key（用于触发保存）
//   - fileUrl: 副本文件URL
export function submitTask(taskId, documentKey, fileUrl) {
  return request({
    url: '/Task/submit',
    method: 'post',
    data: {
      taskId: taskId,
      documentKey: documentKey,
      fileUrl: fileUrl
    }
  })
}

// 创建副本
// 学生打开编辑器前，从模板文件创建一个副本
export function createCopy(taskId) {
  return request({
    url: '/Task/createCopy',
    method: 'post',
    data: {
      taskId: taskId
    }
  })
}

// 检查任务提交状态
// 用于轮询检查callback是否已成功保存文件到MinIO
export function checkSubmitStatus(taskId) {
  return request({
    url: '/Task/checkSubmitStatus',
    method: 'get',
    params: {
      taskId: taskId
    }
  })
}
