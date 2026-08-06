# StarX-Car 乘客端

`starx_car_passenger` 是 StarX-Car 的乘客 Android App，依赖本地 `starx_car_common` 提供会话、网络和高德地图基础能力。本阶段已完成账号、即时叫车、订单详情、司机位置轮询和服务中路线展示。

## 当前状态

登录、乘客注册、会话恢复、退出、个人资料编辑与修改密码均已对接现有后端；即时叫车支持地图选点、报价、创建、司机到达前取消、订单列表/详情、司机位置和服务中高德驾车路线折线。项目采用 Riverpod 管理登录态；`flutter analyze`、`flutter test` 和 Android debug APK 构建均已通过。认证 Controller 测试覆盖角色会话恢复、匹配登录保存和错误角色拒绝；Repository 契约测试覆盖报价地点序列化、状态筛选、取消原因清理和路线位置解析。

## 已实现功能

### 认证与会话

- 登录：`POST /api/auth/login`，仅接受后端返回 `passenger` 角色的账号。
- 登录提交期间保留表单内容；账号、密码或网络错误会在当前页面显示提示，不会重建页面或静默失败。
- 注册：`POST /api/auth/passengers/register`，收集用户名、密码、邮箱、真实姓名、性别、11 位手机号；注册成功后直接保存令牌并进入个人中心。
- 会话恢复：启动时从公共包的加密存储读取会话，只有乘客角色会进入已登录界面；切换账号时资料 Provider 会随会话失效并重新加载，避免展示上一账号的缓存资料。
- 退出：清除加密存储中的 access token 与 refresh token，返回认证页。
- 注销账号：二次确认后调用 `DELETE /api/passengers/me`；服务端保留乘客资料和历史行程并标记账号 `cancelled`，成功后清理本地会话且不可恢复登录。
- 请求鉴权和 401 刷新全部由公共 `ApiClient` 完成，乘客业务代码不重复管理 Token。

### 个人中心

- 查看当前用户的用户名、真实姓名、邮箱、性别和后端脱敏手机号。
- 调用 `PUT /api/users/me` 的 Multipart 资料表单修改用户名、真实姓名、邮箱和性别；从系统相册选择头像文件后与资料一次提交，由后端上传并保存头像 URL。
- 个人中心优先展示后端返回的头像 URL；未上传头像时使用用户名或真实姓名首字作为默认头像。
- 调用 `PUT /api/passengers/me` 修改手机号；手机号更新后由后端触发器维护脱敏展示值。
- 调用 `POST /api/users/me/password`，验证原密码后修改密码。
- 资料编辑与密码弹窗会等待退出动画结束后再释放输入控制器，焦点停留在输入框时保存或取消不会使用已释放的 controller。
- 展示后端安全消息；网络、认证与服务端错误不会暴露底层异常。

### 即时叫车与行程

- 地图点击、搜索地点或使用当前位置选择 GCJ-02 上车点和目的地；本地先校验坐标系和两点不能相同，再调用 `/orders/quote`。当前位置定位失败时保留现有选点并提示重试。
- 展示起步、距离、行程时间、等待和总价五项预估费用；确认后调用 `/orders` 创建即时订单，并直接进入新建 `PENDING` 行程详情。报价和下单请求支持取消，处理中可用关闭图标终止，页面离开时也会自动取消未完成请求。
- 我的行程支持分页、状态筛选、详情时间线、状态时间和费用明细；新建详情会在前台约每 4 秒刷新订单状态，自动从等待接单切换到接驾、行程中和完成状态；`PENDING`/`ACCEPTED` 可在司机到达前取消。
- 行程列表首屏、刷新和分页请求绑定页面生命周期；切换筛选或离开页面会取消未完成请求，避免继续加载旧筛选结果。
- 接驾阶段约每 4 秒轮询司机位置，页面销毁后自动停止并取消正在进行的 HTTP 请求；`stale` 位置会标记为最后已知位置。
- 订单状态、位置和路线上下文请求遇到临时网络错误时按 1/2 秒有限退避重试，连续失败后提供手动重试；页面离开、后台和终态会停止任务并取消进行中的请求。
- `IN_SERVICE` 通过 common 的高德 Android 路线 SDK 规划“司机当前位置 -> 目的地”，失败时保留两个标记并提供路线重试；`stale` 位置会明确标注为最后已知位置。

## 工程结构

```text
lib/
  main.dart                         ProviderScope 启动入口
  app.dart                          MaterialApp、主题、会话分流
  core/providers.dart               API 地址、SessionStore、ApiClient Provider
  features/
    auth/
      auth_controller.dart          Riverpod AsyncNotifier 登录、注册、退出
      auth_page.dart                登录/注册表单
    profile/
      passenger_repository.dart     当前用户资料和密码 API
      profile_page.dart             个人中心与编辑弹窗
    booking/
      booking_page.dart              地图点选、报价和即时下单
    orders/
      passenger_order_repository.dart  报价、即时订单、位置和路线 API
      passenger_orders_page.dart        我的行程、状态/位置轮询、取消和服务中地图
```

