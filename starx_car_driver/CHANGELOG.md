# Changelog

## Unreleased

- 修复待抢订单抢单按钮在全局 `FilledButton` 最小宽度为无限时触发 `BoxConstraints forces an infinite width` 的渲染异常；按钮现在使用有限宽度和局部最小尺寸，兼容订单卡片的横向布局。

- 修复待抢订单卡片在长地址或窄屏下触发 `RenderBox was not laid out` 的布局异常：移除带复杂标题行和 trailing 按钮的 `ListTile` 组合，改为显式约束的左右布局，地址文本最多显示两行并稳定省略。

- 修复司机听单时高德返回高精度 `bearing` 导致位置上报被后端 `@Digits(integer = 3, fraction = 2)` 拒绝的问题：`accuracyM`、`speedMps`、`bearing` 现在统一保留两位小数，非有限值或超出接口范围的可选值会被省略，不再阻断有效 GCJ-02 经纬度上报。
- 修复待抢订单和进行中订单接口响应超过 5 秒时，定时刷新反复取消在途请求并导致界面一直转圈的问题；轮询现在等待当前请求完成或失败后才发起下一次刷新，并新增加载中不刷新回归测试。
- 司机工作台不再展示位置 SDK、后端校验或接口返回的原始错误文本；位置上报失败继续在后台按既有退避策略自动重试，列表失败仅显示固定提示和重试操作，抢单失败显示固定的用户提示。

- 修复 Android Debug 构建在 `dl.google.com:443` 超时时无法下载 AndroidX AAR：插件与项目依赖仓库均增加阿里云 Google 镜像和华为云镜像，并保留官方 Google/Maven Central 回退；移除了指向未运行的 `localhost:7879` 的 Gradle Wrapper 代理配置，避免 Wrapper 重新下载时同样失败。

- 统一司机端 Material 3 视觉：调整蓝色主题、页面背景、输入框、按钮和卡片；听单状态升级为高亮工作卡，待抢订单改为独立卡片，个人中心提现余额升级为高亮金额卡。

- 个人中心新增收入余额展示与提现入口；提现后即时刷新余额，服务端拒绝导致余额为负的请求。

- 修复乘客取消已接订单后司机端状态不自动更新的问题：司机在线时工作台每 5 秒同时刷新待抢订单和进行中订单；订单详情在接单、已到达或服务中时每 3 秒轮询服务端，收到取消或完成状态后自动停止轮询并渲染最新状态，无需重新进入页面。

- 待抢订单列表支持预约单：已派单的预约单显示蓝色「预约」徽标与「预约 MM-dd HH:mm 上车 · 预估 ¥xx」信息；司机订单详情在预约单上展示「预约上车时间」行，抢单后可按预约时间准时到达。

- 个人中心新增「历史订单」入口：新增 `DriverOrdersHistoryPage`，按全部/已完成/已取消筛选分页查看本人历史订单，滚动自动加载更多，点击进入既有订单详情页；复用 `DriverRepository.orders` 的状态筛选与分页能力。

- 个人中心新增「我的评价」入口，通过 `GET /api/drivers/me/ratings` 分页查看乘客对本人的评价（订单号、星级、文字、时间），列表顶部显示评价总数并支持下拉刷新。
- `DriverRepository` 新增 `myRatings` 方法及 `RatingSummary`/`RatingPage` 模型；Repository 契约测试新增 1 项（评价历史分页解析）。

- 司机位置上报间隔从 3 秒缩短到 2 秒，配合乘客端 2 秒位置轮询，行程中司机位置在乘客端能更及时地更新。


- 司机端 `debug` 构建改为复用 `android-signing/starx-car-release.jks` 发布签名（`android/app/build.gradle.kts`），debug 与 release 证书 SHA-1 统一为 `4C:83:43:EE:4B:E8:5E:0B:89:7A:29:E9:83:D8:17:C1:9A:0C:BF:73`（`gradlew :app:signingReport` 验证通过）；高德控制台司机端 Key 只需登记这一份签名。`profile` 变体仍使用默认 debug.keystore（SHA-1 `1C:50:46:29:AE:A7:92:22:36:9C:CB:83:B7:E7:53:D1:1B:AE:D8:28`），如需统一可再改。

- 司机订单详情地图升级：接单后标注司机当前位置（蓝色标记），`ACCEPTED` 状态绘制“司机当前位置 → 上车点”驾车路线，`IN_SERVICE` 状态绘制“司机当前位置 → 目的地”驾车路线；位置每 2 秒更新，司机移动超 100 米或间隔 30 秒自动重规划剩余路线，服务中实时标注当前行进位置。
- common 的 `AmapMapView` 支持按标记自定义颜色（`markerColors`），用于区分司机当前车辆与上车点/目的地。


- 订单详情在 `DRIVER_ARRIVED` 状态展示乘客手机尾号（后四位）；点击“开始服务”必须先输入乘客手机尾号，与订单返回值一致且通过服务端校验后才进入 `IN_SERVICE`，尾号不一致会在客户端提示并中止请求。
- 司机端开始服务接口改为携带 `{"phoneTail":"1234"}` 请求体，对接后端新增的乘客尾号核对逻辑；Repository 契约测试同步覆盖请求体。

- 修复司机位置上报经纬度超过后端 `DECIMAL(..., 6)` 小数位导致的校验失败；客户端现在统一截断/四舍五入为 6 位小数。
- 修复听单 Provider 的 Riverpod 循环依赖；待抢订单改为读取而非监听工作状态，并在上线/下线完成后主动刷新。
- 补充高德 `SERVICE_NOT_EXIST` 排查说明：该错误来自高德控制台未给司机 Android Key 开通定位服务，需同时核对包名、签名 SHA-1 和定位服务权限。
- 定位插件挂载时从 Android 清单读取高德 Key 并显式设置到定位 SDK，避免清单注入时序导致 Key 未生效。

- 移除听单页嵌套的第二层标题栏；司机首页只保留统一工作台导航。
- 离线时停止请求待抢订单；待抢和进行中订单加载失败增加就地重试，司机资料及车辆资料失败页不再暴露底层异常并可重新加载。
- 抢单增加二次确认；进行中订单列表改为统一进入详情页，避免在拥挤列表中误触到达、开始或完成操作，状态推进继续使用详情页既有确认流程。

- 同步当前依赖对应的 Linux、macOS 和 Windows Flutter 插件注册器；vendor 高德插件示例工程保留在本地但不再出现在 Git 工作区状态中。

- 恢复 Android Flutter/Gradle 忽略规则，停止追踪 Gradle wrapper、原生生成注册器和构建报告；本机高德 Key、SDK 路径、签名配置与缓存继续保留在工作区但不进入 Git。

- 修复听单开关逐项请求权限的问题：首次上线现在一次提交粗略/精确定位和 Android 13+ 通知权限，等待整组结果后自动继续启动前台服务，不再要求每缺一项权限就重新点击开关。
- 高德定位 SDK 从 5.6.0 升级到仍支持 x86_64 模拟器的 6.5.1，并补齐地图/定位网络、Wi-Fi、A-GPS 权限及独立 `APSService`，同步应用乘客端已验证的 Android 16 兼容修复。
- 增加司机端高德一次性定位 integration test；Android 16 模拟器已完成地图挂载、整组权限请求及成功定位实测，定位返回 `hasPoint=true, errorCode=0`，司机 Key 未再出现签名、海外或服务不存在鉴权错误。

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
