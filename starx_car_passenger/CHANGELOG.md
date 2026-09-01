# Changelog

## Unreleased

- 新增个人中心「我的投诉」页面，调用 `/api/complaints/me` 查看投诉原因、处理进度、管理员文字回复、处理方式、退款金额和结案时间；提交投诉后可直接打开进度页面。
- 新增完成和中途结束订单的七天内投诉入口；投诉失败只显示固定业务提示，不暴露服务端原始错误。
- 新增乘客订单结束流程：`SCHEDULED`/`PENDING`/`ACCEPTED`/`DRIVER_ARRIVED` 可取消，`IN_SERVICE` 可提交中途结束原因；终止后展示服务端实际里程、服务时长和结算金额，并将 `TERMINATED` 纳入终态及历史筛选。
- 统一乘客端 Material 3 视觉：调整绿色主题、页面背景、输入框、按钮、卡片和底部导航的圆角与间距；个人中心余额升级为可点击的高亮金额卡，叫车页的地点与报价面板增加层级阴影。

- 个人中心新增真实账户余额展示和模拟充值入口；充值成功后刷新余额，错误使用统一 API 异常提示。

- 新增预约叫车：叫车页顶部增加「即时叫车/预约叫车」切换；预约模式需选择预约上车时间（最早当前时间后 30 分钟、最远 7 天，由后端校验），报价与下单携带 `orderType=SCHEDULED` 与 `scheduledPickupTime`，创建成功后进入「等待派单」状态。
- 行程列表/详情适配预约单：列表显示预约上车时间，筛选新增「待派单」；详情在 `SCHEDULED` 状态展示预约上车时间与「系统将在预约时间前 30 分钟开始派单，派单前可免费取消」提示，并支持派单前取消订单；`SCHEDULED -> PENDING` 流转由既有状态轮询自动承接。
- `PassengerOrderRepository` 的 `quote`/`create` 新增可选 `orderType` 与 `scheduledPickupTime` 参数，预约时间按 `yyyy-MM-dd HH:mm:ss` 序列化；Repository 契约测试新增预约报价与预约创建 2 项。

- 行程详情页地图信息下方新增司机与车辆信息卡：接单司机评分、脱敏手机号、车牌号和车辆品牌/颜色/车型；「联系司机」按钮通过 `url_launcher` 以 `tel:` 调起系统拨号，用后端订单详情返回的司机完整手机号预填号码。新增 `url_launcher` 依赖；公共 `OrderDriver` 模型同步解析 `phoneNumber`、`rating`、`carBrand`、`carColor`、`carType`。

- 订单完成后可在行程详情对司机进行 1-5 星评价：未评价时展示星级选择与可选文字输入（最多 500 字），调用 `POST /api/orders/{orderNo}/rating` 提交，提交后只读展示评分、文字与提交时间；重复评价、订单未完成或超过 7 天时限由服务端返回 409，并以 SnackBar 提示。
- 个人中心新增「我的评价」入口，通过 `GET /api/passengers/me/ratings` 分页查看本人提交的评价（订单号、星级、文字、时间），支持下拉刷新。
- `PassengerOrderRepository` 新增 `rating`、`submitRating`、`myRatings` 方法及 `OrderRating`/`RatingSummary`/`RatingPage` 模型；Repository 契约测试新增 3 项（提交评分与文字、空白文字省略、评价查询与我的评价分页）。

- 行程中司机位置更新更及时：司机位置与接驾位置轮询从 4 秒缩短到 2 秒；服务中路线在司机移动超过 50 米或间隔 15 秒后以司机当前位置为起点重规划（原为 100 米/60 秒），上次规划失败且暂无路线时每 5 秒自动重试，大范围移动后能尽快刷新路线与视野。
- 服务中行程地图在路线上用蓝色标记实时标注司机当前位置，地图视野跟随司机当前位置与目的地，位置每次轮询更新后立即重绘，不再等待路线重规划才移动视野。


- 登录后首次进入叫车页自动请求定位权限：通过 common 的 `AmapPermissionService` 拉起系统授权，区分普通拒绝与永久拒绝并给出提示，授权成功后再自动发起首次定位；不再需要用户手动去系统设置开启权限。
- 接驾阶段（司机已接单、尚未开始服务）行程详情升级为接驾面板：地图同时绘制“司机当前位置 → 上车点”驾车路线（蓝色）与“乘客当前位置 → 上车点”步行路线（橙色），显示司机预计到达分钟数、乘客步行距离/分钟数；地图视野以乘客步行路线为主进行缩放。司机已到达时改为显示“司机已到达上车点”提示。
- 接驾面板持续轮询司机位置并自动重规划司机驾车路线（移动超过 100 米或间隔 30 秒）；乘客步行路线仅在乘客位置明显变化（超过 100 米）或 120 秒后重规划，乘客已在上车点附近时不再绘制步行路线。

