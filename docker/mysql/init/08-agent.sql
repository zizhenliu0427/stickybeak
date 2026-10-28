-- AI 助手的对话状态（LangGraph checkpoint）。表由 agent 启动时自动创建（langgraph-checkpoint-mysql 的 setup）
-- 不要写死 COLLATE utf8mb4_general_ci：checkpoint 查询里用了 json_table，MySQL 8 中它的输出列是 utf8mb4_0900_ai_ci，
-- 与 general_ci 的表列比较会报 "Illegal mix of collations"，每次对话读存档都失败。用库的默认排序规则即可。
-- 已有数据卷按 docker/mysql/upgrade/agent-collation-mysql8.sql 转换一次。
CREATE DATABASE IF NOT EXISTS stickybeak_agent DEFAULT CHARACTER SET utf8mb4;
