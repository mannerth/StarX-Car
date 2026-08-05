# Changelog

## 2026-08-05

### P11：全链路验收与发布准备

- 新增显式执行的 `OrderDatabaseSmokeIT`。该测试不匹配常规 Surefire 测试命名，仅在明确指定并运行时加载 `.env` 后执行；测试事务创建唯一前缀临时数据，使用模拟高德路线走真实 MySQL 的报价、下单、司机位置、抢单、到达、开始服务、乘客位置/路线上下文、完成结算、账单/事件查询以及计价规则创建、激活、归档流程。
- 烟测验证订单最终状态、五个状态事件、账单明细金额与订单总额一致、司机 `busy -> online`、位置订单关联清理、乘客/司机订单详情一致以及归档规则的分段可读性。测试事务结束后使用非事务查询确认临时用户、角色资料、车辆、位置、订单和规则均不存在；不留下业务记录。
- 补齐统一错误信封与 OpenAPI 终验：MockMvc 覆盖 400、401、403、404、409、503 及 Trace ID，验证 Bearer 安全定义、订单与规则请求模型路径；运行时验证 Swagger UI 200、OpenAPI JSON 及 P8--P10 路径。
- 使用 JDK 21 执行 `mvnw.cmd clean package`，85 项默认自动化测试通过；显式真实 MySQL 烟测额外 1 项通过。运行时启动、数据库登录查询与 Swagger/OpenAPI 验证均未留下业务数据。

### P10：管理员计价规则管理

- 新增管理员规则接口：`GET/POST /api/admin/pricing-rules`、`GET/PUT /api/admin/pricing-rules/{ruleId}`、`POST /api/admin/pricing-rules/{ruleId}/activate` 和 `/archive`。所有接口要求账号有效的 `admin`，具备完整 OpenAPI 成功、认证、权限、校验、资源不存在和冲突说明。
- 新建规则始终为 `draft` 且非默认；同名规则在事务中读取当前最大版本并生成下一版本，数据库唯一键继续兜底并发版本冲突。写入要求一次提交距离、行程时间、等待时间全部分段，复用分段连续性、无重叠、无上限尾段、非负金额和 `CEIL` 取整校验。
- 草稿更新以事务替换完整分段，仅允许同名且未被 `ride_order` 引用的 `draft`；被订单引用、已激活或已归档规则均不可改写，保留历史结算依据。
- 激活规则会锁定目标草稿和现有活动默认规则；`setAsDefault=true` 时先撤销旧默认规则再激活新规则，确保任意时刻至多一个 `active + is_default` 规则。归档不删除规则或分段，当前默认规则不可归档。
- 新增规则生命周期、分段函数校验、引用保护、默认规则替换、权限和 OpenAPI 测试；使用 JDK 21 运行 `mvnw.cmd clean package`，83 项测试通过。运行时以 `.env` 属性文件启动临时实例，真实登录查询返回 401，OpenAPI 已验证 P10 路径，未写入线上规则数据。

### P9：乘客实时位置与路线上下文

- 新增 `GET /api/orders/{orderNo}/driver-location`：仅订单乘客可在 `ACCEPTED`、`DRIVER_ARRIVED`、`IN_SERVICE` 阶段读取司机最后已知 GCJ-02 位置。位置记录必须仍关联该订单；无位置或位置关联已清除返回 404，非允许状态返回 409，跨乘客访问沿用订单归属检查返回 404。
- 返回设备记录时间、服务端接收时间、精度、速度和朝向；按 `DRIVER_LOCATION_FRESHNESS_SECONDS` 计算 `stale`，过期位置仍如实返回最后已知坐标，不伪造实时位置。客户端建议每 3 至 5 秒轮询。
- 新增 `GET /api/orders/{orderNo}/route-context`：仅该订单乘客且状态为 `IN_SERVICE` 时返回司机位置、目的地 GCJ-02 坐标/名称和建议的 100 米或 60 秒重规划阈值。路线折线由 Flutter 高德能力自行规划，后端不代理路线请求、不保存轨迹。
- 新增状态边界、位置保鲜、订单关联、路线目的地快照、角色拒绝和 OpenAPI 测试；使用 JDK 21 运行 `mvnw.cmd clean package`，76 项测试通过。运行时以 `.env` 属性文件启动临时实例，真实登录查询返回 401，OpenAPI 已验证两个新增路径，未创建业务数据。

