# StarX-Car 预约打车开发计划

> 创建日期：2026-08-30
> 范围：预约叫车（预约单）全链路。覆盖乘客预约下单与取消、后端定时派单调度、司机端预约单展示、计价复用默认规则；不含预约加价/动态计价、司机预约资格校验（能否按时赶到）、支付、WebSocket 和推送通知。
> 数据库基线：以 [create_sql.md](./StarX-Car-Backend/create_sql.md) 为准。执行 R0 数据库结构变更前必须先与用户确认并同步 `create_sql.md`。

## 0. 实施进度

| 工作包 | 状态 | 说明 |
| --- | --- | --- |
| R0 | ✅ 已完成（2026-08-30） | 线上库 ALTER + 索引已执行并 `SHOW CREATE TABLE` 验证；`create_sql.md` 已同步 |
| R1--R5 | ✅ 已完成（2026-08-30） | 后端预约单全部能力实现；全量默认测试 117 项 + 真实库烟测 2 项通过 |
| R6--R7 | ✅ 已完成（2026-08-30） | common 模型、乘客端预约下单/等待派单/取消、司机端预约徽标；三端 analyze + test 通过 |
| R8 | ✅ 已完成（2026-08-30） | 文档同步（本文件、README/CHANGELOG/接口契约、前后端计划）与构建验证完成 |

## 1. 目标

在不动摇已验证的即时叫车闭环的前提下，新增预约叫车能力：

```text
乘客选择预约时间 -> 预约报价 -> 创建 SCHEDULED 订单（等待派单）
-> 定时任务在预约时间前 30 分钟置为 PENDING（进入司机可抢单池）
-> 复用现有抢单/接驾/服务/结算流程 -> 完成
```

复用收益：抢单乐观锁、司机位置关联、到达/开始服务/完成结算、计价规则快照全部沿用即时单实现，预约单只新增「类型字段 + 等待派单状态 + 调度器」。

## 2. 现状基线

- 后端已实现即时叫车完整闭环（报价、下单、原子抢单、到达、尾号核对开始服务、历史规则快照结算、`order_fare_item` 明细、评价），104 项自动化测试 + 真实库 `OrderDatabaseSmokeIT`。
- `ride_order` 表、`OrderStateMachine`、`ride_order_event`、`driver_location`、计价规则体系均已就绪。
- 乘客端/司机端 Flutter App 与 `starx_car_common` 公共包已完成即时单全流程；枚举解析对未知值容错，新增状态不会导致客户端崩溃。
- 当前缺口：`ride_order` 无预约字段，`OrderStatus` 无预约状态，无任何预约接口与定时任务。

## 3. 已确认业务决策

| 编号 | 决策点 | 确认结果 |
| --- | --- | --- |
| D1 | 派单模型 | **定时派单**：订单停在 `SCHEDULED`，由调度器在预约时间前 `RESERVATION_DISPATCH_AHEAD_MINUTES`（默认 30，支持 0）置为 `PENDING` |
| D2 | 计价 | **复用默认即时叫车规则**，下单时快照规则 ID/版本，与即时单完全一致 |
| D3 | 乘客冲突规则 | 预约单与即时单**可并存**：进行中的即时单不阻止创建预约单；同一乘客同时最多一个未结束预约单（`SCHEDULED`/`PENDING`） |
| D4 | 取消规则 | `SCHEDULED` 派单前乘客随时免费取消；转 `PENDING` 后沿用即时单取消规则（乘客可取消 `PENDING`，司机可取消已接 `ACCEPTED`，到达后不可取消） |
| D5 | 无人接单 | 到预约时间仍为 `PENDING` 时**保持等待**，由乘客自行取消；仅当**超过预约时间 30 分钟**（`RESERVATION_EXPIRE_GRACE_MINUTES`，默认 30）仍未接单时，调度器以 `system` 身份自动取消，写 `CANCEL` 事件 + `cancelled_by='system'` |
| D6 | 预约时间窗口 | 创建时校验：预约时间 ≥ 当前 + `RESERVATION_MIN_LEAD_MINUTES`（默认 30）且 ≤ 当前 + `RESERVATION_MAX_ADVANCE_DAYS`（默认 7 天），均为可配置 |
| D7 | 司机端门槛 | v1 预约单进入 `PENDING` 后对司机无额外资格门槛，待抢列表展示「预约 HH:mm 上车」标识；司机能否按时赶到校验列为后续增强 |

