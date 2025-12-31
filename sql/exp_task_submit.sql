-- ----------------------------
-- 学生提交记录表
-- ----------------------------
DROP TABLE IF EXISTS `exp_task_submit`;
CREATE TABLE `exp_task_submit` (
  `submit_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '提交记录ID',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '学生ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '学生姓名',
  `file_url` varchar(500) DEFAULT NULL COMMENT '提交的文件URL',
  `document_key` varchar(128) DEFAULT NULL COMMENT 'OnlyOffice文档唯一标识key',
  `submit_pending` tinyint(1) DEFAULT 0 COMMENT '是否正在提交中(0否 1是)',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待批阅 1已批阅）',
  `score` decimal(5,2) DEFAULT NULL COMMENT '分数',
  `teacher_remark` varchar(500) DEFAULT NULL COMMENT '教师评语',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`submit_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='学生提交记录表';
