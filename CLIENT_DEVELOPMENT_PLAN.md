# StarX-Car Android 客户端开发计划

## 1. 目标与范围

本计划面向两个 Flutter Android App：`starx_car_passenger`（乘客端）和
`starx_car_driver`（司机端），以及两端共同依赖的 `starx_car_common`。
目标是基于当前已完成的后端接口交付一条可验收的即时叫车闭环：

```text
乘客选点 -> 报价 -> 下单 -> 司机上线/上报位置 -> 抢单
-> 司机到达 -> 开始服务 -> 乘客查看路线/位置 -> 司机完成 -> 双方查看账单
```

本期只做即时订单和 HTTP 定时位置同步，不做预约单、支付、评价、收入、轨迹回放、
推送通知和 WebSocket。地图业务坐标统一为高德 `GCJ-02`；设备定位得到的 `WGS-84`
只能在公共地图层转换后再进入业务接口。

## 当前实施进度（2026-08-06）

已完成并通过静态检查/单元测试/Android debug 构建：C-00（包括客户端接口契约文档）、C-01、C-02 的地图、定位、
原生驾车路线、POI 搜索和逆地理编码部分、C-03、C-04、C-05、C-07、C-09，以及
C-10 的司机详情、导航和完成结算展示。C-06 的基础位置轮询和 C-08 的位置上报已完成，
其中司机上报已具备串行、最新点覆盖和退避重试，司机端已加入 Android location 前台服务和常驻通知。

已完成后端运行时探测：使用 `spring.config.import=optional:file:./.env[.properties]` 加载 `.env` 后，Swagger UI 和 OpenAPI 均返回 200；未认证报价/登录请求均返回 401，订单报价、司机位置、路线和位置上报路径均出现在 OpenAPI 中。真实 MySQL `OrderDatabaseSmokeIT` 已通过，覆盖报价、下单、抢单、到达、开始服务、位置/路线、完成结算、账单事件和事务回滚。

Android 模拟器 `Pixel 9 Pro`（Android 16/API 36）已完成乘客端和司机端启动级 integration test、两端高德地图 Widget 挂载测试、乘客一次性定位回调测试，以及司机前台服务启动/停止测试；两个 APK 均能安装并渲染。高德 SDK 日志报告 `INVALID_USER_SCODE`，因此仍需修正并验收 Key 的包名/SHA-1 限制；同时仍需验收隐私授权界面、定位权限/GPS 异常、
原生路线/POI 实际返回，以及前台服务在 Android 13/14 的通知和权限行为。C-06 的后台生命周期暂停
和乘客分页加载、详情状态轮询已在代码中完成；详情页会在前台约每 4 秒刷新，进入完成/取消终态后停止。C-06 的状态筛选、订单事件时间线、轮询有限退避和手动重试均已补齐。C-11 的乘客/司机 Repository、认证 Controller、基础 Widget 和启动级 integration test 已补充；乘客与司机注销入口也已接入，服务端保留历史订单并对司机进行中订单注销返回 409。当前 common、passenger、driver 的 analyze 和单元测试均通过，两个 App 的 Android debug APK 均可构建；集成测试静态分析通过，跨端演练必须在有 Android 真机和后端测试账号后执行。

## 2. 依赖与职责边界

```text
starx_car_passenger ─┐
                     ├──> starx_car_common
starx_car_driver ────┘
```

### `starx_car_common`

- 保留现有 Dio、统一响应信封、Bearer、单飞 401 刷新、加密会话。
- 增加地图底层适配：高德地图 Widget、权限、设备定位、GCJ-02 坐标模型、POI/逆地理编码、驾车路线规划和外部导航跳转。
- 增加角色无关的 `GeoPoint`、`Place`、分页、基础用户和通用错误/网络状态模型。
- 不放订单状态机、叫车/抢单/取消/到达/开始服务/完成流程，不放角色页面、Repository、Riverpod Provider，也不放高德 Key 和签名资料。

### `starx_car_passenger`

- 乘客认证、上车点/目的地、报价和下单。
- 当前订单、取消、订单历史和详情。
- 等待接单、司机位置轮询、司机到达前状态展示。
- `IN_SERVICE` 后以公共高德能力规划并展示“司机当前位置 -> 目的地”的路线。
- 完成后展示最终费用明细；不实现支付/评价入口的业务调用。

