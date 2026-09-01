# StarX-Car 后端

StarX-Car-Backend 是 StarX-Car 网约车系统的 Spring Boot 后端。当前已实现用户认证、乘客/司机资料、司机车辆管理、即时与预约订单闭环，以及供 Vue 管理端使用的用户管理、司机审核、计费规则、订单监控和预约运营能力。

本阶段新增余额支付、模拟充值提现、到达后取消费、服务中中途结束和投诉售后退款。订单结算均基于订单绑定的历史计价规则；退款通过余额账本回补乘客并回冲司机，管理员可运营投诉工单，乘客可查询投诉进度、处理方式、管理员回复和退款结果。

数据库建表及触发器脚本见 [create_sql.md](./create_sql.md)。接口在线文档在服务启动后可通过 Swagger UI 查看。

即时叫车订单一期的可独立执行开发工作包、依赖关系、接口批次和验收标准见 [BACKEND_DEVELOPMENT_PLAN.md](./BACKEND_DEVELOPMENT_PLAN.md)。该计划基于当前已初始化的订单表，不授权未确认的数据库变更。

## 技术栈

- Java 21
- Spring Boot 3.5.16 / Spring Security 6
- MyBatis-Plus 3.5.7
- MySQL 5.6.50
- JWT（access token + refresh token）
- BCrypt 密码哈希
- springdoc OpenAPI / Swagger UI
- Maven Wrapper

## 项目结构

后端以业务域为一级边界，在每个业务域内按职责分层。这样既能让订单、认证、司机等业务保持内聚，也能避免 Controller、Service、实体和 Mapper 混放在同一目录。

```text
src/main/java/org/mannerth/starxcar/
  auth/                    # 认证与用户账号
  passenger/               # 乘客资料
  driver/                  # 司机资料、车辆绑定与出车状态
  car/                     # 车辆管理
  order/                   # 订单主域
    admin/                 # 管理端订单运营子域
    pricing/               # 计价规则子域
    rating/                # 订单评价子域
  infrastructure/          # 高德、图床等外部系统适配器
  config/ security/ web/ logging/ constant/  # 跨域技术支撑
```

业务域内统一使用以下目录职责：

- `api`：HTTP Controller，只处理鉴权、参数绑定和响应。
- `application`：服务接口、实现、调度与事务编排。
- `domain`：实体、枚举、状态机、值对象和领域计算规则。
- `infrastructure`：MyBatis Mapper 等持久化适配器。
- `request`、`response`：面向接口的请求与响应 DTO。

依赖方向为 `api -> application -> domain`，`application` 可依赖同域或跨域的 `infrastructure` 端口实现；`domain` 不依赖 Web、Spring MVC 或外部服务。新增代码必须放入对应业务域和职责目录，禁止重新建立全局 Controller、Service 或 Mapper 聚合目录。

## 当前已实现能力

### 认证与账号

- 乘客注册：创建 `user` 与 `passenger` 记录。
- 司机注册：创建 `user` 与 `driver` 记录。
- 用户名和非空邮箱唯一性校验。
- BCrypt 保存和验证密码，接口不会返回密码字段。
- 用户名密码登录，成功后签发 access token 与 refresh token。
- 刷新令牌：仅接收 `token_type=refresh` 的有效 JWT，并签发新的 access/refresh token 对。
- 登录成功更新 `last_login_time`。
- 当前用户查看和编辑用户名、邮箱、性别。
- 当前用户修改密码：必须验证原密码，之后以 BCrypt 保存新密码。

### 角色与权限

用户角色来自 `user.type`：

| 角色 | 值 | 已实现权限 |
| --- | --- | --- |
| 管理员 | `admin` | 用户查询、编辑、删除乘客/司机、重置任意用户密码 |
| 乘客 | `passenger` | 管理本人乘客资料、注销账号 |
| 司机 | `driver` | 管理本人司机资料、绑定车辆、管理当前绑定车辆、注销账号 |

受保护接口采用 `Authorization: Bearer <accessToken>`。Spring Security 将 JWT 的 `role` 声明映射为 `ROLE_ADMIN`、`ROLE_PASSENGER` 或 `ROLE_DRIVER`。

