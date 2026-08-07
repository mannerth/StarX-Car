# Changelog

## Unreleased

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