### `starx_car_driver`

- 司机认证、资料和多车辆/当前车辆管理（现有能力继续复用）。
- 上线/下线、位置采集和定时上报。
- 待抢订单、抢单、已接订单。
- 导航到上车点、确认到达、开始服务、导航到目的地、完成订单。
- 位置上报策略、订单状态和司机工作台均留在司机端。

## 3. 接口与客户端模型约定

所有 `ApiClient` 成功返回值已经去除 `{code,message,traceId,data}` 外壳；Repository 只处理 `data`。
错误需保留用户可读 `message`、HTTP 状态和 `traceId`，并按状态显示重试、重新登录或冲突提示。

### 认证与基础资料接口

| 方法 | 路径 | 客户端用途 |
| --- | --- | --- |
| POST | `/auth/login` | 登录后校验返回角色与当前 App 匹配 |
| POST | `/auth/passengers/register` | 乘客注册并直接建立会话 |
| POST | `/auth/drivers/register` | 司机注册并直接建立会话 |
| POST | `/auth/refresh` | 仅由 common 在 401 时自动调用 |
| POST | `/auth/logout` | 主动退出时撤销服务端 refresh token，再清本地会话 |
| GET/PUT | `/users/me` | 角色无关基础资料和头像 |
| POST | `/users/me/password` | 修改密码，要求当前密码 |

注册/登录成功后必须检查 `user.type`，乘客端拒绝 driver/admin 会话，司机端拒绝
passenger/admin 会话；拒绝时清理刚保存的 token 并提示使用正确客户端。

### 乘客订单接口

| 方法 | 路径 | 客户端用途 |
| --- | --- | --- |
| POST | `/orders/quote` | 用 `pickup`、`destination` 获取预估距离、时长和费用 |
| POST | `/orders` | 使用同一地点请求创建 `PENDING` 订单；服务端会重新规划和计价 |
| GET | `/passengers/me/orders?page=&size=&status=` | 分页历史/当前订单 |
| GET | `/orders/{orderNo}` | 详情、状态时间、司机、费用和事件 |
| POST | `/orders/{orderNo}/cancel` | 司机到达前取消，body 可为 `{reason}` |
| GET | `/orders/{orderNo}/driver-location` | `ACCEPTED`/接驾阶段读取司机最后位置 |
| GET | `/orders/{orderNo}/route-context` | 仅 `IN_SERVICE` 获取司机位置、目的地和重规划建议 |

地点 JSON 统一为：`poiId?`、`name`、`address?`、`latitude`、`longitude`。费用模型包含
`baseFare`、`distanceFare`、`drivingTimeFare`、`waitingFare`、`totalFare`；订单完成前标记
`estimatedFare=true`，不能把客户端报价作为最终金额。

### 司机接口

| 方法 | 路径 | 客户端用途 |
| --- | --- | --- |
| GET | `/drivers/me` | 获取审核和工作状态 |
| GET | `/drivers/me/cars` | 获取司机绑定的全部车辆 |
| PUT | `/drivers/me/current-car` | 切换 `carId`；上线前必须已有当前车辆 |
| PUT | `/drivers/me/work-status` | body `{status: online|offline}` |
| PUT | `/drivers/me/location` | 上报最新 GCJ-02 位置和设备时间 |
| GET | `/drivers/me/orders/pending` | 获取可抢订单 |
| GET | `/drivers/me/orders?page=&size=&status=` | 已接订单列表 |
| POST | `/orders/{orderNo}/accept` | 抢单；成功后进入 `ACCEPTED` |
| POST | `/orders/{orderNo}/cancel` | 司机仅能取消自己已接的 `ACCEPTED` 订单 |
| POST | `/orders/{orderNo}/arrive` | `ACCEPTED -> DRIVER_ARRIVED` |
| POST | `/orders/{orderNo}/start-service` | `DRIVER_ARRIVED -> IN_SERVICE`，服务端计算等待时长 |
| POST | `/orders/{orderNo}/complete` | `IN_SERVICE -> COMPLETED`，返回最终账单 |

模型至少包括：`OrderStatus`（`PENDING`、`ACCEPTED`、`DRIVER_ARRIVED`、`IN_SERVICE`、
`COMPLETED` 及后端实际取消终态）、`OrderDetail`、`OrderSummary`、`DriverSummary`、
`FareQuote`、`DriverLocation`（含 `recordedTime`、`receivedTime`、`stale`）和分页容器。
枚举解析必须对未知值容错，不能因服务端新增状态导致 App 崩溃。