### 管理员用户管理

- 用户分页查询；支持用户名/邮箱关键字和角色筛选。
- 查看用户基础资料，不返回密码。
- 编辑任意用户的用户名、邮箱、性别；用户名和非空邮箱保持唯一。
- 管理员删除乘客/司机仍为物理删除；乘客和司机自行注销均标记账号 `cancelled`，保留角色资料和订单关联历史，账号不可恢复登录。司机存在进行中订单时必须先完成或取消，不能注销，避免订单悬空。
- 不允许通过管理接口删除管理员账号或当前登录管理员。
- 任一管理员可重置任意用户密码（包括管理员）为配置的默认密码。

### 乘客、司机与车辆

- 乘客可查看、更新本人的手机号和注销账号。
- 用户可维护真实姓名和头像；管理员可维护账号状态，非 `active` 用户不能登录或刷新令牌。
- 司机可查看、更新手机号、驾驶证号、身份证号码和驾驶证有效期，绑定多辆车辆、切换或解绑当前用车，以及注销账号。
- 司机可创建并自动绑定车辆，查看、编辑或停用当前用车；车辆请求使用省份简称、发牌机关代号和号码部分，服务端自动生成完整车牌号。
- 车辆拆分车牌组合唯一；`driver_car_binding` 保存有效及历史绑定，司机可拥有多辆有效绑定车辆；`driver.car_id` 只表示当前实际使用的车辆。车辆绑定和解绑同时记录历史。
- 乘客/司机号码的尾号和脱敏展示号由 MySQL 触发器维护。
- 数据库已预留账号实名/状态、司机审核与工作状态、车辆归属地和拆分车牌字段；`driver_car_binding` 保留司机换车的绑定历史，当前 API 仍使用 `driver.car_id` 维护即时绑定关系。
- 管理员可分页查看司机的账号、证件、审核状态和当前车辆资料，按审核状态或资料关键字筛选，并将待审核申请一次性处理为通过或驳回；审核后司机保持未出车状态，已处理申请不能重复修改。

### 图片上传

- 已登录用户可通过 `POST /api/image/upload` 上传单张图片，字段名为 `file`，后端通过服务端配置的图床账号完成上传。
- 仅接受 `image/*` 的非空文件，沿用全局单文件 20 MB、单请求 50 MB 限制；成功返回图片密钥、原始文件名、公开 URL 和缩略图 URL。
- 图床令牌失效时自动重新授权并重试一次；账号未配置返回 503，图床不可用或响应异常返回 502，不暴露上游错误详情。
- 当前用户通过 `PUT /api/users/me` 的 `multipart/form-data` 资料表单上传 `avatar` 文件时，后端会先调用图床服务，再把返回 URL 保存到既有 `user.avatar_url` 字段；未附带文件时保留原头像。客户端无需、也不得单独调用图床接口。

### 订单数据库基础

