# StickyBeak 演示流程 / Demo Walkthrough

> 中英文对照。每一步先中文、后英文。整套演示约 12–15 分钟，可以按时间删减章节。
> Chinese and English side by side: each step is given in Chinese first, then English. The full walkthrough takes about 12–15 minutes; drop sections to fit your time slot.

---

## 0. 演示前准备 / Before the demo

### 0.1 启动 / Start everything

1. 打开 Docker Desktop 和 Ollama（本地模型 `qwen3.8`、向量模型 `bge-m3` 已下载）。
   Start Docker Desktop and Ollama (the local chat model `qwen3.8` and the embedding model `bge-m3` are already pulled).
2. 启动全栈：
   Start the full stack:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   ```
3. 确认 AI 助手就绪，返回里应有 `"mcpConnected":true` 和模型列表：
   Check that the assistant is ready; the response should contain `"mcpConnected":true` and the model list:
   ```bash
   curl http://localhost/api/agent/health
   ```
4. Docker Desktop 重启后容器会全部退出，再执行一次第 2 步即可。
   After Docker Desktop restarts, all containers exit; just run step 2 again.

### 0.2 提前打开的标签页 / Tabs to open in advance

| 标签 / Tab | 地址 / URL | 账号 / Login |
|---|---|---|
| 商城 / Storefront | http://localhost | 现场注册 / register live |
| 管理端 / Admin | http://localhost/admin | `admin@stickybeak.au` / `Admin123!` |
| RabbitMQ | http://localhost:15672 | `guest` / `guest` |
| MailHog（邮件 / email） | http://localhost:8025 | — |
| SkyWalking | http://localhost:8088 | — |
| Nacos | http://localhost:8848/nacos | `nacos` / `nacos` |

### 0.3 选模型 / Pick the model

- 默认 `LLM_CHAIN=openai,ollama`：先用云端 `gpt-6-luna`，不可用时降级到本地。首字约 4 秒。
  Default `LLM_CHAIN=openai,ollama`: cloud `gpt-6-luna` first, falling back to the local model. First token in about 4 s.
- 想现场更快：把 `docker/.env` 改成 `LLM_CHAIN=ollama,openai`（本地首字约 1 秒），然后：
  For a snappier live demo, set `LLM_CHAIN=ollama,openai` in `docker/.env` (about 1 s to first token locally), then:
  ```bash
  docker compose -f docker/docker-compose.yml up -d --force-recreate agent
  ```

### 0.4 可选：准备已支付订单和邮件 / Optional: seed paid orders and emails

本地没有配 Stripe，页面上付不了款。想演示"支付成功 → 发邮件 → 发货"，先跑一次全链路脚本，它会用模拟回调生成已支付订单和确认邮件：
Stripe isn't configured locally, so you can't pay in the UI. To show "paid → email → ship", run the end-to-end script once; it uses the mock webhook to create paid orders and confirmation emails:

```bash
node scripts/test-sprint8-e2e.js
```

---

## 1. 开场（30 秒）/ Opening (30 s)

> **讲解 / Talking points**
> StickyBeak 是一个卖澳洲主题冰箱贴的跨境电商：悉尼火车站牌、超市价签、大学巴士站牌、澳洲小鸟等 10 个系列，价格用澳元，也可以按人民币显示和支付。后端是 Spring Cloud Alibaba 微服务，前端是 React，另外有一个 Python 写的 AI 购物助手和一个 MCP Server。
> StickyBeak is a cross-border shop selling Australian-themed fridge magnets: Sydney train signs, supermarket price tags, university bus-stop signs, native birds and more, in 10 series. Prices are in AUD and can also be shown and paid in CNY. The backend is Spring Cloud Alibaba microservices, the frontend is React, and there is an AI shopping assistant and an MCP server written in Python.

---

## 2. 买家购物（3 分钟）/ Shopping as a buyer (3 min)

1. **首页与分类**：从首页点进一个系列（例如"火车"），展示商品列表和筛选。
   **Home and categories**: open a series from the home page (e.g. "Train") and show the product list and filters.
2. **搜索**：在搜索框输入 `UNSW` 或 `火车`，结果按相关度排序、名称高亮。
   **Search**: type `UNSW` or `火车` in the search box; results are ranked by relevance with the name highlighted.
   > 搜索走 Elasticsearch + IK 中文分词；ES 挂了会自动回退到 MySQL。
   > Search runs on Elasticsearch with the IK Chinese analyzer, and falls back to MySQL if ES is down.
3. **切换语言和币种**：右上角切到 English、切到 CNY，价格随汇率换算。
   **Switch language and currency**: switch to English and to CNY at the top right; prices are converted with the current rate.
4. **商品详情**：图集、规格、库存；加入购物车，右上角角标更新。
   **Product page**: gallery, options and stock; add to cart and watch the cart badge update.
5. **注册登录**：注册一个新账号。登录前加入购物车的商品会合并到账号里。
   **Sign up**: register a new account. Items added before logging in are merged into the account's cart.
6. **下单**：购物车 → 结算 → 填地址 → 提交订单。
   **Checkout**: cart → checkout → address → place order.
   > **注意 / Note**：没有 Stripe key 时走模拟支付，Docker 全栈默认回到 `http://localhost/checkout/success...`；其他环境通过 `STOREFRONT_URL` 配置商城地址。成功页显示订单号，状态仍是 **PENDING**，因为模拟回跳不会真的付款。 / Mock checkout returns to the configured storefront; the order stays pending until a payment callback is processed.
   > Without a Stripe key the mock payment provider sends the browser to `localhost:5173/checkout/success...`, which won't load. Delete `:5173` from the address and press Enter; the success page shows the order number with status **PENDING** (the mock provider doesn't actually take payment).