### P8：完成服务与最终计费

- 新增 `POST /api/orders/{orderNo}/complete`：仅接单司机可完成自己的 `IN_SERVICE` 订单；以订单 ID、司机 ID、状态和版本号作条件更新，重复完成、状态不符或非接单司机均返回 409。
- 结算固定读取订单保存的 `pricing_rule_id + pricing_rule_version`，不读取当前默认规则；以 `billing_distance_m`、服务端 `Clock` 计算的实际服务时长及 P7 保存的等待时长调用计价引擎，并更新订单各费用小计、最终总额、实际时长和完成时间。
- 新增 `order_fare_item` 批量写入与 `COMPLETE` 审计事件。计费明细保留规则/版本、分段、用量、单位价格与金额快照，明细金额总和与订单最终金额一致。
- 结算事务成功后将司机由 `busy` 恢复为 `online`，并仅清除同一订单的最新位置关联；计费明细、事件或司机状态写入失败均会回滚订单完成状态和金额。
- 新增历史规则结算、明细总额、版本冲突、明细写入失败回滚、司机/乘客权限与 OpenAPI 测试；使用 JDK 21 运行 `mvnw.cmd clean package`，68 项测试通过。运行时以 `.env` 属性文件启动临时实例并发送无效登录请求，获得 401，确认应用和 MySQL 连接可用，未创建业务数据。

### P5：司机出车状态与位置上报

- 新增 `PUT /api/drivers/me/work-status`：仅审核通过且账号有效的司机可切换 `online` 或 `offline`；上线要求存在有效的当前绑定车辆，有进行中订单或 `busy` 状态时拒绝错误的状态切换，客户端无法提交 `busy`。
- 新增 `PUT /api/drivers/me/location`：仅 `online`/`busy` 司机可上报本人 GCJ-02 最新位置，校验经纬度、精度、速度、朝向和设备时间；服务端写入接收时间，位置请求不携带且不能覆盖 `active_order_id`。
- 新增 MySQL 5.6 兼容的 `driver_location` upsert。重复上报相同位置时 MySQL 返回 0 也按幂等成功处理，避免客户端重试被误报为 500。
- 新增位置保鲜与设备时间容差配置：`DRIVER_LOCATION_FRESHNESS_SECONDS`、`DRIVER_LOCATION_MAX_RECORDED_TIME_FUTURE_SECONDS`，并更新 `.env.example` 和 README 配置说明。
- 修复位置请求的时间格式契约：`recordedTime` 按文档接受 `yyyy-MM-dd HH:mm:ss`（Asia/Shanghai）。
- 补齐 DriverWorkService 和 MockMvc 测试，覆盖车辆绑定资格、审核/账号状态、进行中订单、位置权限、幂等写入、未来时间、OpenAPI 路径和统一响应；完整 Maven 构建通过 42 项测试。使用运行时加载 `.env` 的临时应用实例发送只读登录探测，确认 MySQL 可连接，未产生业务数据。

### P6：待抢订单与并发抢单