- 已初始化即时叫车订单基础表：`ride_order`、`ride_order_event`、`pricing_rule`、`pricing_rule_tier`、`order_fare_item` 与 `driver_location`。
- 已建立订单领域状态机：允许创建后的抢单、取消、到达、开始服务和完成这些已确认迁移；后续订单接口将复用该状态机并追加角色与归属校验。
- 已实现计价规则读取和分段计算引擎：服务端只接受唯一的 `active + is_default` 规则，校验距离、行程时间和等待时间分段连续且不重叠；金额使用 `BigDecimal`，支持 `CEIL` 向上取整，返回基础费、最低消费调整、各类小计和可审计分段明细。最低消费调整合并到基础费明细，确保后续订单金额与明细总和一致。
- 乘客已可请求即时叫车报价并创建 `PENDING` 订单。后端使用高德路线与当前默认规则计算，创建时会重新规划与计价，不接受客户端金额；事务内锁定乘客资料行并检查未结束订单，成功时同时写入订单和 `CREATE` 事件。
- 已实现订单分页列表、详情和归属访问控制：乘客只能读取本人订单，司机只能读取自己已接订单，管理员可读取任意订单；非归属详情统一返回 404。详情返回路线、金额、司机与车辆资料（评分、脱敏手机号、供乘客拨号联系司机的完整手机号、车牌号及品牌/颜色/车型）和状态审计事件，不返回证件号。
- 高德业务坐标统一以 GCJ-02 持久化；`driver_location` 一期仅保存每位司机的最新位置，供后续乘客实时查看。
- 已实现司机出车状态和最新位置上报：审核通过、账号有效且当前有效绑定车辆存在的司机可切换 `online`/`offline`；有进行中订单时不可下线，`busy` 只能由订单流程维护。`PUT /api/drivers/me/location` 仅接受 `online` 或 `busy` 司机的 GCJ-02 位置，使用 MySQL 5.6 upsert 保持一人一条最新位置，并保留 P6--P8 维护的订单关联字段。
- 已实现司机待抢订单与并发安全抢单：只有审核通过、账号有效、`online`、当前车辆有效绑定且具有 30 秒内最新位置的司机可查看或抢单。抢单以订单 ID、`PENDING`、空 `driver_id` 和版本号作原子条件更新；成功后写入车辆快照与 `ACCEPT` 事件、司机变为 `busy`，并关联该司机的最新位置，任一步失败会回滚。
- 已实现取消、到达和开始服务：乘客可取消自己的 `PENDING`/`ACCEPTED`/`DRIVER_ARRIVED` 订单，接单司机可取消自己的 `ACCEPTED`/`DRIVER_ARRIVED` 订单；取消已接订单会释放司机和对应位置关联。接单司机可将 `ACCEPTED` 转为 `DRIVER_ARRIVED`，再开始服务为 `IN_SERVICE`；开始服务前必须提交乘客手机尾号（后四位），服务端与乘客资料核对一致后才放行，防止司机未经身份核对开始服务。等待时长仅用服务端到达与开始时间计算。全部转换均以状态和版本号条件更新并记录审计事件。
- 已实现完成服务与最终结算：接单司机可将自己的 `IN_SERVICE` 订单完成，服务端用订单创建时绑定的计价规则版本、订单计费距离、实际服务时长及已记录的等待时长重新计费。事务同时更新订单最终金额与完成时间、批量写入可审计的 `order_fare_item` 明细、记录 `COMPLETE` 事件、将司机恢复为 `online` 并清除该订单的位置关联；任一写入失败都会整体回滚。重复完成返回 409。
- 已实现乘客行程位置查看：订单乘客可在 `ACCEPTED`、`DRIVER_ARRIVED`、`IN_SERVICE` 状态读取与该订单关联的司机最新 GCJ-02 位置；超过位置保鲜窗口会明确返回 `stale=true` 和最后已知位置。仅在 `IN_SERVICE` 可获取路线规划上下文，包含司机最新位置、目的地快照和建议的重规划阈值；Flutter 客户端使用高德自行规划路线，后端不代理路线请求或保存轨迹。
- 已实现管理员计价规则管理：管理员可分页查看、新建、查看、完整更新草稿、激活和归档版本化规则。创建时对同名规则生成下一版本；草稿更新会校验三类分段函数连续无重叠且仅允许未被订单引用的草稿。激活为默认规则时会在事务中锁定并撤销旧活动默认规则，确保同时至多一个；订单创建和结算仍按各自保存的规则 ID/版本读取，历史金额不会回写。
- 后端已具备高德 Web 服务驾车路径规划适配：内部接口输入/输出均使用 GCJ-02，取第一条规划路径的米级距离和秒级时长；高德超时、无路径或异常响应统一映射为 503，不暴露 Key 或上游响应内容。报价与下单接口已使用该能力。
- 默认即时叫车规则已启用：起步价 5 元含 2 公里；总里程 2 至 12 公里按 3 元/公里、12 公里后按 1.2 元/公里；行程前 30 分钟按 0.2 元/分钟、之后按 0.5 元/分钟；司机到达后前 5 分钟等待免费、之后按 0.5 元/分钟。分段均不足一个单位按一个单位计。
- 订单状态流转写入的业务时间统一截断到整秒，与 MySQL `DATETIME` 精度一致，避免数据库舍入纳秒后导致“开始服务时间晚于完成时间”的瞬时 409。
- 已实现订单评价：乘客可在订单完成后 7 天内对司机提交 1-5 星整数评分和可选文字，一个订单只能评价一次（`order_rating.uk_order_rating_order` 唯一约束兜底并发）。评价写入 `order_rating`，同一事务内原子更新 `driver.rating_sum` 与 `driver.rating_count`，并以 `ROUND(rating_sum / rating_count, 1)` 重算 `driver.rating` 缓存平均分，避免平均值四舍五入后的长期漂移。乘客、司机和管理员可按归属权限查询订单评价；未评价订单返回 `rated=false`；重复评价、订单未完成或超过 7 天时限返回 409。司机可分页查看本人收到的评价，乘客可分页查看本人提交的评价，列表均携带被评价订单号。
- 已实现预约叫车：`POST /api/orders/quote` 与 `POST /api/orders` 支持 `orderType=SCHEDULED` + `scheduledPickupTime` 创建预约单。预约单以 `SCHEDULED` 状态等待派单，由预约调度器在预约时间前 `RESERVATION_DISPATCH_AHEAD_MINUTES`（默认 30 分钟）自动转入 `PENDING` 进入司机可抢单池，全程复用即时单的抢单、接驾、服务与结算流程；超过预约时间 `RESERVATION_EXPIRE_GRACE_MINUTES`（默认 30 分钟）仍未接单的预约单由系统自动取消。预约单与进行中即时单可并存；同一乘客同时最多一个未结束预约单；派单前乘客可免费取消。订单列表与详情返回 `orderType`/`scheduledPickupTime`/`dispatchTime`；司机待抢列表自动包含已派单预约单并携带预约上车时间。
- 支付、收入统计、自动派单、轨迹历史和预约单仍在实现计划中；现有订单表结构为后续业务开发提供数据库基础。

