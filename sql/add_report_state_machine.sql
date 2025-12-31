-- 添加状态机相关字段
-- 为 exp_task_submit 表添加打回原因字段
-- 添加打回原因字段
ALTER TABLE exp_task_submit
ADD COLUMN reject_reason VARCHAR(500) DEFAULT NULL COMMENT '打回原因' AFTER teacher_remark;

-- 修改 status 字段注释，支持更多状态
ALTER TABLE exp_task_submit
MODIFY COLUMN status CHAR(1) DEFAULT '0' COMMENT '状态（0草稿 1已提交 2批阅中 3已批阅 4已打回 5重新提交 6已归档）';

-- 添加提交次数字段（可选）
ALTER TABLE exp_task_submit
ADD COLUMN submit_count INT DEFAULT 1 COMMENT '提交次数' AFTER reject_reason;

-- 添加索引以提高查询性能
ALTER TABLE exp_task_submit
ADD INDEX idx_status (status);

-- 查看表结构
DESC exp_task_submit;
