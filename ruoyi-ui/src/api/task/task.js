import request from '@/utils/request'

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

