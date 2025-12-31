import request from '@/utils/request'

/**
 * 状态机API
 */

/**
 * 触发状态转换
 * @param {*} submitId 提交ID
 * @param {*} trigger 触发器: SUBMIT, START_REVIEW, APPROVE, REJECT, RESUBMIT, ARCHIVE
 */
export function fireStateMachine(submitId, trigger) {
  return request({
    url: `/Task/submit/fire/${submitId}/${trigger}`,
    method: 'post'
  })
}

/**
 * 检查是否允许触发
 * @param {*} submitId 提交ID
 * @param {*} trigger 触发器
 */
export function canFire(submitId, trigger) {
  return request({
    url: `/Task/submit/canFire/${submitId}/${trigger}`,
    method: 'get'
  })
}

/**
 * 获取允许的操作列表
 * @param {*} submitId 提交ID
 */
export function getPermittedActions(submitId) {
  return request({
    url: `/Task/submit/actions/${submitId}`,
    method: 'get'
  })
}

/**
 * 提交报告
 * @param {*} submitId 提交ID
 */
export function submitReport(submitId) {
  return fireStateMachine(submitId, 'SUBMIT')
}

/**
 * 开始批阅
 * @param {*} submitId 提交ID
 */
export function startReview(submitId) {
  return fireStateMachine(submitId, 'START_REVIEW')
}

/**
 * 批阅通过
 * @param {*} submitId 提交ID
 */
export function approveReport(submitId) {
  return fireStateMachine(submitId, 'APPROVE')
}

/**
 * 打回报告
 * @param {*} submitId 提交ID
 * @param {*} reason 打回原因
 */
export function rejectReport(submitId, reason) {
  return request({
    url: `/Task/submit/reject/${submitId}`,
    method: 'post',
    params: { reason }
  })
}

/**
 * 重新提交
 * @param {*} submitId 提交ID
 */
export function resubmitReport(submitId) {
  return fireStateMachine(submitId, 'RESUBMIT')
}

/**
 * 归档
 * @param {*} submitId 提交ID
 */
export function archiveReport(submitId) {
  return fireStateMachine(submitId, 'ARCHIVE')
}
