# 百货礼盒拆包退货与组件复原系统

顾客退回礼盒后，柜台调出原订单，逐件核对商品、配件、赠品（完好/缺失/破损），
按售出组合价分摊计算扣减与退款，顾客确认后办理退款并按核对结果复原库存。

## 一键启动

```bash
./start.sh
```

启动后访问 <http://localhost:8080>（前后端同源；SQLite 库在 `backend/data/giftbox.db`，
检查照片在 `backend/data/photos/`）。

- 无系统 Java/Maven 时自动使用仓库内 `.toolchain/`（Temurin JDK 21 + Maven 3.9）。
- 演示订单：`ORD-1001`（锦绣茶礼 ¥588）、`ORD-1002`（缤纷果干礼盒 ¥199）、`ORD-1003`～`ORD-1005`。

开发模式：后端 `cd backend && mvn quarkus:dev`，前端 `cd frontend && npm run dev`（Vite 代理到 8080）。

## 业务规则

- **分摊**：扣减按售出时的组合价分摊（权重 = 组件零售标价 × 数量，最大余数法，
  分摊之和严格等于组合价）；`退款金额 = 组合价 − 扣减明细`，报价页分组展示
  赠品缺失、商品/配件破损等类别及扣减依据，顾客确认后才办理。
- **库存复原**：只有组件齐全、无破损且检查合格的礼盒才恢复整盒库存；
  否则拆散组件各按状态入库（完好→可售散件，破损→破损散件，缺失→不入库），
  散件绝不能算成完整礼盒。
- **幂等与并发**：每个订单行只允许一笔退货单；建单按幂等键去重；
  退款单（退货单/订单行/回执号唯一）、模拟支付台账（幂等键唯一）、
  库存流水（退货单+SKU+状态唯一）层层兜底，乐观锁处理双柜台并发——
  两个柜台同时办理同一订单只会产生一笔有效退款和一次库存调整，
  重复退款回执返回同一回执，不再退款也不再增加库存。
- **留痕**：检查照片（本地磁盘保存）与主管意见挂在退货单下，随原订单回看处理过程。

## 技术栈

- 后端：Java 21 + Quarkus 3（RESTEasy Reactive）+ Hibernate ORM (Panache) + SQLite
  （xerial JDBC + hibernate-community-dialects，`transaction_mode=IMMEDIATE` 串行化写事务）
- 前端：Svelte 4 + Vite，构建产物由 Quarkus 静态托管
- 支付：模拟网关（`PaymentGateway`，按幂等键落台账去重）

## 验证

```bash
cd backend && mvn test
```

- `MoneyAllocationTest`：分摊总和 = 组合价、最大余数法、全扣/部分扣减的金额
- `ReturnFlowTest`：
  - 部分退货 → 退款 = 组合价 − 扣减，散件按状态入库，整盒库存不变（无未复原的整盒）
  - 完整合格 → 整盒 +1、全额退款
  - 重复确认/重复回执 → 同一回执，退款/支付/库存流水均不增加
  - 8 线程双柜台并发确认 → 全部拿到同一回执，仅一笔退款、一次支付、一套库存流水
  - 建单幂等键去重、订单行唯一约束（重复建单 409）

## API 摘要

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/orders` / `/api/orders/{orderNo}` | 订单列表 / 订单详情（含退货单号） |
| POST | `/api/returns` | 建退货单（`orderItemId`、`counterId`、`idempotencyKey`） |
| GET | `/api/returns/{id}` | 退货单详情（核对行、扣减、退款、照片、意见） |
| PUT | `/api/returns/{id}/lines` | 逐件登记实收/完好/破损 + 是否合格 |
| POST | `/api/returns/{id}/quote` | 生成扣减报价（快照） |
| POST | `/api/returns/{id}/confirm` | 顾客确认 → 退款 + 库存复原（幂等） |
| POST | `/api/returns/{id}/photos` | 上传检查照片（multipart） |
| POST | `/api/returns/{id}/notes` | 添加主管意见 |
| GET | `/api/photos/{id}` | 照片文件 |
| GET | `/api/inventory` | 整盒/散件库存 + 最近流水 |
| GET | `/api/stats` | 退货单/退款/支付/库存流水计数 |