7. **看 RabbitMQ**：切到 RabbitMQ → Queues，`order.delay.queue` 里多了一条消息，30 分钟后到期会自动关闭这笔未支付订单。
   **Check RabbitMQ**: in RabbitMQ → Queues, `order.delay.queue` now holds one more message; when it expires after 30 minutes the unpaid order is closed automatically.

> **讲解 / Talking points**
> 下单时库存有三道防线：Redis Lua 原子预扣 → 按商品加 Redisson 分布式锁 → MySQL 条件更新 `stock >= qty`。扣库存和写订单在两个库里，用 Seata AT 全局事务保证要么一起成功、要么一起回滚。
> Stock is protected three ways at checkout: an atomic Redis Lua pre-deduction, a per-product Redisson lock, and a MySQL conditional update `stock >= qty`. Stock and orders live in different databases; a Seata AT global transaction makes them commit or roll back together.

---

## 3. AI 购物助手（4 分钟）/ AI shopping assistant (4 min)

登录状态下点右下角的聊天按钮，按顺序输入：
While logged in, open the chat button at the bottom right and type these in order:

| 输入 / Type | 展示什么 / What it shows |
|---|---|
| 有没有悉尼火车站牌的冰箱贴？ | 推荐商品，商品名是可点的链接。<br>Recommends products; names are clickable links. |
| 把第一个加入购物车 | 弹出确认卡片；点"确认"后才加购，购物车角标更新。<br>A confirmation card appears; the item is added only after you click Confirm, and the cart badge updates. |
| 退货期限是多久？ | 从售后知识库回答，带 `[1]` 引用和来源。<br>Answers from the help articles with `[1]` citations and sources. |
| 能开发票吗？ | 知识库没有，明确说不知道并给客服邮箱，不编造。<br>Not covered, so it says it doesn't know and gives the support email instead of making something up. |
| Do you have a physical store? | 英文提问用英文回答（Paddy's Markets 的合作摊位）。<br>Answers in English (the partner stall at Paddy's Markets). |
| 我的订单状态怎么样 | 只查当前用户自己的订单。<br>Looks up only the signed-in user's orders. |
| 取消刚才那个订单 | 待支付订单可取消，同样先弹确认卡片。<br>Pending orders can be cancelled, again behind a confirmation card. |

然后**刷新页面**：对话和还没确认的卡片都会恢复。
Then **refresh the page**: the conversation and any unconfirmed card come back.

