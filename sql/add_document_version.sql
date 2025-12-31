-- 为exp_task_submit表添加文档版本号字段
-- 用于解决OnlyOffice文档版本冲突问题

-- 添加document_version字段
ALTER TABLE exp_task_submit
ADD COLUMN document_version INT DEFAULT 1 COMMENT '文档版本号，每次保存后自动+1';

-- 为已存在的记录设置初始版本号
UPDATE exp_task_submit
SET document_version = 1
WHERE document_version IS NULL;

-- 添加索引以提高查询性能
CREATE INDEX idx_task_user ON exp_task_submit(task_id, user_id);