## API 概览

成功响应统一包装为：

```json
{
  "code": 0,
  "message": "success",
  "traceId": "...",
  "data": {}
}
```

认证接口无需 Bearer token：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 用户名密码登录 |
| `POST` | `/api/auth/refresh` | 使用 refresh token 刷新令牌 |
| `POST` | `/api/auth/passengers/register` | 注册乘客 |
| `POST` | `/api/auth/drivers/register` | 注册司机 |

当前用户接口：

| 方法 | 地址 | 角色 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/users/me` | 任意登录用户 | 获取基础用户资料 |
| `PUT` | `/api/users/me` | 任意登录用户 | 修改用户名、邮箱、性别 |
| `POST` | `/api/users/me/password` | 任意登录用户 | 验证原密码后修改密码 |
| `GET` / `PUT` / `DELETE` | `/api/passengers/me` | 乘客 | 管理/注销本人乘客资料 |
| `GET` / `PUT` / `DELETE` | `/api/drivers/me` | 司机 | 管理/注销本人司机资料 |
| `GET` | `/api/drivers/me/cars` | 司机 | 获取本人全部有效绑定车辆 |
| `PUT` | `/api/drivers/me/car` | 司机 | 绑定车辆并设为当前用车 |
| `PUT` | `/api/drivers/me/current-car` | 司机 | 在本人有效绑定车辆中切换当前用车 |
| `DELETE` | `/api/drivers/me/car` | 司机 | 解绑当前用车 |

管理员接口（均需要 `ROLE_ADMIN`）：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/users` | 分页查询；参数为 `page`、`size`、`keyword`、`type` |
| `GET` | `/api/admin/users/{userId}` | 获取用户基础资料 |
| `PUT` | `/api/admin/users/{userId}` | 编辑用户基础资料 |
| `POST` | `/api/admin/users/{userId}/reset-password` | 重置任意用户密码为默认密码 |
| `DELETE` | `/api/admin/users/{userId}` | 删除乘客或司机账号 |
| `GET` | `/api/admin/orders` | 分页监控即时与预约订单；支持订单号、类型、状态、乘客/司机关键字、创建与预约时间筛选 |
| `GET` | `/api/admin/orders/summary` | 按创建时间范围获取订单、预约风险和已完成金额摘要 |
| `GET` | `/api/admin/orders/{orderNo}` | 获取管理员订单详情与状态事件 |
| `GET` | `/api/admin/orders/{orderNo}/fare-items` | 获取订单分段计费明细 |
| `GET` / `POST` | `/api/admin/orders/{orderNo}/notes` | 查询或追加不可修改的运营备注 |
| `GET` | `/api/admin/orders/{orderNo}/driver-location` | 获取订单司机最后位置，不返回历史轨迹 |
| `POST` | `/api/admin/orders/{orderNo}/cancel` | 管理员以必填原因取消 `SCHEDULED` 或 `PENDING` 订单 |
| `POST` | `/api/admin/orders/{orderNo}/dispatch` | 管理员以必填原因将 `SCHEDULED` 预约订单提前转为 `PENDING` |