> **讲解 / Talking points**
> 助手用 LangGraph 编排：先识别意图，再交给导购、订单、售后或闲聊节点，每个节点只能用自己的工具。用户身份来自网关，模型改不了；加购和取消订单这类写操作一定要用户在卡片上确认。售后问答是 RAG：向量 + 关键词两路检索、RRF 融合、相似度阈值过滤，没有依据就说不知道。模型可以换：云端 GPT、通义、DeepSeek、Gemini、本地 Ollama，按顺序降级。
> The assistant is orchestrated with LangGraph: it classifies the intent, then hands off to a shopping, orders, after-sales or chit-chat node, each limited to its own tools. The user's identity comes from the gateway and the model can't change it; write actions such as adding to cart or cancelling an order always need the user's confirmation on a card. After-sales answers use RAG: vector and keyword search, RRF fusion and a similarity threshold, and it says "I don't know" when there's no source. Models are swappable: cloud GPT, Qwen, DeepSeek, Gemini or local Ollama, with fallback in order.

**可选 / Optional**
- 连续快速发 11 条消息，第 11 条返回 429（每用户每分钟 10 次）。
  Send 11 messages in quick succession; the 11th returns 429 (10 requests per user per minute).
- 把 `LLM_CHAIN` 里的模型都去掉并重建 agent，助手提示"暂时不可用"，而商城浏览、下单照常。
  Remove every model from `LLM_CHAIN` and recreate the agent: the assistant says it's temporarily unavailable while browsing and checkout keep working.

---

## 4. 管理端（2 分钟）/ Admin console (2 min)

用 `admin@stickybeak.au` / `Admin123!` 登录 http://localhost/admin。
Log in to http://localhost/admin with `admin@stickybeak.au` / `Admin123!`.

1. **仪表盘**：累计销售额、订单数、待发货、库存预警，7 / 30 / 90 天销售趋势图，热销前 10 商品。
   **Dashboard**: total GMV, order count, orders awaiting shipment, stock alerts, a 7 / 30 / 90-day sales trend chart and the top 10 best sellers.
2. **订单管理**：找一笔 **PAID** 订单（0.4 的脚本会生成）→ 备货 → 填物流单号发货 → 完成。每一步只显示当前状态允许的按钮。
   **Orders**: pick a **PAID** order (the script in 0.4 creates some) → Processing → Ship with a tracking number → Complete. Only the buttons allowed for the current status are shown.
   > 状态机：`pending → paid → processing → shipped → completed`，`pending → cancelled`，`paid / processing → refunding → refunded`。非法流转直接拒绝，并发更新用条件 UPDATE 防止覆盖。
   > State machine: `pending → paid → processing → shipped → completed`, `pending → cancelled`, `paid / processing → refunding → refunded`. Illegal transitions are rejected, and concurrent updates use a conditional UPDATE so they can't overwrite each other.
3. **商品管理**：新建一个商品并上传图片（或视频），文件存进 MinIO（http://localhost:9001 可以看到）。保存后到商城搜索它，几秒内就能搜到。
   **Products**: create a product and upload an image (or video); files go to MinIO (visible at http://localhost:9001). After saving, search for it in the storefront; it shows up within seconds.

---

## 5. 系统内部（2 分钟）/ Under the hood (2 min)

1. **MailHog**：打开 http://localhost:8025，能看到支付成功后发出的订单确认邮件（由 `order.paid` 事件触发）。
   **MailHog**: at http://localhost:8025 you can see the order confirmation emails sent after payment (triggered by the `order.paid` event).
2. **SkyWalking**：打开 http://localhost:8088 → 拓扑图，看到 7 个 Java 服务（含网关）和 Python 助手、MCP 的调用关系；在链路里找一次结账，能看到 gateway → order → cart / product / payment 的完整耗时。
   **SkyWalking**: open http://localhost:8088 → Topology to see the 7 Java services (gateway included), the Python assistant and the MCP server; find a checkout trace to see the full gateway → order → cart / product / payment timeline.
3. **Nacos**：服务列表里所有服务都已注册；配置中心里是各服务的配置。
   **Nacos**: every service is registered in the service list; per-service config lives in the config center.
4. **报错带链路号**：接口出 5xx 时，页面提示里带 `X-Trace-Id`，直接拿去 SkyWalking 查。
   **Errors carry a trace ID**: when an API returns 5xx, the error message shows the `X-Trace-Id`, which you can look up directly in SkyWalking.

---

## 6. MCP：给外部 AI 客户端用（1 分钟）/ MCP for external AI clients (1 min)

1. 商城 → 个人中心 → "AI 客户端接入（MCP）" → 生成令牌，页面给出可复制的客户端配置。
   Storefront → Profile → "AI client access (MCP)" → generate a token; the page shows a client config to copy.
2. 用命令行演示（把 `<令牌>` 换成刚生成的）：
   Demo it from the command line (replace `<token>` with the one you just generated):
   ```bash
   docker exec sb-agent python -m stickybeak_agent.mcp_demo --url http://gateway:8080/api/mcp --token <token>
   ```
   会依次调用搜索商品、售后检索、查我的订单等工具。
   It calls the product search, help-article search and my-orders tools in turn.
3. 在个人中心撤销令牌后再跑一次，返回 401。
   Revoke the token in the profile and run it again: it returns 401.

> **讲解 / Talking points**
> 商城的只读能力（商品、售后说明、本人订单）用 Model Context Protocol 暴露，自家助手和 Cursor、Cherry Studio 等外部客户端用的是同一套工具。令牌 30 天有效、只能访问 `/api/mcp`、可以随时撤销。
> The shop's read-only capabilities (products, help articles, the user's own orders) are exposed over the Model Context Protocol; our assistant and external clients such as Cursor or Cherry Studio use the same tools. Tokens last 30 days, only work on `/api/mcp`, and can be revoked at any time.

