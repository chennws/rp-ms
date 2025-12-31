/**
 * 实验报告状态常量
 * 对应后端 ReportState 枚举
 */
export const ReportState = {
  DRAFT: '0',           // 草稿
  SUBMITTED: '1',       // 已提交
  REVIEWING: '2',       // 批阅中
  REVIEWED: '3',        // 已批阅
  REJECTED: '4',        // 已打回
  RESUBMITTED: '5',     // 重新提交
  ARCHIVED: '6'         // 已归档
}

/**
 * 状态描述映射
 */
export const ReportStateDesc = {
  [ReportState.DRAFT]: '草稿',
  [ReportState.SUBMITTED]: '已提交',
  [ReportState.REVIEWING]: '批阅中',
  [ReportState.REVIEWED]: '已批阅',
  [ReportState.REJECTED]: '已打回',
  [ReportState.RESUBMITTED]: '重新提交',
  [ReportState.ARCHIVED]: '已归档'
}

/**
 * 状态标签类型映射（Element UI Tag type）
 */
export const ReportStateType = {
  [ReportState.DRAFT]: 'info',
  [ReportState.SUBMITTED]: 'primary',
  [ReportState.REVIEWING]: 'warning',
  [ReportState.REVIEWED]: 'success',
  [ReportState.REJECTED]: 'danger',
  [ReportState.RESUBMITTED]: 'primary',
  [ReportState.ARCHIVED]: 'info'
}

/**
 * 触发器常量
 * 对应后端 ReportTrigger 枚举
 */
export const ReportTrigger = {
  SUBMIT: 'SUBMIT',               // 提交报告
  START_REVIEW: 'START_REVIEW',   // 开始批阅
  APPROVE: 'APPROVE',             // 批阅通过
  REJECT: 'REJECT',               // 打回
  RESUBMIT: 'RESUBMIT',           // 重新提交
  ARCHIVE: 'ARCHIVE'              // 归档
}

/**
 * 触发器描述映射
 */
export const ReportTriggerDesc = {
  [ReportTrigger.SUBMIT]: '提交',
  [ReportTrigger.START_REVIEW]: '开始批阅',
  [ReportTrigger.APPROVE]: '批阅通过',
  [ReportTrigger.REJECT]: '打回',
  [ReportTrigger.RESUBMIT]: '重新提交',
  [ReportTrigger.ARCHIVE]: '归档'
}

/**
 * 获取状态描述
 */
export function getStateDesc(state) {
  return ReportStateDesc[state] || '未知'
}

/**
 * 获取状态标签类型
 */
export function getStateType(state) {
  return ReportStateType[state] || 'info'
}

/**
 * 获取触发器描述
 */
export function getTriggerDesc(trigger) {
  return ReportTriggerDesc[trigger] || '未知'
}
