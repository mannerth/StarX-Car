# StarX-Car 司机端

`starx_car_driver` 是 StarX-Car 的司机 Android App，依赖本地 `starx_car_common`。当前已完成司机账号、个人资料、车辆、听单工作台和进行中订单状态流转。

## 当前状态

司机登录/注册、加密会话恢复、基础资料、司机资料、密码与车辆绑定功能均已对接后端；司机工作台已支持上线/下线、位置上报、待抢订单轮询、抢单、确认到达、开始服务和完成结算。抢单需二次确认，成功后会刷新已接订单并直接打开订单详情。进行中订单从工作台统一进入详情页查看地图、上车点/目的地、跳转高德外部导航，并在确认后推进到达、开始和完成状态；订单地图会实时标注司机当前车辆位置：接单后（`ACCEPTED`）绘制“司机当前位置 → 上车点”驾车路线，服务中（`IN_SERVICE`）绘制“司机当前位置 → 目的地”驾车路线，司机位置每 2 秒更新并在移动后自动重规划剩余路线；到达上车点后必须先输入乘客手机尾号（后四位），与订单核对一致并通过服务端校验后才能开始服务。位置上报、待抢订单和已接订单查询会随任务生命周期取消在途请求，离线时不会请求待抢订单。项目使用 Riverpod 管理会话；`flutter analyze`、`flutter test`、Android debug APK 构建均已通过。认证 Controller 测试覆盖角色会话恢复、匹配登录保存和错误角色拒绝；Repository 契约测试覆盖当前车辆解析、GCJ-02 位置上报、WGS-84 拒绝和状态动作/完成账单。

## 已实现功能

### 认证与会话

- 登录：`POST /api/auth/login`，只接受 `driver` 角色。
- 登录提交期间保留表单内容；账号、密码或网络错误会在当前页面显示提示，不会重建页面或静默失败。
- 注册：`POST /api/auth/drivers/register`，收集用户名、密码、邮箱、真实姓名、性别、手机号和驾驶证号；成功后直接登录。
- 启动恢复会话，退出时清除安全存储；资料、司机资料和车辆 Provider 随会话切换自动重新加载；请求 Bearer Token、401 刷新、重放和错误处理均复用公共包。

### 个人中心

- 查看并修改用户名、真实姓名、头像、邮箱、性别、脱敏手机号/手机号、驾驶证号、身份证号和驾驶证有效期。
- 修改头像时从系统相册选择图片并随 `PUT /api/users/me` Multipart 表单提交，由后端上传并保存 URL；基础资料和司机资料仍分别使用对应接口更新。
- 性别通过“男/女”选项选择；驾驶证有效期使用日期选择器；车辆出厂年份使用年份下拉选择，避免手输枚举值或日期格式。
- 修改密码：`POST /api/users/me/password`。
- 注销账号：个人中心二次确认后调用 `DELETE /api/drivers/me`；服务端保留司机资料、车辆绑定和历史订单并标记账号 `cancelled`，存在进行中订单时会返回 409，成功后清理本地会话。
- 所有编辑弹窗为 Stateful 组件，自身持有并在卸载时释放 `TextEditingController`；保存/取消时输入框仍有焦点不会卡死。
- 个人中心提供「历史订单」入口：按全部/已完成/已取消筛选分页查看本人历史订单，滚动自动加载更多，点击进入订单详情查看路线、费用与结算金额。

### 当前绑定车辆

- 车辆页加载司机全部有效绑定车辆，使用 `driver.carId` 标记当前使用车辆；一名司机可保留多辆绑定车辆。
- 对非当前车辆提供“切换”操作，调用 `PUT /api/drivers/me/current-car`；切换只改变当前指针，不改变绑定历史。
- 未绑定时填写行驶证号、拆分车牌、登记归属地、核定载客数、出厂年份、车型和品牌，调用 `POST /api/cars` 创建并自动设为当前车辆；车牌省份简称、车牌字母及颜色使用固定下拉选项，避免提交不规范值。
- 当前车辆支持调用 `PUT /api/cars/{carId}` 修改；其他绑定车辆先切换为当前车辆后再编辑。
- 尚未提供解绑/停用车辆入口；后端相关接口保留给后续确认后的车辆管理流程。