---

## 7. 常见问题速答 / Quick answers to common questions

| 问题 / Question | 回答要点 / Key points |
|---|---|
| 怎么防超卖？<br>How do you prevent overselling? | Redis Lua 预扣 + Redisson 锁 + MySQL 条件更新，三道防线；跨库一致由 Seata AT 保证。<br>Redis Lua pre-deduction, a Redisson lock and a MySQL conditional update; Seata AT keeps the two databases consistent. |
| 支付回调重复怎么办？<br>What about duplicate payment callbacks? | Webhook 验签，Stripe 事件 ID 唯一键去重，金额和币种再核对一遍。<br>Verify the webhook signature, dedupe on a unique Stripe event ID, and re-check amount and currency. |
| 未支付订单怎么关？<br>How are unpaid orders closed? | RabbitMQ 延迟队列 30 分钟到期关单并归还库存，另有定时扫描兜底。<br>A RabbitMQ delay queue closes the order and returns stock after 30 minutes, with a scheduled sweep as a backstop. |
| AI 会不会乱操作？<br>Can the AI take actions on its own? | 写操作必须用户确认；身份由网关注入，模型拿不到别人的数据；工具结果只当数据，不当指令。<br>Writes need user confirmation; identity is injected by the gateway so the model can't reach other users' data; tool output is treated as data, never as instructions. |
| AI 答错政策怎么办？<br>What if the AI gets a policy wrong? | 只依据知识库回答并附引用，检索分数不够就说不知道；有 52 条评测集，改提示词后必跑。<br>It answers only from the help articles with citations and says "I don't know" when retrieval scores are too low; a 52-case eval set is rerun after every prompt change. |

---

## 附：出问题时 / Troubleshooting

| 现象 / Symptom | 处理 / Fix |
|---|---|
| 页面打不开、接口全 502<br>Pages don't load, APIs return 502 | 容器可能退出了：`docker compose -f docker/docker-compose.yml up -d`<br>Containers may have exited: rerun the compose command. |
| 结账后跳到 `localhost:5173` 打不开<br>Checkout redirects to `localhost:5173` | 把地址里的 `:5173` 删掉。<br>Delete `:5173` from the address. |
| 助手回复 429<br>Assistant returns 429 | 每用户每分钟 10 次，等一分钟或换个账号。<br>10 requests per user per minute; wait a minute or use another account. |
| 助手"暂时不可用"<br>Assistant "temporarily unavailable" | 看 `curl http://localhost/api/agent/health`；检查 Ollama 是否在运行、`docker/.env` 里的 key。<br>Check the health endpoint, whether Ollama is running, and the keys in `docker/.env`. |
| 助手回答很慢<br>Assistant is slow | 改用 `LLM_CHAIN=ollama,openai`（见 0.3）。<br>Switch to `LLM_CHAIN=ollama,openai` (see 0.3). |