- 修复行程详情页地图无法操控的问题：common 的 `AmapMapView` 注册手势识别器后，服务中路线地图和司机位置地图即使在详情 `ListView` 内也可自由放大、缩小、拖动；路线重规划时地图仍会自动调整到完整路线视野。

- 地点搜索从固定中心点 5 公里调整为同城优先、全国兜底；定位失败时不再使用北京默认坐标排序，搜索结果补充城市、区县、地址和距离。

- 地点搜索改为随输入动态联想，增加 350ms 防抖和旧请求失效保护；查询为空时明确显示无结果，搜索失败可就地重试，清空输入后恢复初始提示。

- 重构即时叫车首屏：上车地和目的地改为上下排列的地点入口并展示地点名，点击直接进入全屏搜索；首次进入自动尝试定位并把地图移至当前位置。
- 两个地点选定后自动规划高德推荐驾车路线，地图按路线边界调整比例尺，并展示距离、预计时长及可重试的路线错误。
- 修复部分高德 POI 坐标超过后端 6 位小数约束导致报价提示“数字的值超出了允许的范围”；Repository 测试新增高精度坐标覆盖。
- 补充个人资料、手机号资料的失败重试，并让修改密码长度不足时显示明确提示。

- 同步当前依赖对应的 Linux、macOS 和 Windows Flutter 插件注册器；vendor 高德插件示例工程保留在本地但不再出现在 Git 工作区状态中。

- 恢复 Android Flutter/Gradle 忽略规则，停止追踪 Gradle wrapper、原生生成注册器和构建报告；本机高德 Key、SDK 路径、签名配置与缓存继续保留在工作区但不进入 Git。

- 修复乘客端高德地图启动时缺少 `ACCESS_NETWORK_STATE` 等权限导致的 `SecurityException`；网络、Wi-Fi、前台定位和 A-GPS 权限及 `APSService` 现在统一声明在主清单，对 debug/release 均生效且不申请后台定位。
- 核对运行 APK 的 debug SHA-1、包名和注入 Key 与高德控制台配置一致；定位 SDK 从 5.6.0 升级到仍支持 x86_64 模拟器的 6.5.1，消除 Android 16 上的电话权限异常和用户可见 Key 错误遮罩。美国运营商模拟器仍可能打印非阻断的 `SERVICE_NOT_EXIST`，但坐标、蓝点和上车点选择均已实测成功。
- 当前位置仅请求业务所需的 GCJ-02 坐标，不再连带请求未使用的地址信息，避免额外逆地理编码鉴权失败遮挡地图。

- 完成本阶段 Android Release 联调记录：确认后端默认监听 `8080`、模拟器宿主机地址使用 `10.0.2.2`，并补充 API 地址注入、旧 APK 和 HTTP 明文流量排查说明。

- 修复登录失败时 Session Gate 重建登录页导致输入内容被清空且错误未提示的问题；提交状态改由登录页管理，API 错误现在显示为 SnackBar，Release 登录失败行为与 Debug 一致。
- 增加 `auth_error_test.dart` Android integration test，验证 API 不可达时登录错误可见且输入值保留。

- 在 `Pixel 9 Pro (Android 16/API 36)` 模拟器完成启动级 Android integration test；乘客 APK 可安装并渲染会话 Gate。补充记录高德 Key、地图、定位和 POI 仍需专项实测。
- 增加依赖真实 Android 的高德地图 Widget 挂载 integration test，验证 GCJ-02 初始点和原生地图插件创建路径。
- 模拟器日志确认高德 SDK 返回 `INVALID_USER_SCODE`；记录当前乘客包名及 debug/release 签名 SHA-1，便于在高德控制台修正 Key 限制。
- 记录全局 Flutter 3.19.6 触发的旧 Android embedding 误报；文档明确客户端必须使用 FVM Flutter 3.44.8。
- 增加一次性定位 integration test；模拟器可收到结构化定位错误回调（errorCode=12），未发生超时或崩溃，真实位置仍需授权并配置 GPS 后验收。

- 为行程列表首屏和分页请求接入 `CancelToken`；切换筛选、刷新、离开页面或 Provider 销毁时取消无效请求，主动取消不再显示网络错误。

- 增加乘客端注销账号入口：二次确认后调用软注销接口，成功清理本地会话；服务端保留历史行程并拒绝后续登录。

- 补齐 C-04 当前位置入口：调用 common 一次性高德定位，将当前 GCJ-02 坐标作为上车点或目的地，并处理定位超时、权限和 GPS 失败提示。

- 为报价和即时下单接入 common `CancelToken`；处理中显示取消图标，页面销毁时自动取消请求，主动取消不会误提示网络错误。

