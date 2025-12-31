-- 为exp_task_submit表添加document_key和submit_pending字段
-- document_key: 存储OnlyOffice文档的唯一标识key，解决文档版本冲突问题
-- submit_pending: 标记是否正在提交中，确保callback成功后才显示提交成功

ALTER TABLE `exp_task_submit`
ADD COLUMN `document_key` varchar(128) DEFAULT NULL COMMENT 'OnlyOffice文档唯一标识key' AFTER `file_url`,
ADD COLUMN `submit_pending` tinyint(1) DEFAULT 0 COMMENT '是否正在提交中(0否 1是)' AFTER `document_key`;
