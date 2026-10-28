# Nacos 多环境配置与动态刷新（故事线 9.4）

> 演示：`node scripts/demo-nacos-multi-env.cjs`（发布 dev 配置 → 观察热更新 → 建 prod 命名空间）
> 实测日期：2026-09-17

## 机制

- 各服务通过 `spring.config.import: optional:nacos:...` 从 Nacos 读配置
- **多环境双层叠加**：
  - **profile**：`stickybeak-product.yml`（共享）+ `stickybeak-product-dev.yml`（`spring.profiles.active` 决定）
  - **命名空间隔离**：`NACOS_NAMESPACE` 环境变量（public / prod）——注意：discovery 也按命名空间隔离，
    prod 命名空间里的实例对 public 命名空间的网关不可见（这才是真正的环境隔离）
- **动态刷新**：Nacos 配置变更 → 客户端监听推送 → `@RefreshScope` Bean 重建，免重启

## 关键坑（实测）

1. **SCA 2023 的 config import 不会自动补 `-{profile}` 变体**——必须显式声明：
   ```yaml
   spring:
     config:
       import:
         - optional:nacos:stickybeak-product.yml
         - optional:nacos:stickybeak-product-${spring.profiles.active:dev}.yml
   ```
   验证：服务日志应出现两条 subscribe（`...yml` + `...-dev.yml`）
2. **dataId 必须在服务启动时已存在**（哪怕为空），否则 optional 导入跳过且不会订阅后续创建
3. **Windows 上 `curl --data-urlencode content@file` 用控制台代码页读文件，中文必乱码**——
   用 node 先 `encodeURIComponent` 再以纯 ASCII form body 发送（见演示脚本 `publishConfig`）

## 演示端点

`GET /api/products/config-demo`（product 服务）返回当前 banner / TTL / activeProfiles / namespace。

## 实测记录

| 步骤 | 结果 |
|---|---|
| 发布 `stickybeak-product-dev.yml` v1 | 端点显示 dev 横幅 v1，TTL 60 |
| 改发 v2（不重启） | 数秒内端点显示 v2，TTL 120（动态刷新 ✓） |
| 建 `prod` 命名空间 + 发布 prod 配置 | product 以 `NACOS_NAMESPACE=prod SPRING_PROFILES_ACTIVE=prod` 重启后显示 prod 横幅 + TTL 300 |
| 隔离证据 | prod 命名空间实例注册后，public 命名空间网关路由直接 503（发现层即隔离） |

## 生产建议

- 三个环境：dev（public 命名空间）/ staging / prod（独立命名空间或独立 Nacos 集群）
- 密钥类配置永远不要进 Nacos 明文——走环境变量或 KMS（本项目 JWT_SECRET/Stripe key 已是 env 注入）
- 配置变更审计：生产环境开启 Nacos 认证（`NACOS_AUTH_ENABLE=true`）并限制控制台写权限