车辆接口（均需要 `ROLE_DRIVER`）：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `/api/cars/me` | 获取当前用车 |
| `POST` | `/api/cars` | 创建、绑定并设为当前用车 |
| `GET` / `PUT` / `DELETE` | `/api/cars/{carId}` | 操作当前司机用车 |

图片接口（均需要登录）：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `POST` | `/api/image/upload` | `multipart/form-data`，字段名 `file`；上传单张图片并返回图床访问地址 |
| `PUT` | `/api/users/me` | JSON 更新基础资料；或以 `multipart/form-data` 同时提交基础资料与可选 `avatar` 文件，上传并保存头像 |

即时订单接口：

| 方法 | 地址 | 角色 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/orders/quote` | 乘客 | 按 GCJ-02 上车点/目的地获取高德路线和当前默认规则的预估金额；`orderType=SCHEDULED` 时校验并回显预约时间。 |
| `POST` | `/api/orders` | 乘客 | 创建即时 `PENDING` 订单或预约 `SCHEDULED` 订单；同一乘客已有未结束即时单时即时下单返回 409，已有未结束预约单时预约下单返回 409。 |
| `GET` | `/api/passengers/me/orders` | 乘客 | 分页查询本人订单，可按状态筛选。 |
| `GET` | `/api/drivers/me/orders` | 司机 | 分页查询本人已接订单，可按状态筛选。 |
| `GET` | `/api/orders/{orderNo}` | 乘客、司机、管理员 | 按归属权限读取订单详情。 |
| `PUT` | `/api/drivers/me/work-status` | 司机 | 设置本人 `online` 或 `offline` 出车状态；`busy` 由订单流程维护。 |
| `PUT` | `/api/drivers/me/location` | 司机 | 上报本人最新 GCJ-02 位置和设备记录时间，返回统一空成功响应。 |
| `GET` | `/api/drivers/me/orders/pending` | 司机 | 分页查询当前可抢的 `PENDING` 订单；司机必须满足有效听单资格和位置保鲜要求。 |
| `POST` | `/api/orders/{orderNo}/accept` | 司机 | 原子抢单；成功后订单为 `ACCEPTED`、司机为 `busy`。 |
| `POST` | `/api/orders/{orderNo}/cancel` | 乘客、司机 | 取消订单；乘客支持 `SCHEDULED`/`PENDING`/`ACCEPTED`/`DRIVER_ARRIVED`，司机限本人 `ACCEPTED`/`DRIVER_ARRIVED` 订单。 |
| `POST` | `/api/orders/{orderNo}/arrive` | 司机 | 接单司机确认到达上车点，订单转为 `DRIVER_ARRIVED`。 |
| `POST` | `/api/orders/{orderNo}/start-service` | 司机 | 提交 `{"phoneTail":"1234"}` 核对乘客尾号后开始服务，订单转为 `IN_SERVICE` 并返回等待秒数；尾号不一致返回 400。 |
| `POST` | `/api/orders/{orderNo}/complete` | 司机 | 接单司机完成自己的 `IN_SERVICE` 订单，按该订单的历史规则版本生成最终金额和分段明细，并恢复司机为 `online`。 |
| `GET` | `/api/orders/{orderNo}/driver-location` | 乘客 | 在已接单至服务中的阶段读取本订单司机最新 GCJ-02 位置；位置过期时返回 `stale=true`。 |
| `GET` | `/api/orders/{orderNo}/route-context` | 乘客 | 仅服务中订单；返回高德路线重规划所需的司机位置、目的地快照和建议刷新阈值。 |
| `POST` | `/api/orders/{orderNo}/rating` | 乘客 | 对已完成且未超过 7 天时限的订单提交 `{"rating":5,"content":"..."}` 评价；一个订单只能评价一次，同时更新司机缓存评分。 |
| `GET` | `/api/orders/{orderNo}/rating` | 乘客、司机、管理员 | 按归属权限查询订单评价；未评价返回 `rated=false`。 |
| `GET` | `/api/drivers/me/ratings` | 司机 | 分页查询本人收到的评价，含被评价订单号。 |
| `GET` | `/api/passengers/me/ratings` | 乘客 | 分页查询本人提交的评价，含被评价订单号。 |
| `GET` | `/api/admin/pricing-rules` | 管理员 | 分页查询规则和完整分段，可按 `draft`、`active`、`inactive`、`archived` 状态筛选。 |
| `POST` | `/api/admin/pricing-rules` | 管理员 | 完整提交三类分段，新建下一版本的 `draft` 规则。 |
| `GET` / `PUT` | `/api/admin/pricing-rules/{ruleId}` | 管理员 | 查看规则；或完整更新未被订单引用的草稿分段。 |
| `POST` | `/api/admin/pricing-rules/{ruleId}/activate` | 管理员 | 激活草稿；或以 `setAsDefault=true` 将已启用规则原子切换为新订单默认。 |
| `POST` | `/api/admin/pricing-rules/{ruleId}/archive` | 管理员 | 归档非当前默认规则，保留历史规则及已结算订单可读性。 |
| `GET` | `/api/admin/drivers` | 管理员 | 分页查询司机审核资料，可按 `pending`、`approved`、`rejected` 状态及账号、姓名、联系方式或证件号筛选。 |
| `PUT` | `/api/admin/drivers/{driverId}/approval` | 管理员 | 将待审核司机处理为 `approved` 或 `rejected`；已审核或账号不可用时拒绝操作。 |

完整的请求模型、示例和错误状态码，请访问 Swagger UI：`/swagger-ui.html`。

## 配置

配置文件为 `src/main/resources/application.yaml`。生产环境请使用环境变量，不要提交真实密钥或数据库密码。

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SERVER_PORT` | 服务端口 | `8080` |
| `MYSQL_HOST` | MySQL 主机 | `localhost` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_DATABASE` | 数据库名称 | 无 |
| `MYSQL_USERNAME` | 数据库用户名 | 空 |
| `MYSQL_PASSWORD` | 数据库密码 | 空 |
| `JWT_SECRET` | JWT 签名密钥，必填 | 无 |
| `JWT_EXPIRE` | access token 有效期（秒） | `3600` |
| `JWT_REFRESH_EXPIRE` | refresh token 有效期（秒） | `604800` |
| `ADMIN_DEFAULT_PASSWORD` | 管理员重置密码时使用的默认密码，必填且长度为 6 至 72 | 无 |
| `AMAP_KEY` | 高德 Web 服务 Key；即时叫车路线预估必填 | 无 |
| `AMAP_WEB_SERVICE_BASE_URL` | 高德 Web 服务基础地址 | `https://restapi.amap.com` |
| `AMAP_ROUTE_CONNECT_TIMEOUT_MS` | 高德路线服务连接超时，范围 100--60000 毫秒 | `3000` |
| `DRIVER_LOCATION_FRESHNESS_SECONDS` | 最新司机位置的保鲜窗口，供后续抢单和乘客查看使用，范围 1--300 秒 | `30` |
| `DRIVER_LOCATION_MAX_RECORDED_TIME_FUTURE_SECONDS` | 设备记录时间允许超过服务端当前时间的最大秒数，范围 0--600 秒 | `60` |
| `AMAP_ROUTE_READ_TIMEOUT_MS` | 高德路线服务响应读取超时，范围 100--60000 毫秒 | `5000` |
| `RESERVATION_DISPATCH_AHEAD_MINUTES` | 预约时间前多少分钟开始派单（`SCHEDULED` 转入 `PENDING`），范围 0--1440 | `30` |
| `RESERVATION_DISPATCH_INTERVAL_MS` | 预约派单调度器扫描间隔，范围 5000--600000 毫秒 | `30000` |
| `RESERVATION_MIN_LEAD_MINUTES` | 预约时间最早可为当前时间之后多少分钟，范围 1--1440 | `30` |
| `RESERVATION_MAX_ADVANCE_DAYS` | 预约时间最远可为当前时间之后多少天，范围 1--30 | `7` |
| `RESERVATION_EXPIRE_GRACE_MINUTES` | 超过预约时间多少分钟后系统自动取消未接单的 `PENDING` 预约单，范围 1--1440 | `30` |
| `UPLOAD_PATH` | 上传目录，预留 | 空 |
| `IMAGE_SERVICE_BASE_URL` | 图床 API 基础地址 | `https://image.foofish.work/api/v1` |
| `IMAGE_SERVICE_EMAIL` | 图床授权邮箱，必填 | 空 |
| `IMAGE_SERVICE_PASSWORD` | 图床授权密码，必填 | 空 |
| `IMAGE_SERVICE_STRATEGY_ID` | 图床存储策略 ID，可选 | 空 |