### 我的评价

- 个人中心新增「我的评价」入口，通过 `GET /api/drivers/me/ratings` 分页查看乘客对本人的评价（订单号、星级、文字、时间），列表顶部显示评价总数，支持下拉刷新。

## 工程结构

```text
lib/
  main.dart                         ProviderScope 启动入口
  app.dart                          MaterialApp、主题、登录态分流
  core/providers.dart               API 地址、SessionStore、ApiClient Provider
  features/
    auth/                           Riverpod 认证控制器与登录/注册表单
    home/
        driver_repository.dart        用户、司机资料与车辆 API
        driver_home_page.dart         工作台、资料页、车辆页、编辑弹窗
      work/
        driver_work_page.dart         上下线、抢单和进行中订单
        driver_order_detail_page.dart 订单地图、导航和状态操作
```

依赖方向固定为 `starx_car_driver -> starx_car_common`。司机工作流、车辆与位置上报策略不会放入公共包。

## 后端接口

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 司机登录 |
| `POST` | `/api/auth/drivers/register` | 司机注册并返回令牌 |
| `GET` / `PUT` | `/api/users/me` | 当前基础资料 |
| `POST` | `/api/users/me/password` | 修改密码 |
| `GET` / `PUT` | `/api/drivers/me` | 司机手机号、驾驶证资料 |
| `DELETE` | `/api/drivers/me` | 注销司机账号并保留历史记录 |
| `GET` | `/api/drivers/me/cars` | 获取全部有效绑定车辆 |
| `PUT` | `/api/drivers/me/current-car` | 从本人绑定车辆中切换当前车辆 |
| `PUT` | `/api/drivers/me/work-status` | 上线或下线 |
| `PUT` | `/api/drivers/me/location` | 上报最新 GCJ-02 位置 |
| `GET` | `/api/drivers/me/orders/pending` | 查询待抢订单 |
| `GET` | `/api/drivers/me/orders` | 查询已接订单 |
| `GET` | `/api/orders/{orderNo}` | 查询司机自己的订单详情 |
| `POST` | `/api/orders/{orderNo}/accept` | 抢单 |
| `POST` | `/api/orders/{orderNo}/arrive` | 确认到达 |
| `POST` | `/api/orders/{orderNo}/start-service` | 提交乘客尾号后开始服务（`{"phoneTail":"1234"}`） |
| `POST` | `/api/orders/{orderNo}/complete` | 完成并结算 |

## Android 配置、运行与验证