- 新增 `GET /api/drivers/me/orders/pending` 和 `POST /api/orders/{orderNo}/accept`。待抢列表仅面向审核通过、账号有效、`online`、当前车辆有效绑定且位置未过期的司机，按创建时间与 ID 倒序返回尚未接单的 `PENDING` 订单。
- 抢单使用 MySQL 5.6 兼容的条件更新：同时匹配订单 ID、`PENDING`、空 `driver_id` 与原始 `version`，成功后写入司机/车辆快照、接单时间并递增版本，避免并发司机重复接单。
- 抢单事务中写入 `ACCEPT` 审计事件，将司机从 `online` 原子切换为 `busy`，并仅在司机位置新鲜且未关联其他订单时设置 `driver_location.active_order_id`；事件、状态或位置关联失败均抛出异常以触发整体回滚。
- 新增服务层并发、资格、失败路径和原子 SQL 条件测试，以及 MockMvc 角色、响应和 OpenAPI 测试；完整 Maven 构建通过 52 项测试。使用运行时加载 `.env` 的临时应用实例发送只读登录探测，确认新 Mapper 可随真实 MySQL 配置启动，未产生业务数据。

### P7：取消、到达与开始服务

- 新增 `POST /api/orders/{orderNo}/cancel`、`/arrive` 和 `/start-service`。乘客可取消自己的 `PENDING`/`ACCEPTED` 订单；接单司机只能取消自己的 `ACCEPTED` 订单；到达、服务中及终态订单均不可取消。
- 取消、到达和开始服务均使用状态与版本号的条件更新，并各自写入一个 `CANCEL`、`ARRIVE` 或 `START_SERVICE` 审计事件。已接订单取消时司机必须从 `busy` 恢复 `online`，并只清空其与该订单匹配的 `active_order_id` 位置关联。
- 开始服务使用服务端 `Clock` 计算 `waiting_duration_s = service_started_time - driver_arrived_time`；缺少或晚于开始时间的到达时间会拒绝转换，客户端不能伪造等待时长。
- 新增状态转换服务、并发竞争、SQL 条件、参数校验、角色和 OpenAPI 测试；完整 Maven 构建通过 63 项测试。使用运行时加载 `.env` 的临时应用实例发送只读登录探测，确认新 Mapper 可随真实 MySQL 配置启动，未产生业务数据。

## 2026-08-04

### P0、P1：订单基础与高德路线服务

- 新增订单领域枚举和 `OrderStateMachine`，固定一期即时叫车的 `PENDING -> ACCEPTED -> DRIVER_ARRIVED -> IN_SERVICE -> COMPLETED` 流转，以及 `PENDING`、`ACCEPTED` 的取消分支；后续工作流将复用它并实现角色、归属和并发校验。
- 新增可注入的 UTC `Clock` Bean，后续订单工作流可使用固定时钟进行时间相关测试。
- 新增高德 Web 服务驾车路线内部客户端：使用 GCJ-02 坐标并按“经度,纬度”调用 `/v5/direction/driving`，通过 `show_fields=cost` 读取首条路线的距离（米）和时长（秒）；网络、非成功响应、无路径和无效指标统一返回 503，日志不记录 Key 或地点。
- 新增 `AmapProperties` 配置校验和独立 `amapRestClient`，默认连接/读取超时分别为 3 秒、5 秒；为图床和高德两个 `RestClient` 注入点显式添加限定符，避免多 Bean 注入歧义。
- `.env.example` 改为当前 MySQL 连接变量模板，并补充高德 Web 服务地址与超时变量；README 同步更新订单基础和高德配置说明。
- 新增订单状态机与高德模拟 HTTP 单元测试，覆盖已确认状态迁移、GCJ-02 经度纬度顺序、首路径读取、无路径/上游 500 映射 503，以及坐标边界校验。

### P2：计价规则读取与计算引擎

