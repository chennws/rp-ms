import request from '@/utils/request'

/**
 * 获取任务的提交列表
 * @param {Number} taskId 任务ID
 * @param {Object} params 查询参数
 */
export function getSubmitList(taskId, params) {
  return request({
    url: `/Task/submit/list/${taskId}`,
    method: 'get',
    params
  })
}

/**
 * 获取批改详情
 * @param {Number} submitId 提交记录ID
 */
export function getReviewDetail(submitId) {
  return request({
    url: `/Task/submit/${submitId}`,
    method: 'get'
  })
}

/**
 * 保存批改结果
 * @param {Object} data 批改数据
 */
export function saveReview(data) {
  return request({
    url: '/Task/submit/review',
    method: 'post',
    data
  })
}

/**
 * 批量导出成绩
 * @param {Number} taskId 任务ID
 */
/**
 * 获取提交ID列表（用于上一个/下一个导航）
 * @param {Number} taskId 任务ID
 */
export function getSubmitIdList(taskId) {
  return request({
    url: `/Task/submit/idList/${taskId}`,
    method: 'get'
  })
}

/**
 * 获取提交统计信息（批改列表页）
 * @param {Number} taskId 任务ID
 * @param {Object} params 查询参数
 */
export function getSubmitStats(taskId, params) {
  return request({
    url: `/Task/submit/stats/${taskId}`,
    method: 'get',
    params
  })
}

/**
 * 获取任务信息（用于批改列表页）
 * @param {Number} taskId 任务ID
 */
export function getTaskInfo(taskId) {
  return request({
    url: `/Task/${taskId}`,
    method: 'get'
  })
}
