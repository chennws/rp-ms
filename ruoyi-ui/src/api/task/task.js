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


