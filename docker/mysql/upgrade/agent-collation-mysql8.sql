-- 一次性升级（仅 MySQL 8）：早期 08-agent.sql 把 stickybeak_agent 建成 utf8mb4_general_ci，
-- LangGraph checkpoint 的 json_table 查询会报 "Illegal mix of collations"。转换为 MySQL 8 默认的 utf8mb4_0900_ai_ci。
-- 执行前 agent 已启动过一次（表已由 setup 创建）。用法：
--   docker cp docker/mysql/upgrade/agent-collation-mysql8.sql sb-mysql:/tmp/ && docker exec sb-mysql sh -c "mysql -uroot -p<密码> < /tmp/agent-collation-mysql8.sql"
ALTER DATABASE stickybeak_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE stickybeak_agent;
ALTER TABLE checkpoint_migrations CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE checkpoints CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE checkpoint_blobs CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE checkpoint_writes CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