- 为订单详情、司机位置和服务中路线的上下文请求接入 Provider 生命周期取消；页面离开时取消正在进行的 HTTP 请求，不再进入退避重试。

- 修复新建行程详情只读取一次订单状态的问题；现在详情页在前台按 4 秒间隔轮询订单状态，进入完成/取消终态后自动停止，并在状态切换后显示对应的司机位置或服务中路线面板。

- 增加报价前的地点校验，提前拦截相同上车点/目的地和非 GCJ-02 坐标，减少无效路线请求。

- 修复乘客确认叫车后只停留在选点页的问题；创建成功后立即打开新订单详情，并使行程列表缓存失效。

- 明确高德 Android Key 的实际配置位置为 `android/amap.properties`，避免误用 `--dart-define` 导致原生地图 Key 未注入。

- 增加 `integration_test/app_start_test.dart` 启动级 Android 集成测试，验证应用能够渲染会话 Gate；测试依赖真实设备执行。

- 新增乘客认证 Controller 测试，验证仅恢复 passenger 会话、匹配登录持久化和错误角色登录不落盘。

- 新增乘客订单 Repository 契约测试，覆盖 GCJ-02 地点请求、报价解析、分页状态筛选、取消原因清理及 stale 路线上下文。

- 修复 release 签名文件路径按 app module 解析导致找不到共享 keystore 的问题；现在从 Android 项目根目录解析，并验证 release APK 可构建、可通过 APK v2 签名校验。

- Add passenger trip status filters and render the server-provided order-event timeline in trip details.

- Add bounded exponential backoff and manual retry actions for passenger driver-location and in-service route polling; terminal 404/409 responses still stop polling.

- Add the shared client API contract link and document the current logout and account-deletion behavior before the real-device acceptance flow.

- Add scroll-triggered paginated trip loading with refresh/error recovery, and stop driver-location/route polling while the Android app is not resumed.
- Add native AMap Android driving-route planning to the in-service trip panel, drawing the returned GCJ-02 polyline while retaining driver/destination markers when route planning fails.
- Package the official AMap 3D map and location Android SDKs explicitly in the passenger app runtime so the vendored Flutter wrappers cannot compile without the native SDK at install time.

- Add four-second auto-disposed driver-location polling for ACCEPTED/DRIVER_ARRIVED orders and route-context map rendering for IN_SERVICE orders, preserving stale-location messaging and GCJ-02 markers.

- Add `PassengerOrderRepository` for quote, immediate order creation, paginated history, detail, passenger cancellation, driver-location polling and in-service route-context endpoints.
- Add GCJ-02 validation and backend request serialization to the shared `Place` model; passenger order API calls can now be wired directly to map-selected places.
- Add the passenger "My Trips" navigation tab with paginated first-page loading, details, fare breakdown, assigned-driver display and state-gated cancellation for PENDING/ACCEPTED orders.

- Android Gradle 配置关闭 Kotlin 增量缓存并改用进程内编译，修复 Windows 下工程目录与 Pub 缓存分属不同盘符时 `image_picker_android` 编译失败的问题。

- 个人资料与乘客资料 Provider 改为依赖当前认证会话；退出或切换账号后会使旧缓存失效，防止新会话短暂展示上一乘客的资料或手机号。

- 个人资料编辑移除手工头像 URL 输入，改用系统相册选择图片并随 `PUT /api/users/me` Multipart 表单上传；个人中心展示后端保存的头像 URL，并保留首字默认头像。

- 注册与个人中心适配真实姓名、头像地址和账号状态字段；公共用户资料模型同步读取新版响应。

- Add passenger phone-number display and update flow through `/api/passengers/me`, including refresh of the backend-maintained masked number.

- Rewrite README with the implemented passenger authentication/profile workflow, Riverpod structure, backend endpoint mapping, Android configuration, validation commands and remaining business scope.

- Split the Flutter entry-file implementation into app, core providers, authentication feature and profile feature layers.
- Fix template-test imports and all analyzer errors introduced during the initial client implementation.
- Pin the Android path-provider implementation to avoid the incompatible `jni` Gradle plugin during Android builds.
- Delay disposal of profile-dialog text controllers until dialog exit animations have finished, preventing focused fields from using a disposed controller.

- Add Riverpod-managed passenger login, registration, encrypted session restoration/logout and API error presentation.
- Add passenger personal center for viewing/updating base profile and changing password with current-password verification.
- Enable Android local HTTP development traffic for the configurable backend API address.
- Replace the Flutter counter template test with a passenger app-root smoke test.

- Configure the Android package ID as `org.mannerth.starxcar.passenger` and connect the release build to the local shared signing certificate.
- Document the passenger-app responsibility boundary, including ride-hailing, trip, payment and evaluation workflows, and its dependency on `starx_car_common`.