> 安全提示：`ADMIN_DEFAULT_PASSWORD` 必须显式设为强密码，应用不会提供弱默认值；不要将真实密码写入版本控制。

例如，在 Unix-like Shell 中：

```bash
export MYSQL_DATABASE=starx_car
export MYSQL_USERNAME=starx
export MYSQL_PASSWORD='replace-me'
export JWT_SECRET='a-long-random-secret-at-least-32-characters'
export ADMIN_DEFAULT_PASSWORD='a-strong-reset-password'
```

## 数据库初始化

1. 创建 MySQL 数据库，例如 `CREATE DATABASE starx_car DEFAULT CHARACTER SET utf8mb4;`。
2. 执行 [create_sql.md](./create_sql.md) 中的 `user`、`passenger`、`driver`、`car`、`admin` 表和触发器脚本。
3. 创建至少一个 `user.type = 'admin'` 的管理员账号。密码必须是 BCrypt 哈希，不能以明文写入 `user.password`。
4. 设置上述环境变量后再启动服务。

当前管理员鉴权使用 `user.type`；`admin` 从表可按照建表脚本保留，用于维持关系模型。

## 本地运行

### macOS/Linux

```bash
cd StarX-Car-Backend
export JAVA_HOME=/Users/mannerth/.sdkman/candidates/java/21.0.11-oracle
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

服务默认地址为 `http://localhost:8080`。

