# Changelog

## Unreleased

- 完成本阶段 Android Release 联调记录：确认后端默认监听 `8080`、模拟器宿主机地址使用 `10.0.2.2`，并补充 API 地址注入、旧 APK 和 HTTP 明文流量排查说明。

- 修复登录失败时 Session Gate 重建登录页导致输入内容被清空且错误未提示的问题；提交状态改由登录页管理，API 错误现在显示为 SnackBar，Release 登录失败行为与 Debug 一致。
- 增加 `auth_error_test.dart` Android integration test，验证 API 不可达时登录错误可见且输入值保留。

- 在 `Pixel 9 Pro (Android 16/API 36)` 模拟器完成启动级 Android integration test；司机 APK 可安装并渲染会话 Gate。补充记录高德 Key、定位、GPS 和前台服务仍需专项实测。
- 模拟器日志确认高德 SDK 返回 `INVALID_USER_SCODE`；记录当前司机包名及 debug/release 签名 SHA-1，便于在高德控制台修正 Key 限制。
- 记录全局 Flutter 3.19.6 触发的旧 Android embedding 误报；文档明确客户端必须使用 FVM Flutter 3.44.8。
- 增加依赖真实 Android 的高德地图 Widget 挂载 integration test，验证 GCJ-02 初始点和原生地图插件创建路径。
- 增加司机前台服务启动/停止 integration test；在已授予定位与通知权限的 Android 16 模拟器上通过。

- 增加司机端注销账号入口：二次确认后调用软注销接口，保留司机资料、车辆绑定和历史订单，成功后清理本地会话。
- 注销请求遇到进行中订单时显示服务端 409，要求司机先完成或取消订单，避免账号注销后订单悬空。

- 为司机位置上报、待抢订单和已接订单查询接入 common `CancelToken`；下线、退出或 Provider 销毁时取消在途请求，取消不会进入位置退避重试。

- 修复工作台进行中订单的“完成并结算”快捷操作丢弃服务端结算结果的问题；现在快捷完成后同样展示实际服务时长、等待时长和最终金额，并在关闭弹窗后刷新订单列表。

- 修复抢单成功后已接订单区不立即更新的问题；现在同时刷新 pending/owned 列表并打开订单详情，司机可直接继续到达流程。

- 明确高德 Android Key 的实际配置位置为 `android/amap.properties`，避免误用 `--dart-define` 导致原生地图 Key 未注入。

- 增加 `integration_test/app_start_test.dart` 启动级 Android 集成测试，验证应用能够渲染会话 Gate；测试依赖真实设备执行。

- 新增司机认证 Controller 测试，验证仅恢复 driver 会话、匹配登录持久化和错误角色登录不落盘。

- 新增司机 Repository 契约测试，覆盖多车辆当前指针、GCJ-02 位置请求、WGS-84 防混用、状态流转请求及完成结算解析。

- 修复 release 签名文件路径按 app module 解析导致找不到共享 keystore 的问题；现在从 Android 项目根目录解析，并验证 release APK 可构建、可通过 APK v2 签名校验。

- Add local loading locks to pending-order accept and active-order transition buttons, preventing duplicate submissions while a request is in flight.

- Add the multi-vehicle list and current-car switch UI; the page now uses `driver.carId` as the current pointer and keeps other active bindings available.

- Add the shared client API contract link and document the current logout and account-deletion behavior before the real-device acceptance flow.

- Pause location reporting and pending-order refresh when the app is backgrounded, then restart them after resume while preserving the online work state.
- Add the driver-only Android `DriverForegroundService` and Flutter control channel; online status now starts a location-typed foreground notification before enabling server work status, and startup failure does not leave a foreground service running.
- Make driver location reporting serial and latest-point-wins, with timeout, exponential retry, and visible AMap permission/GPS errors; initialize location tasks when an existing online session is restored.
- Package the official AMap 3D map and location Android SDKs explicitly in the app runtime; the vendored Flutter wrappers remain compile-time adapters only.
- Add driver order details with GCJ-02 pickup/destination map markers, AMap external navigation actions, guarded arrive/start/complete transitions, and a final settlement dialog using server-calculated fare values.

- Add active-order actions to the driver workbench: `ACCEPTED -> DRIVER_ARRIVED -> IN_SERVICE -> COMPLETED`, with server-calculated completion response and refresh of pending/owned order lists.

- Add driver work tab with online/offline control, AMap location lifecycle, three-second GCJ-02 location reporting, five-second pending-order refresh and state-safe accept action.

- Correct current-vehicle lookup to use `/drivers/me` plus `/drivers/me/cars`, honoring the multi-vehicle binding model and `driver.carId` current-vehicle pointer.
- Add driver Repository methods for current-car switching, online/offline work status, GCJ-02 location reporting with backend time format, pending/owned order queries, accept/cancel, arrive, start-service and complete operations.

- 车辆创建/编辑表单将车牌省份简称、车牌字母改为固定下拉选择；字母排除 I/O，并在保存前再次校验。颜色改为白色、黑色、银色、灰色、红色、金色、蓝色、棕色、紫色、绿色、黄色、粉色、橙色的固定选项。

- Android Gradle 配置关闭 Kotlin 增量缓存并改用进程内编译，修复 Windows 下工程目录与 Pub 缓存分属不同盘符时 `image_picker_android` 编译失败的问题。

- 基础资料、司机资料和当前车辆 Provider 现在依赖司机认证会话；退出或切换司机后旧缓存会失效，避免展示上一账号的数据。

- 司机个人中心新增头像展示和相册选图上传；头像文件通过 `PUT /api/users/me` 与当前资料一次提交，由后端图床服务保存 URL，取消手工头像 URL 编辑。

- 司机注册和资料页适配真实姓名、头像、身份证号码、驾驶证有效期、审核状态与工作状态。
- 车辆页面改用拆分车牌、登记归属地、核定载客数和出厂年份；不再提交旧版 `carNumber` 请求字段。
- 资料编辑将性别改为中文二选一，驾驶证有效期改用日期选择器，车辆出厂年份改用年份下拉选择；可选证件和日期字段可清空。

- Add display and edit flow for driver username, email and gender through `/api/users/me`; the profile now exposes all currently supported account and driver fields.

- Rewrite README with the implemented driver authentication/profile/vehicle workflow, modal-controller safety, API mapping, Android signing behavior, validation commands and pending business scope.

- Split the Flutter entry-file implementation into app, core providers, authentication and driver-home feature layers.
- Fix the malformed initial widget tree, update template-test imports and clear analyzer errors.
- Pin the Android path-provider implementation to avoid the incompatible `jni` Gradle plugin during Android builds.
- Allow debug APK assembly without local release signing credentials; release signing remains applied whenever `android/key.properties` is present.
- Move driver dialog text-controller ownership into stateful dialog widgets so controllers remain valid through focus and route-exit animations.

- Add Riverpod-managed driver login, registration, encrypted session restoration/logout and API error presentation.
- Add driver personal center for account, phone number, driving licence and password updates.
- Add create-and-bind and edit flows for the driver's currently bound vehicle.
- Enable Android local HTTP development traffic for the configurable backend API address.
- Replace the Flutter counter template test with a driver app-root smoke test.

- Configure the Android package ID as `org.mannerth.starxcar.driver` and connect the release build to the local shared signing certificate.
- Document the driver-app responsibility boundary, including work status, order handling, navigation, location reporting and income workflows, and its dependency on `starx_car_common`.