## 4. 数据库变更（R0，需确认后执行）

`ride_order` 增加三列（存量即时单均为 NULL/默认值，不受影响）与一个索引：

```sql
ALTER TABLE ride_order
    ADD COLUMN order_type          VARCHAR(20) NOT NULL DEFAULT 'INSTANT'
        COMMENT '订单类型：INSTANT 即时单、SCHEDULED 预约单' AFTER order_no,
    ADD COLUMN scheduled_pickup_time DATETIME NULL
        COMMENT '预约上车时间；即时单为 NULL' AFTER destination_lng,
    ADD COLUMN dispatch_time       DATETIME NULL
        COMMENT '订单进入可抢单 PENDING 的时间；即时单等于创建时间' AFTER created_time;

CREATE INDEX idx_ride_order_type_status_scheduled
    ON ride_order (order_type, status, scheduled_pickup_time);
```

配套说明：

- `ride_order_event.event_type` 注释补充 `DISPATCH`（`SCHEDULED -> PENDING`，`operator_type='system'`，`operator_id` 为空）。
- `ride_order.cancelled_by` 在 `passenger`、`driver` 之外新增 `system` 取值（`VARCHAR(20)` 无 CHECK 约束，兼容）。
- 不新增表，不触碰 `pricing_rule`、`driver_location` 等既有结构。
- `create_sql.md` 以初始化写法同步（新库建表含新列；存量库附 ALTER）。

## 5. 订单状态机扩展

```
SCHEDULED(已预约待派单) ──调度器(到 T-ahead)──▶ PENDING(可抢单)
      │                                          │
      ├─ 乘客取消 ──────────────▶ CANCELLED       ├─ 乘客取消 ──────────▶ CANCELLED
      └─ 超预约时间 30min 未派单 ─▶ CANCELLED     └─ 司机抢单 ─▶ ACCEPTED ▶ DRIVER_ARRIVED
                        (system)                                │
                                                  COMPLETED ◀─ IN_SERVICE ◀─ 开始服务(尾号核对)
```

- `OrderStatus` 枚举新增 `SCHEDULED`，Java 值与 SQL 存储值一致。
- `OrderStateMachine` 新增迁移：`SCHEDULED -> PENDING`（仅调度器）、`SCHEDULED -> CANCELLED`（仅乘客）。
- `PENDING` 及之后的全部迁移与即时单共享，不改动抢单/取消/到达/开始/完成逻辑。
- `ride_order_event` 新增 `DISPATCH` 事件类型（`SCHEDULED -> PENDING`）。

## 6. 派单调度器

新增 `ReservationDispatchScheduler`（Spring `@Scheduled`，配置类增加 `@EnableScheduling`），固定延迟运行两个扫描：

1. **派单**：`status='SCHEDULED' AND order_type='SCHEDULED' AND scheduled_pickup_time <= now + ahead`
   - 逐单条件更新为 `PENDING`（状态 + `version` 条件，`affectedRows==0` 跳过），写 `dispatch_time` 与 `DISPATCH` 事件（`operator=system`）。
2. **过期清理**：`status='PENDING' AND order_type='SCHEDULED' AND scheduled_pickup_time < now - grace`
   - 置 `CANCELLED`，`cancelled_by='system'`，`cancel_reason='超过预约时间30分钟无司机接单'`，写 `CANCEL` 事件。

约束：

- 单实例运行即可满足课程项目部署；扫描用 `<= now + ahead` / `< now - grace` 非等值，容忍调度抖动。
- 所有查询带 `order_type='SCHEDULED'`，绝不触碰即时单。
- 使用可注入 `Clock`，测试固定时钟覆盖边界（T-ahead 前/后一刻、T+grace 前/后一刻、幂等、调度器失败回滚）。