- application ID：`org.mannerth.starxcar.driver`。
- 跨角色接口字段、状态机、错误码和坐标约定见仓库根目录的 [`docs/client-api-contract.md`](../docs/client-api-contract.md)。
- 默认后端地址：`http://10.0.2.2:8080/api`；可通过 `--dart-define=API_BASE_URL=...` 覆盖。
- 高德 Android Key 配置在本项目 `android/amap.properties` 的 `AMAP_ANDROID_KEY`；该文件已忽略且不能提交。`--dart-define` 只用于 API 地址，不能替代原生清单 Key。
- 若日志出现 `SERVICE_NOT_EXIST`，说明 Key 已被识别但高德控制台未开通 Android 定位 SDK 服务；请在该 Key 的“服务平台/服务权限”中启用定位，并核对包名 `org.mannerth.starxcar.driver` 与实际 debug/release SHA-1。`hiddenapi` 反射提示是高德 SDK 在 Android 新版本上的兼容性警告，不是业务失败原因。
- 本地开发允许 HTTP，生产必须改用 HTTPS。
- 因工程位于 D 盘、Flutter Pub 缓存位于 C 盘，已关闭 Kotlin 增量编译并使用进程内编译，避免 `image_picker_android` 的跨盘缓存错误；首次 Android 构建会稍慢。
- 未配置 `android/key.properties` 时仍可构建 debug APK；配置本机 release 签名后 release 构建会自动使用它。
- 签名文件和 `key.properties` 均不可提交、删除或分享。
- release 签名路径从 Android 项目根目录解析，当前本机配置可使用仓库根目录 `android-signing/starx-car-release.jks` 构建；APK 证书 SHA-1 为 `4C:83:43:EE:4B:E8:5E:0B:89:7A:29:E9:83:D8:17:C1:9A:0C:BF:73`，须在高德控制台登记到司机端 Key。
- 司机端 `debug` 构建已复用 `android-signing/starx-car-release.jks` 发布签名（见 `android/app/build.gradle.kts`），debug 与 release 证书 SHA-1 统一为 `4C:83:43:EE:4B:E8:5E:0B:89:7A:29:E9:83:D8:17:C1:9A:0C:BF:73`，高德控制台司机端 Key 只需登记这一份签名；`profile` 变体仍使用默认 debug.keystore（SHA-1 `1C:50:46:29:AE:A7:92:22:36:9C:CB:83:B7:E7:53:D1:1B:AE:D8:28`）。若出现高德 `INVALID_USER_SCODE`，先确认司机端 Key 的 Android 包名为本行 application ID，并同时登记实际构建签名的 SHA-1。
- 高德定位 SDK 使用仍支持 x86_64 模拟器的 6.5.1；主清单统一声明地图/定位所需网络、Wi-Fi、A-GPS 权限并注册 `APSService`，debug 与 release 配置一致。

```powershell
cd starx_car_driver
fvm flutter pub get
fvm flutter analyze
fvm flutter test
fvm flutter run
fvm flutter build apk --debug
# 连接 Android 真机后运行启动级集成测试
 fvm flutter test integration_test/app_start_test.dart
 fvm flutter test integration_test/amap_map_test.dart -d <android-device> # 需要已配置高德 Key
 fvm flutter test integration_test/amap_location_test.dart -d <android-device> # 需要前台精确定位权限
 fvm flutter test integration_test/foreground_service_test.dart -d <android-device> # 需要授予定位和通知权限
 fvm flutter test --dart-define=API_BASE_URL=http://127.0.0.1:1/api integration_test/auth_error_test.dart -d <android-device>
```

本次验证：`fvm flutter analyze`、`fvm flutter test`、`fvm flutter build apk --debug` 和高德地图挂载测试均通过。已在 `Pixel 9 Pro (Android 16/API 36)` 撤销全部运行时权限后单次调用前台服务启动：系统连续展示定位和通知授权页，同一个调用在整组授权后自动启动并停止服务，无需再次点击开关；一次性高德定位返回 `hasPoint=true, errorCode=0`，司机 Key 未再报告 `INVALID_USER_SCODE`、海外权限或 `SERVICE_NOT_EXIST`。

若出现“older Android embedding”并提示缺少 `android/AndroidManifest.xml`，说明使用了系统全局 Flutter。请在本模块目录执行 `fvm flutter ...`；本工程已使用 Android embedding v2，不能通过新增根级 Manifest 修复该提示。

## Android 前台服务

- 司机上线时会启动 `DriverForegroundService`，显示“正在听单并上报位置”的常驻通知；下线、登出或服务销毁时停止。
- 首次上线会在同一次原生权限请求中提交粗略/精确定位与 Android 13+ 通知权限；用户完成整组授权后自动继续启动服务和上线，无需为每项缺失权限反复拨动开关。
- Android 13+ 需要允许通知，Android 14+ 需要允许前台定位服务和精确定位权限；拒绝任一权限时不会把服务端工作状态切为在线。司机端只申请前台定位，不申请后台定位。
- 断网退避和长时间后台运行仍需在 Android 真机上验证。

## 尚未实现

- 订单历史分页、收入、支付。

