# StarX-Car 乘客端

`starx_car_passenger` 是 StarX-Car 的乘客 Android App，依赖本地 `starx_car_common` 提供会话、网络和基础模型。本阶段已完成账号与个人中心，不包含叫车与订单业务。

## 当前状态

登录、乘客注册、会话恢复、退出、个人资料编辑与修改密码均已对接现有后端。项目采用 Riverpod 管理登录态；`flutter analyze`、`flutter test` 和 Android debug APK 构建均已通过。

## 已实现功能

### 认证与会话

- 登录：`POST /api/auth/login`，仅接受后端返回 `passenger` 角色的账号。
- 注册：`POST /api/auth/passengers/register`，收集用户名、密码、邮箱、真实姓名、性别、11 位手机号；注册成功后直接保存令牌并进入个人中心。
- 会话恢复：启动时从公共包的加密存储读取会话，只有乘客角色会进入已登录界面。
- 退出：清除加密存储中的 access token 与 refresh token，返回认证页。
- 请求鉴权和 401 刷新全部由公共 `ApiClient` 完成，乘客业务代码不重复管理 Token。

### 个人中心

- 查看当前用户的用户名、真实姓名、邮箱、性别和后端脱敏手机号。
- 调用 `PUT /api/users/me` 的 Multipart 资料表单修改用户名、真实姓名、邮箱和性别；从系统相册选择头像文件后与资料一次提交，由后端上传并保存头像 URL。
- 个人中心优先展示后端返回的头像 URL；未上传头像时使用用户名或真实姓名首字作为默认头像。
- 调用 `PUT /api/passengers/me` 修改手机号；手机号更新后由后端触发器维护脱敏展示值。
- 调用 `POST /api/users/me/password`，验证原密码后修改密码。
- 资料编辑与密码弹窗会等待退出动画结束后再释放输入控制器，焦点停留在输入框时保存或取消不会使用已释放的 controller。
- 展示后端安全消息；网络、认证与服务端错误不会暴露底层异常。

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
| `GET` / `PUT` | `/api/passengers/me` | 获取、修改乘客手机号 |

## Android 配置、运行与验证

- application ID：`org.mannerth.starxcar.passenger`。
- 默认 API 地址：`http://10.0.2.2:8080/api`，适用于 Android 模拟器访问本机后端；也可用 `--dart-define=API_BASE_URL=http://<host>:<port>/api` 覆盖。
- 为本地 HTTP 开发已允许 Android 明文流量；部署环境必须使用 HTTPS。
- `android/key.properties` 与 release keystore 均为本机敏感文件，已忽略，不能提交或分享。

```powershell
cd starx_car_passenger
fvm flutter pub get
fvm flutter analyze
fvm flutter test
fvm flutter run
# 或生成 debug APK
fvm flutter build apk --debug
```

## 尚未实现

- 上车点/目的地选择、地图、POI 与路线展示。
- 即时或预约叫车、取消、等待接单、司机实时位置。
- 乘客订单列表和详情、支付、评价。