## 4. 分阶段执行计划

每个步骤都可以独立提交和验收。步骤编号表示依赖关系，不表示必须一次完成全部代码。

### C-00 基线与接口契约（无前置）

**工作项**

- 固定 FVM Flutter `3.44.8`、Dart `3.12.2`，确认 Android SDK/Java 21 和两个 App 的包名。
- 从后端 OpenAPI/Controller 建立 `docs/client-api-contract.md`：字段、状态、错误码、时间格式、分页和权限。
- 在两个 App 配置可覆盖的 `API_BASE_URL`；真机使用局域网 HTTPS/地址，模拟器默认 `10.0.2.2`。

**验收**

- `fvm flutter pub get && fvm flutter analyze && fvm flutter test` 在 common、passenger、driver 均通过。
- 使用 mock server 能验证一个带信封的 GET、401 刷新重放和错误 traceId 展示。

### C-01 公共模型与网络测试（依赖 C-00）

**工作项**

- 在 common 增加订单展示所需的角色无关模型和 ISO/后端 `yyyy-MM-dd HH:mm:ss` 时间解析。
- 为 `ApiClient` 增加超时、取消请求、网络不可用、401 刷新失败、409/503 的统一异常映射。
- 当前 `ApiClient` 已支持所有请求方法传入可选 `CancelToken`；主动取消统一为 `ApiException.cancelled == true`，角色页面可以区分取消和网络失败。
- 使用 Dio adapter/mock 编写模型和刷新并发测试；不引入角色 Repository。

**验收**

- 模型 JSON 往返测试覆盖金额精度、空司机、空位置、未知状态和 `stale=true`。
- 两个 App 只通过 common 获取会话和 HTTP 能力，依赖方向检查通过。

### C-02 高德 Android 基础接入（依赖 C-01）

**工作项**

- 选定与 Flutter 3.44/Android Gradle 兼容的高德插件并锁定版本；封装 `AmapMapView`、定位、POI 搜索、逆地理编码、驾车路线规划和导航 Intent。
- Android Manifest 仅声明必要定位权限（前台定位优先）；处理拒绝、永久拒绝、GPS 关闭和无网络。
- 当前实现通过两个 App 各自未提交的 `android/amap.properties` 注入 `AMAP_ANDROID_KEY` 到 AndroidManifest；`--dart-define` 不能直接替代原生清单 Key。禁止写入 Dart 常量、common README 或 Git；构建后的 APK 清单会包含运行所需的 Key，这是 Android SDK 的正常运行方式。
- 明确转换边界：定位原始值先转/标注 GCJ-02，Repository 只接收 `GeoPoint(coordinateSystem: gcj02)`。

**验收**

- Android 真机能显示高德底图、当前位置和一个 POI 标记；权限异常有可恢复提示。
- 规划路线的起终点与后端请求中的坐标一致，截图/日志确认没有 WGS-84 与 GCJ-02 混用。

### C-03 认证与应用壳完善（依赖 C-01，可与 C-02 并行）

**工作项**

- 统一登录态 Gate、退出清理、账号停用/注销后的登录页回退。
- 增加全局加载、空态、错误重试、401 重新登录和 409 冲突提示组件。
- 为两个 App 建立清晰路由/页面栈，避免用单一 `home` 条件承载全部业务。

**验收**

- 冷启动恢复会话；access token 失效时只刷新一次并重放原请求；刷新失败回登录页。
- 旋转/后台恢复（Android 配置变化）不重复提交表单或重复启动轮询。

### C-04 乘客地点选择与报价（依赖 C-02、C-03）

**工作项**

- 新增地图首页、上车点/目的地搜索、地图选点、当前位置和确认地点页。
- 乘客 Repository 调用 `/orders/quote`，展示距离、时长和五项费用；下单前重新确认地点。
- 报价和创建订单请求支持主动取消；页面离开时取消未完成请求，取消不展示为网络错误。
- 防止起点等于终点、坐标无效、路线服务 503；保存最近一次未提交地点仅用于恢复页面。

**验收**

