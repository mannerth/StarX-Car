# StarX-Car Flutter 公共包

`starx_car_common` 是 StarX-Car 乘客端与司机端共用的本地 Flutter package，不发布至 pub.dev。它提供跨角色基础设施与基础模型，绝不包含角色页面、路由、状态管理或订单业务。

公共包还提供余额模型和 `WalletApi` 传输层；具体的充值、提现和订单结算流程仍分别属于乘客端、司机端和后端。

当前使用 FVM Flutter `3.44.8`、Dart `3.12.2`；两个 App 以 path 依赖引用本包。

## 当前状态

公共认证、网络基础设施和高德 Android 地图/定位/驾车/步行路线适配以及定位权限请求已经可供两个 Android App 使用；订单展示模型、GCJ-02 校验、高德外部导航、POI 搜索和逆地理编码均已加入 common。订单流程仍由角色端实现。包级静态检查和单元测试均已通过。

## 已实现能力

### 会话与基础用户模型

- `AuthSession`：保存 access token、refresh token、当前用户 ID 与角色；支持 JSON 序列化，供安全存储恢复会话。
- `UserProfile`：解析用户名、真实姓名、头像地址、账号状态、邮箱、性别与角色，供两个角色端展示基础资料。
- `SessionStore`：用 `flutter_secure_storage` 加密保存、读取与清除会话。两个 App 启动时均通过它恢复登录态，退出时清除本地令牌。
- `UserProfile`：角色无关的用户基础资料模型，包含 ID、用户名、邮箱、性别与角色。

### HTTP 与令牌刷新

- `ApiClient` 基于 Dio，统一使用后端 `/api` 响应信封，成功时返回 `data` 字段。
- `ApiClient.putMultipart`：统一发送带 Bearer Token 的 Multipart 请求；根据文件名推导图片 MIME 类型，确保头像文件能通过后端类型校验；乘客端和司机端不直接访问图床服务。
- 每个请求自动读取安全会话并附加 `Authorization: Bearer <accessToken>`。
- `get`、`post`、`put`、`delete` 和 `putMultipart` 均支持可选的 `CancelToken`；调用方取消后统一收到 `ApiException.cancelled == true`，页面可安全忽略该结果。
- 收到业务请求的 `401` 时，使用 refresh token 请求 `/auth/refresh`；刷新请求不会附带旧 access token，同一时刻的多个 `401` 合并为一次刷新请求，刷新成功后自动重放原请求。
- 刷新失败或不存在会话时清除本地会话；调用方可据此返回登录页。
- `ApiException` 只保留可展示的服务端消息、HTTP 状态、trace ID 和是否由调用方取消，不暴露底层异常详情。

### 通用模型

- `ApiResponse<T>`：对应后端 `{ code, message, traceId, data }` 信封。
- `GeoPoint`、`CoordinateSystem`、`Place`：为地图与地点选择提供基础类型；提交后端时坐标统一保留 6 位小数，匹配接口精度约束。
- 高德业务坐标统一按 `GCJ-02` 使用；设备 GPS 的 `WGS-84` 必须在地图基础层明确转换或标注，业务端不得混用。
- `OrderStatus`、`FareQuote`、`OrderQuote`、`OrderSummary`、`OrderPage`、`OrderDetail`、`OrderEvent`、`OrderCompletionResult`、`DriverLocation` 和 `RouteContext`：只负责解析跨角色订单展示字段；金额保留十进制字符串，位置模型保留 `stale` 标志，订单事件枚举保留原始字符串以兼容服务端扩展。
- `OrderType`（`INSTANT`/`SCHEDULED`）：`OrderSummary`/`OrderDetail`/`OrderQuote` 均解析 `orderType` 与预约时间 `scheduledPickupTime`，`OrderDetail` 另解析 `dispatchTime`；订单为预约单时 `isScheduled` 为 true，旧服务端缺少字段时按即时单处理。

### 高德 Android 基础能力

- 本地 vendor 的 `amap_flutter_map`、`amap_flutter_location` 和 `amap_flutter_base` 适配 Flutter 3.44；底层仍使用高德官方 Android SDK。
- 本地 `amap_flutter_map` 已移除地图加载完成时调试分支中的无效空数组访问，地图首次启动不再固定打印 `ArrayIndexOutOfBoundsException`。
- `amap_flutter_location` 使用定位 SDK 6.5.1，兼顾新版 Android 兼容修复与 x86_64 模拟器运行能力。
- `AmapMapView` 接受 GCJ-02 标记、主路线折线和多条不同颜色的路线折线；标记、路线或定位点变化后会自动居中，多个点会自动缩放至完整可见，调用方可指定 `viewportPoints` 控制视野以某条路线为主。地图已注册手势识别器，即使嵌入 `ListView` 等滚动容器仍支持拖动、缩放、旋转和倾斜操控。`AmapLocationService` 支持持续定位流和一次性当前位置请求，定位只请求坐标，不额外调用地址解析服务或消耗逆地理编码配额。
- `AmapPermissionService` 通过 common 的 Android 插件通道查询和申请前台定位权限，返回“已授予 / 普通拒绝 / 永久拒绝”结果；乘客端登录后和司机端上线前可据此主动拉起系统授权，不再依赖高德定位 SDK 的被动失败提示。
- `AmapNavigationService` 只负责打开已安装的高德地图进行驾车导航，调用方负责订单状态和位置上报策略。
- `AmapRouteService` 通过官方 Android 路线 SDK 返回驾车与步行路线的距离、时长和 GCJ-02 折线；不使用 Web Service Key。
- `AmapSearchService` 支持 POI 搜索和逆地理编码：有定位城市时先在同城搜索并按当前位置距离排序，同城无结果自动进行全国搜索；结果携带城市、区县和可用距离并转换为 `Place`。

## 职责边界

```text
starx_car_passenger ─┐
                     ├──> starx_car_common
starx_car_driver ────┘
```

可放入：网络、会话、令牌刷新、安全存储、通用错误、地图底层能力与跨角色模型。

不得放入：乘客/司机页面与 Riverpod 状态、完整角色 Repository、叫车/接单/支付/评价/收入流程、订单状态机、派单、定价、数据库访问，以及任何高德 Key 或签名资料。

## 依赖与 Android 兼容性

- `dio`：HTTP 传输。
- `flutter_secure_storage 9.2.4`：Android Keystore 安全存储。
- `url_launcher 6.3.2`：打开高德地图外部导航 URI。
- Android app 显式打包高德 `3dmap:8.1.0`、`location:6.5.1` 和 `search:9.7.0` 官方 SDK；Flutter wrapper 的 `compileOnly` 声明不会替代运行时依赖。
- `path_provider_android 2.2.22`：在包与两个 App 中固定该版本，避免新版本引入的 `jni` Android Gradle 插件与当前 Flutter 3.44 Gradle 模板冲突。

## 使用与验证

跨角色后端字段、状态机、错误码和坐标约定见仓库根目录的
[`docs/client-api-contract.md`](../docs/client-api-contract.md)。

```yaml
dependencies:
  starx_car_common:
    path: ../starx_car_common
```

```powershell
cd starx_car_common
fvm flutter pub get
fvm flutter analyze
fvm flutter test
```

## 尚未实现

- 高德 POI、逆地理编码和路线搜索的更多策略（当前已支持基础搜索、逆地理编码和驾车路线）。
- WebSocket 的连接、重连、心跳和通用事件信封。
- 分页及更多跨角色展示模型。