- 新增 `pricing_rule`、`pricing_rule_tier` 的 MyBatis-Plus 映射和只读 `PricingRuleService`：仅允许唯一的 `active + is_default` 默认规则，按规则 ID/版本可读取历史规则；规则缺失、重复或存储配置不合法统一返回 503。
- 新增 `PricingRuleValidator` 与无状态 `FareCalculator`：校验三类分段从 0 开始、连续、无重叠、仅最后一段无上限，按 `CEIL` 计算整数米/秒用量，金额全程使用 `BigDecimal`；距离起步包含部分不重复计费，等待免费分段保留 0 元审计明细。
- 最低消费作为显式调整额并入基础费明细，使计算总额始终等于各明细金额总和；默认规则在 2 km、12 km、30 min、5 min 等边界都由单元测试覆盖。
- 增加规则服务和计费引擎测试，并补齐测试 Profile 下新增 Mapper 的 mock；以运行时加载 `.env` 的非 Web Spring 上下文只读验证线上默认规则（7 个分段）及默认规则计算，未写入数据库。

### P3：乘客报价与创建即时订单

- 新增 `POST /api/orders/quote` 和 `POST /api/orders`：仅 passenger 可调用，地点统一校验为 GCJ-02；报价使用服务端高德驾车路线与默认规则，创建订单时重新读取规则、重新规划路线和重新计算金额。
- 新增 `ride_order`、`ride_order_event` 映射、订单号生成器和订单 Service。创建事务通过 `SELECT ... FOR UPDATE` 锁定当前乘客资料行，检查 `PENDING`、`ACCEPTED`、`DRIVER_ARRIVED`、`IN_SERVICE` 未结束订单；成功时同时写入 `PENDING` 订单和 `CREATE` 审计事件，订单插入未获得自增 ID 时立即失败并回滚。
- 新增订单 Service 与 MockMvc 测试，覆盖服务端重算、未结束订单 409、订单/事件字段、201 统一响应、乘客角色限制和 OpenAPI 文档路径；以真实高德和默认规则运行创建事务后显式回滚，未留下订单数据。
- 修复方法级 `@PreAuthorize` 被全局异常兜底错误映射为 500 的问题：`AccessDeniedException` 现在返回统一 403 响应。

### P4：订单查询、详情与访问控制

- 新增乘客/司机订单分页列表和 `/api/orders/{orderNo}` 详情接口；列表按创建时间和 ID 倒序，支持状态筛选并限制页大小 1--100。
- 新增 `OrderAccessService`，集中处理订单存在性、乘客归属和司机接单归属；无归属详情统一返回 404，避免通过订单号探测他人订单。
- 订单详情返回路线快照、金额是否为最终结算、状态时间、最小司机/车辆展示资料和审计事件，不返回密码、证件号或完整手机号；在线上数据库以只读方式验证了乘客分页查询。

### 文档

- 新增 `BACKEND_DEVELOPMENT_PLAN.md`：定义即时叫车一期后端的可独立执行工作包、P0--P11 依赖关系、接口交付顺序、逐包验收标准、状态机、计价约束及数据库变更门禁；明确当前范围不含预约单、自动派单、支付、轨迹回放和 WebSocket。
- 在 README 中增加后端开发计划入口，说明计划基于现有订单表，不包含未确认的数据库变更。

## 2026-08-04

- 在线 MySQL 初始化即时叫车数据库基础：新增 `pricing_rule`、`pricing_rule_tier`、`ride_order`、`ride_order_event`、`order_fare_item` 与 `driver_location` 六张表，以及订单查询、抢单并发控制、状态审计、计费明细和最新司机 GCJ-02 位置所需外键与索引。
- 初始化并启用“默认即时叫车规则”第 1 版：起步价 5 元含 2 公里；总里程 2 至 12 公里 3 元/公里、12 公里后 1.2 元/公里；行程前 30 分钟 0.2 元/分钟、之后 0.5 元/分钟；司机到达后前 5 分钟等待免费、之后 0.5 元/分钟；全部分段不足一个计费单位按一个单位计。
- `create_sql.md` 补充上述建表脚本、外键索引和默认规则初始化 SQL；当前仅完成数据库初始化，订单、位置和计价接口将在后续实现。

## 2026-08-01

