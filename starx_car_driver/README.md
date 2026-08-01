# StarX-Car 司机端

`starx_car_driver` 是 StarX-Car 的司机 Android App，依赖本地 `starx_car_common`。当前已完成司机账号、个人资料和当前绑定车辆管理；听单、订单和行程仍未实现。

## 当前状态

司机登录/注册、加密会话恢复、基础资料、司机资料、密码与车辆创建绑定功能均已对接后端。项目使用 Riverpod 管理会话；`flutter analyze`、`flutter test`、Android debug APK 构建均已通过。

## 已实现功能

### 认证与会话

- 登录：`POST /api/auth/login`，只接受 `driver` 角色。
- 注册：`POST /api/auth/drivers/register`，收集用户名、密码、邮箱、真实姓名、性别、手机号和驾驶证号；成功后直接登录。
- 启动恢复会话，退出时清除安全存储；请求 Bearer Token、401 刷新、重放和错误处理均复用公共包。

### 个人中心

- 查看并修改用户名、真实姓名、头像、邮箱、性别、脱敏手机号/手机号、驾驶证号、身份证号和驾驶证有效期。
- 修改头像时从系统相册选择图片并随 `PUT /api/users/me` Multipart 表单提交，由后端上传并保存 URL；基础资料和司机资料仍分别使用对应接口更新。
- 性别通过“男/女”选项选择；驾驶证有效期使用日期选择器；车辆出厂年份使用年份下拉选择，避免手输枚举值或日期格式。
- 修改密码：`POST /api/users/me/password`。
- 所有编辑弹窗为 Stateful 组件，自身持有并在卸载时释放 `TextEditingController`；保存/取消时输入框仍有焦点不会卡死。

### 当前绑定车辆

- 未绑定时填写行驶证号、拆分车牌、登记归属地、核定载客数、出厂年份、车型、品牌和颜色，调用 `POST /api/cars` 创建并自动绑定。
- 已绑定时按拆分车牌展示完整车牌号，并调用 `PUT /api/cars/{carId}` 修改车辆。
- 未实现解绑、删除车辆与绑定既有车辆的独立页面，尽管后端已有相关接口。

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
| `GET` | `/api/cars/me` | 获取当前绑定车辆；未绑定为 404 |
| `POST` | `/api/cars` | 创建并自动绑定车辆 |
| `PUT` | `/api/cars/{carId}` | 修改当前绑定车辆 |

## Android 配置、运行与验证

- application ID：`org.mannerth.starxcar.driver`。
- 默认后端地址：`http://10.0.2.2:8080/api`；可通过 `--dart-define=API_BASE_URL=...` 覆盖。
- 本地开发允许 HTTP，生产必须改用 HTTPS。
- 未配置 `android/key.properties` 时仍可构建 debug APK；配置本机 release 签名后 release 构建会自动使用它。
- 签名文件和 `key.properties` 均不可提交、删除或分享。

```powershell
cd starx_car_driver
fvm flutter pub get
fvm flutter analyze
fvm flutter test
fvm flutter run
fvm flutter build apk --debug
```

## 尚未实现

- 出车/收车、听单、待接订单列表、接单/拒单。
- 到达、开始服务、结束服务等行程状态。
- 地图、接驾/送驾导航、按订单状态的位置上报与断网重试。
- 订单历史、收入、评价。