## 7. 配置项（新增）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `RESERVATION_DISPATCH_AHEAD_MINUTES` | 30 | 预约时间前多少分钟开始派单；0 表示创建后立即进入 PENDING |
| `RESERVATION_DISPATCH_INTERVAL_MS` | 30000 | 调度器扫描间隔（毫秒） |
| `RESERVATION_MIN_LEAD_MINUTES` | 30 | 预约时间最早可为当前时间之后多少分钟 |
| `RESERVATION_MAX_ADVANCE_DAYS` | 7 | 预约时间最远可为当前时间之后多少天 |
| `RESERVATION_EXPIRE_GRACE_MINUTES` | 30 | 超过预约时间多少分钟后系统自动清理未接单 PENDING 预约单 |

同步写入 `application.yaml`（含校验）、`.env.example`、测试 Profile。

## 8. 后端接口设计

沿用「扩展既有 DTO + 新增服务方法」的兼容路线，即时单契约完全不变：

| 方法 | 路径 | 角色 | 变更 |
| --- | --- | --- | --- |
| `POST` | `/api/orders/quote` | passenger | `OrderQuoteRequest` 增加可选 `orderType`（默认 `INSTANT`）+ `scheduledPickupTime?`；`SCHEDULED` 校验时间窗口；响应回显 `orderType`/`scheduledPickupTime` |
| `POST` | `/api/orders` | passenger | 同一 DTO；`SCHEDULED` 分支：校验无其他未结束预约单（不检查即时活动单）、锁乘客行、重新规划路线与计价、写 `SCHEDULED` 订单 + `CREATE` 事件，返回 `status=SCHEDULED` |
| `POST` | `/api/orders/{orderNo}/cancel` | passenger | 状态机增加 `SCHEDULED -> CANCELLED`（派单前免费取消） |
| `GET` | `/api/passengers/me/orders` | passenger | `status` 筛选自动支持 `SCHEDULED`；摘要响应增加 `orderType`/`scheduledPickupTime` |
| `GET` | `/api/orders/{orderNo}` | 乘客/司机/管理员 | 详情响应增加 `orderType`/`scheduledPickupTime`/`dispatchTime` |
| `GET` | `/api/drivers/me/orders/pending` | driver | 自动包含已转 `PENDING` 的预约单，摘要带 `orderType`/`scheduledPickupTime`；宽限期内（超过预约时间 30 分钟以内）仍可抢，超时由调度器自动取消移出列表 |

所有新端点/字段补齐 `@Tag`/`@Operation`/`@ApiResponses`/`@Schema`，行为变更同步 `docs/client-api-contract.md`。

## 9. 客户端改动

### `starx_car_common`（仅跨角色模型）

- `OrderStatus` 增加 `SCHEDULED`（容错解析已支持未知值）。
- `OrderSummary`/`OrderDetail` 增加 `orderType`、`scheduledPickupTime?`、`dispatchTime?`；报价/下单请求与 `OrderQuote` 增加 `orderType`、`scheduledPickupTime`。
- 不引入任何预约业务流程。

### `starx_car_passenger`

- 叫车页增加「即时/预约」切换：预约模式选择日期 + 时间（分钟粒度，禁用不满足最小提前量的时段），选点后带预约时间报价、下单。
- 「我的行程」/详情：`SCHEDULED` 显示「等待派单」+ 预约时间 + 派单倒计时；派单前可取消（明确免费）；`SCHEDULED -> PENDING` 流转由既有 4 秒状态轮询自动承接，不新增轮询。
- 超预约时间未接单时展示等待/可取消状态，不展示「已过期」误导文案。

### `starx_car_driver`

- 待抢列表对 `orderType=SCHEDULED` 显示「预约 HH:mm 上车」徽标与预约时间；抢单/接驾/服务/结算流程零改动。

### `StarX-Car-Admin`

- v1 不做改动；后续可在订单列表加类型筛选（当前无管理员订单列表接口，仅详情按 orderNo 读取，自动带出新字段）。