- 司机车辆模型调整为多车辆有效绑定：`driver_car_binding` 保留司机全部有效及历史绑定，`driver.car_id` 仅指向当前实际用车；创建或绑定新车辆不再结束其他车辆绑定。
- 新增 `GET /api/drivers/me/cars` 查询当前司机全部有效绑定车辆，以及 `PUT /api/drivers/me/current-car` 在已绑定车辆中切换当前用车；原 `PUT /api/drivers/me/car` 保持兼容，仍执行绑定并设为当前用车。
- 司机注销改为将 `user.status` 标记为 `cancelled`，结束有效绑定并清空当前用车，但保留 `driver` 资料及 `driver_car_binding` 历史记录。
- 更新 `create_sql.md` 的 `driver.car_id` 与 `driver_car_binding.status` 注释，明确前者是当前用车、后者表示有效/历史绑定而非当前用车。

- 移除管理员密码重置的 `123456` 弱默认值：`ADMIN_DEFAULT_PASSWORD` 现在为必填环境变量，并继续由配置校验约束为 6 至 72 个字符；同时将 README 的部署配置说明更新为无默认值。
- 将 MyBatis-Plus 全局主键策略改为 `auto`，与现有 MySQL 自增主键及实体上的 `IdType.AUTO` 约定一致；`driver` 和 `passenger` 仍通过实体的 `IdType.INPUT` 使用对应 `user.id`。
- 请求访问日志只记录 `Authentication.getName()` 的用户 ID，不再序列化整个 Spring Security 认证主体，避免日志包含无关认证对象信息。
- 补全认证、当前用户、乘客、司机和车辆接口的 OpenAPI 成功、参数校验、认证、权限、资源不存在及冲突状态码说明，使 Swagger UI 与实际错误契约一致。

- `PUT /api/users/me` 新增 Multipart 资料更新形式：接收可选 `avatar` 图片文件，调用内部图床服务并将成功返回的 URL 保存至现有 `user.avatar_url`；图床失败时不执行用户资料更新。
- 新增受 JWT 保护的 `POST /api/image/upload` 接口，接收 `multipart/form-data` 的 `file` 图片字段，并返回图床图片密钥、原始文件名、公开 URL 和缩略图 URL。
- 接入 `image.foofish.work` 图床：后端以环境变量配置账号和可选存储策略，获取并线程安全缓存图床 Bearer Token；上游返回 401 时清除缓存、重新授权并重试一次上传。
- 增加图片空文件、非图片 MIME 类型、图床未配置和上游失败的明确错误处理；账号、密码、Token 和上游错误正文均不会暴露给客户端或写入日志。
- 添加图片上传控制器回归测试，并在 README 记录接口、文件限制与 `IMAGE_SERVICE_*` 配置项。

## 2026-07-31

- 调整用户、司机与车辆基础表：补充真实姓名、头像、账号状态、司机审核/工作状态、证件有效期及车辆车牌拆分、归属地和载客信息字段。
- 新增 `driver_car_binding` 表及司机、车辆当前/历史绑定查询索引；保留现有 `driver.car_id`，以保证既有后端功能不受立即影响。
- 为 `car` 新增按省份简称、发牌机关代号和号码部分组成的联合唯一车牌约束。
- 后端接口同步支持新增用户、司机和车辆资料字段；注册、个人资料、管理员用户管理和返回资料均可读写或展示相应字段。
- 车辆创建、绑定、解绑和停用流程同步维护 `driver_car_binding`，并继续维护 `driver.car_id` 以兼容现有查询；物理删除车辆改为标记 `disabled`，保留绑定历史。
- 账号状态为 `disabled` 或 `cancelled` 时拒绝登录和刷新令牌。
- 在管理端和两端 Flutter 客户端切换至拆分车牌请求后，移除 `CarRequest` 中旧版 `carNumber` 入参；完整车牌仍由服务端生成并持久化。