### 常用命令

```bash
# 运行测试
./mvnw test

# 打包
./mvnw clean package

# 启动
./mvnw spring-boot:run
```

仅使用 Maven Wrapper，不需要安装全局 Maven。

### 显式数据库烟测

`OrderDatabaseSmokeIT` 不会进入常规 Maven 测试。仅在已确认可写且允许事务回滚的数据库中，才使用 JDK 21 与运行时配置显式执行：

```powershell
./mvnw.cmd test -Dtest=OrderDatabaseSmokeIT -Dspring.config.import=optional:file:./.env[.properties]
```

该测试在一个事务内创建唯一前缀的临时乘客、司机、车辆、订单和计价规则，验证即时叫车的报价、下单、抢单、位置、到达、服务、完成结算和规则生命周期；结束后回滚，并在事务外确认上述业务记录不存在。MySQL 自增值可能保留间隔，但不会保留业务数据。

## 文档、日志与错误处理

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 请求支持并返回 `X-Trace-Id`；响应体也会带 `traceId`，可用于日志定位。
- 认证、权限、参数校验、唯一约束等错误返回统一 JSON 结构，不返回密码、JWT、数据库异常详情或堆栈。

## 当前未实现的业务

项目根目录描述了网约车课程设计的完整目标。即时叫车与预约叫车已完成报价、下单、抢单、订单状态流转、HTTP 轮询司机位置、服务中路线规划上下文、最终结算、管理员计价规则管理和预约定时派单；仍未实现自动派单、轨迹历史、支付/退款、收入统计和调度规则。