## 10. 工作包划分

| 包 | 内容 | 依赖 | 交付物与验收 |
| --- | --- | --- | --- |
| R0 | 数据库变更 | 无 | `create_sql.md` 同步 + 线上库 ALTER + `SHOW CREATE TABLE` 验证；索引存在 |
| R1 | 模型与基础扩展 | R0 | `OrderStatus.SCHEDULED`、`OrderEventType.DISPATCH`、`RideOrder` 三字段、`@EnableScheduling`、配置项与校验；编译与枚举单测通过 |
| R2 | 预约报价与创建 | R1 | `ReservationOrderService`：时间窗口校验、预约单唯一性、锁乘客、写 `SCHEDULED` + `CREATE` 事件；MockMvc 覆盖 400/409/403 |
| R3 | 状态机扩展与取消 | R2 | `SCHEDULED->PENDING`/`CANCELLED` 迁移、乘客取消预约单；状态矩阵外迁移全部拒绝 |
| R4 | 派单调度器 | R2 | 派单 + 过期清理，固定时钟单测覆盖边界、幂等、不碰即时单；调度器失败回滚 |
| R5 | 查询与待抢列表扩展 | R3 | 列表/详情/待抢响应增加预约字段；已派单预约单在宽限期内保持可抢 |
| R6 | common 模型 + 乘客端预约下单 | R5 | 时间选择器、预约态展示与免费取消；模型解析/Repository 契约测试通过 |
| R7 | 司机端预约单标识 | R5 | 待抢列表预约徽标；解析测试通过 |
| R8 | 全链路验收与文档 | 全部 | `OrderDatabaseSmokeIT` 扩展预约场景、全量 `mvnw.cmd test`、双端 analyze/test/APK、README/CHANGELOG/接口契约/既有开发计划同步 |

## 11. 测试与验收标准

后端：

- 预约创建：时间窗口边界（`minLead` 前/后、`maxAdvance` 前/后）、`INSTANT` 携带 `scheduledPickupTime` 与 `SCHEDULED` 缺失时间的非法组合、同乘客多个未结束预约单 409、进行中即时单并存成功。
- 调度器：T-ahead 前一刻不派单、后一刻派单；T+grace 前一刻不清理、后一刻系统取消；重复运行幂等；`SCHEDULED`/`PENDING` 之外的状态不被触碰；即时单永不被扫描。
- 取消与状态机：乘客取消 `SCHEDULED`；越权/重复取消；`PENDING` 后按即时单规则。
- MockMvc：角色控制、统一信封、400/401/403/404/409/503、OpenAPI 新路径可见。
- 真实库 `OrderDatabaseSmokeIT` 扩展：创建预约单 -> 调整调度器时钟进入 PENDING -> 司机抢单 -> 完成结算全流程回滚。

客户端：

- common 模型往返解析（新增状态/字段、空值）、乘客 Repository 契约（预约报价/下单/取消请求体）、司机端待抢列表预约单解析。
- `fvm flutter analyze` / `fvm flutter test` / Android debug APK 构建通过。

端到端验收闭环：

```text
乘客预约（T+2h）-> 等待派单 -> 调度器 T-30min 转 PENDING -> 司机端看到「预约 HH:mm 上车」
-> 抢单 -> 到达 -> 尾号核对开始服务 -> 完成结算 -> 乘客查看账单
分支：派单前乘客取消；超过预约时间 30min 无司机 -> 系统自动取消
```

## 12. 文档与数据库变更门禁

- R0 数据库变更执行前：向用户提交「变更原因、SQL 初始化写法、线上影响、回滚/兼容方案、验证 SQL」并得到确认，同步 `create_sql.md`。
- R2 起每个工作包完成：更新本文件进度、对应 `README.md`、`CHANGELOG.md`、`docs/client-api-contract.md`，运行相关测试与完整构建。
- 本计划文档与 `BACKEND_DEVELOPMENT_PLAN.md`、`CLIENT_DEVELOPMENT_PLAN.md` 的「暂不纳入」范围在 R8 同步更新。