- 真机搜索 POI 并选择后能看到两个 GCJ-02 标记和报价。
- 模拟 400/503 时表单数据不丢失；报价金额仅展示为预估。

### C-05 乘客即时下单与取消（依赖 C-04）

**工作项**

- 调用 `/orders` 创建订单，成功后进入当前订单页，状态为 `PENDING`。
- 处理 409 未结束订单：跳转已有订单详情，不重复创建；按钮提交期间禁用并支持请求取消。
- 司机到达前允许取消（`PENDING`、`ACCEPTED`）；`DRIVER_ARRIVED` 及之后只展示不可取消原因。

**验收**

- 重复点击只产生一个订单；下单后杀进程重启仍能通过列表/详情恢复当前订单。
- 分别验证 PENDING、ACCEPTED 可取消，及 DRIVER_ARRIVED/IN_SERVICE/COMPLETED 被后端 409 拒绝。

### C-06 乘客订单列表、详情与接驾位置（依赖 C-05、C-02）

**工作项**

- 实现分页列表、状态筛选、详情时间线和司机卡片。
- 详情页在前台轮询订单状态；状态进入 `COMPLETED`/`CANCELLED` 后停止，后台、页面离开和终态不再请求。
- `ACCEPTED`/接驾阶段每 3-5 秒轮询 `/driver-location`；页面离开、终态或 App 进入后台立即停止并取消正在进行的请求，回前台重新拉取。
- `stale=true` 时显示“最后已知位置/更新时间”，不标记为实时；无位置显示等待司机上报。
- 轮询请求串行化，网络失败采用退避并显示手动重试，不堆积请求。

**验收**

- mock 连续返回位置时地图标记平滑更新；stale、空位置、401、503 均有明确 UI。
- 订单终态后不再产生位置请求；分页下拉和加载更多不会重复或丢失记录。
- 乘客从新建 `PENDING` 详情开始，能自动看到 `ACCEPTED`、`DRIVER_ARRIVED`、`IN_SERVICE` 和最终终态变化；状态变化后对应位置/路线面板随之出现或停止。

### C-07 乘客服务中路线（依赖 C-06）

**工作项**

- `IN_SERVICE` 首次调用 `/route-context`，以司机最后位置和目的地调用 common 高德驾车路线。
- 位置轮询仍由 passenger 自己负责；位置变化超过响应中的 `recommendedRefreshDistanceM`（默认 100m）或超过 `recommendedRefreshSeconds`（默认 60s）才重规划。
- 路线规划失败保留目的地和司机标记，提供重试；不把路线点/轨迹写回后端。

**验收**

- 司机开始服务后乘客能看到路线、当前位置和目的地；司机位置变化达到阈值时路线刷新。
- `stale=true` 时路线标注为基于最后位置，不能声称实时；订单完成后停止规划和轮询。

### C-08 司机上线、车辆与位置上报（依赖 C-02、C-03）

**工作项**

- 将现有车辆页扩展为多车辆列表、当前车辆切换和上线前校验；调用 `/drivers/me/cars` 与 `/drivers/me/current-car`。
- 上线/下线调用 `/work-status`；服务端返回审核、无当前车、进行中订单等 403/409 时保持开关真实状态。
- 上线后由 driver 端启动前台可见的位置采集任务，建议 3-5 秒上报 `/location`；请求体含 GCJ-02 纬度/经度、精度、速度、朝向、设备记录时间。
- 位置上报串行化、超时丢弃旧点、断网只保留最新待发点；位置请求、待抢订单刷新和已接订单查询支持取消令牌，下线、登出、无权限时停止并清理任务。

**验收**

- 无当前车辆不能上线；切换车辆后下一次上线使用新车。
- mock 观察到上报频率、时间格式和坐标系正确；网络恢复后不发送过期位置；后台/前台行为符合 Android 权限策略。

### C-09 司机抢单工作台（依赖 C-08、C-01）

**工作项**

- 上线状态下轮询 `/drivers/me/orders/pending`，展示上车点、目的地、预估距离/费用和创建时间。
- 抢单调用 `/orders/{orderNo}/accept`；成功立即停止该订单的 pending 轮询，进入已接订单；409 表示订单已被他人抢走并刷新列表。
- `/drivers/me/orders` 支持当前订单和历史分页；司机只能看到自己的已接订单。

**验收**