依赖方向保持为 `starx_car_passenger -> starx_car_common`。乘客页面、资料 Repository 和 Riverpod 状态只保留在本项目。

## 后端接口

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 乘客账号登录 |
| `POST` | `/api/auth/passengers/register` | 创建乘客账号并返回令牌 |
| `POST` | `/api/auth/refresh` | 由公共 HTTP 层自动调用 |
| `GET` | `/api/users/me` | 获取当前基础资料 |
| `PUT` | `/api/users/me` | 修改用户名、邮箱、性别 |
| `POST` | `/api/users/me/password` | 验证原密码后修改密码 |
| `GET` / `PUT` / `DELETE` | `/api/passengers/me` | 获取、修改手机号或注销账号（保留历史记录） |
| `POST` | `/api/orders/quote` | 获取即时订单预估报价 |
| `POST` | `/api/orders` | 创建即时订单 |
| `GET` | `/api/passengers/me/orders` | 分页查询乘客行程 |
| `GET` | `/api/orders/{orderNo}` | 查询行程详情 |
| `POST` | `/api/orders/{orderNo}/cancel` | 司机到达前取消 |
| `GET` | `/api/orders/{orderNo}/driver-location` | 查询司机最后位置 |
| `GET` | `/api/orders/{orderNo}/route-context` | 获取服务中路线起点和目的地 |

## Android 配置、运行与验证

- application ID：`org.mannerth.starxcar.passenger`。
- 跨角色接口字段、状态机、错误码和坐标约定见仓库根目录的 [`docs/client-api-contract.md`](../docs/client-api-contract.md)。
- 默认 API 地址：`http://10.0.2.2:8080/api`，适用于 Android 模拟器访问本机后端；也可用 `--dart-define=API_BASE_URL=http://<host>:<port>/api` 覆盖。
- 高德 Android Key 配置在本项目 `android/amap.properties` 的 `AMAP_ANDROID_KEY`；该文件已忽略且不能提交。`--dart-define` 只用于 API 地址，不能替代原生清单 Key。
- 为本地 HTTP 开发已允许 Android 明文流量；部署环境必须使用 HTTPS。
- 因工程位于 D 盘、Flutter Pub 缓存位于 C 盘，已关闭 Kotlin 增量编译并使用进程内编译，避免 `image_picker_android` 的跨盘缓存错误；首次 Android 构建会稍慢。
- `android/key.properties` 与 release keystore 均为本机敏感文件，已忽略，不能提交或分享。
- release 签名路径从 Android 项目根目录解析，当前本机配置可使用仓库根目录 `android-signing/starx-car-release.jks` 构建；APK 证书 SHA-1 为 `4C:83:43:EE:4B:E8:5E:0B:89:7A:29:E9:83:D8:17:C1:9A:0C:BF:73`，须在高德控制台登记到乘客端 Key。
- 当前本机 debug APK 证书 SHA-1 为 `1C:50:46:29:AE:A7:92:22:36:9C:CB:83:B7:E7:53:D1:1B:AE:D8:28`；若出现高德 `INVALID_USER_SCODE`，先确认乘客端 Key 的 Android 包名为本行 application ID，并同时登记实际构建签名的 SHA-1。

```powershell
cd starx_car_passenger
fvm flutter pub get
fvm flutter analyze
fvm flutter test
fvm flutter run
# 或生成 debug APK
fvm flutter build apk --debug
# 连接 Android 真机后运行启动级集成测试
fvm flutter test integration_test/app_start_test.dart
```

本次验证：`fvm flutter analyze`、`fvm flutter test`、`fvm flutter build apk --debug` 均通过；启动级 integration test 和高德地图 Widget 挂载测试已在 `Pixel 9 Pro (Android 16/API 36)` 安装运行并通过。清单已注入非空高德 Key，但模拟器日志报告 `INVALID_USER_SCODE`，因此地图 Key 的控制台包名/SHA-1 配置仍未验收通过。

若出现“older Android embedding”并提示缺少 `android/AndroidManifest.xml`，说明使用了系统全局 Flutter。请在本模块目录执行 `fvm flutter ...`；本工程已使用 Android embedding v2，不能通过新增根级 Manifest 修复该提示。

地图挂载验收命令：`fvm flutter test integration_test/amap_map_test.dart -d <android-device>`。一次性定位验收命令：`fvm flutter test integration_test/amap_location_test.dart -d <android-device>`。两项测试均应在已配置 `android/amap.properties`、授予定位权限且可访问高德服务的 Android 环境执行。

登录错误回归：`fvm flutter test --dart-define=API_BASE_URL=http://127.0.0.1:1/api integration_test/auth_error_test.dart -d <android-device>`。

## 尚未实现

- 地点名称自动补全和搜索结果分页。
- 更细粒度的后台恢复提示和真机定位权限引导。
- 支付、评价、推送通知和 WebSocket。