- 下线不轮询 pending；抢单按钮防重复提交；409 后列表最终与服务端一致。
- 一个司机同一时刻只能有一个进行中订单，服务端冲突能被清楚解释。

### C-10 司机接驾、到达、服务和完成（依赖 C-09、C-02）

**工作项**

- 按严格状态机实现按钮：`ACCEPTED` 导航/“已到达”，`DRIVER_ARRIVED` “开始服务”，`IN_SERVICE` 导航/“完成订单”。
- 到达调用 `/arrive`，开始调用 `/start-service`，完成调用 `/complete`；每个动作显示确认、加载和成功后的服务端状态。
- 导航使用 common 的高德外部导航能力；上车点和目的地从订单详情的 GCJ-02 坐标生成。
- 完成页展示 `actualDurationS`、`waitingDurationS` 和最终 `FareQuote`，按后端 CEIL 规则结果展示，不在客户端重算金额。

**验收**

- 乱序点击会被 UI 和后端 409 双重拦截；成功后刷新详情，位置上报仍与 active order 规则一致。
- 等待 5 分钟免费、超出部分计费的账单字段能原样展示；完成后停止订单专属任务。

### C-11 跨端闭环验收与稳定性（依赖 C-07、C-10）

**工作项**

- 准备一部 Android 真机（高德 Key、定位权限）和后端测试账号，执行乘客/司机双端脚本。
- 覆盖断网、弱网、切后台、进程被杀、令牌过期、权限拒绝、服务端 400/403/404/409/503。
- 补充 Repository 单元测试、Provider/Controller 单元测试、页面 Widget 测试和关键 Android integration test。
- 记录版本号、设备、API 地址、traceId 和复现步骤；不记录 token、身份证、手机号明文。

**验收**

- 通过“报价 -> 下单 -> 抢单 -> 到达 -> 开始 -> 位置/路线 -> 完成”的双端演练。
- common、passenger、driver 的 analyze/test/debug APK 均通过；无未处理异常、定时器泄漏或越权数据展示。

## 5. 推荐目录演进

```text
starx_car_common/lib/src/
  auth/ network/ map/
  models/                 # 仅跨角色基础模型
  map/amap_*.dart         # 地图、定位、POI、路线、导航适配

starx_car_passenger/lib/
  core/                   # providers、路由、错误/生命周期
  features/auth/
  features/profile/
  features/booking/       # 选点、报价、创建订单
  features/orders/        # 列表、详情、取消、轮询
  features/trip/          # 服务中路线和司机位置展示

starx_car_driver/lib/
  core/
  features/auth/
  features/home/
  features/vehicles/
  features/work/          # 上下线、位置上报、生命周期
  features/orders/        # pending、抢单、已接订单
  features/trip/          # 到达、开始、导航、完成
```

Repository、Controller/Notifier、页面和定时任务均按角色放置；common 只暴露基础能力接口。

## 6. 配置、隐私与安全

- 必需运行配置：`API_BASE_URL`、`AMAP_ANDROID_KEY`；生产使用 HTTPS，开发 HTTP 仅限 debug。
- 高德 Key 应在高德控制台限制 Android 包名和 SHA-1；发布签名、`key.properties`、Keystore 和密码只存在本机/CI Secret。
- Android 定位权限按“使用时”申请；若后续要求司机后台持续接单，再单独评审前台服务、通知和后台定位权限，不在本计划默认扩大。
- 日志脱敏：禁止输出 access/refresh token、完整手机号、身份证、驾驶证和精确历史轨迹。

## 7. 每步交付与验收记录

每完成一个步骤，必须在涉及模块的 `README.md` 更新“当前能力/运行方式/未实现范围”，并在对应
`CHANGELOG.md` 的 `Unreleased` 追加可追踪条目。提交信息遵循语意化规范，例如
`feat(passenger): add instant booking and quote flow`。涉及数据库、表、索引或后端接口变更时，
先与用户沟通并同步 `StarX-Car-Backend/create_sql.md` 与接口注解；本计划本身不修改数据库。

## 8. 明确暂不纳入

- 在线支付、退款、评价、司机收入和提现。
- 预约单、拼车、优惠券、动态加价、推送通知。
- WebSocket、完整轨迹历史和轨迹回放；第一阶段只保存/展示最新位置。
- 乘客端和司机端互相复用角色业务模型或把订单状态机移入 common。
