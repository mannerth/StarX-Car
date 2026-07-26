# starx_car_passenger

StarX-Car 的乘客 Android 应用。当前工程使用仓库内的 `starx_car_common` 本地公共包，并为课程项目配置了独立 Android 包名和共享 release 签名。

## 职责边界

本项目只实现乘客角色的 Android 页面、状态和业务流程，依赖 `starx_car_common` 提供的网络、认证和高德地图基础能力。依赖方向为 `starx_car_passenger -> starx_car_common`；本项目不得反向修改或让公共模块依赖乘客业务。

### 本项目负责

- 乘客登录、注册、登录态页面跳转和个人资料维护。
- 上车点与目的地的搜索、选择和确认；调用公共地图能力展示地点、路线和司机标记。
- 发起即时/预约叫车、取消叫车、等待派单和查看订单状态。
- 订单接单后查看司机信息与实时位置、行程展示、支付和评价。
- 乘客订单列表、订单详情及其状态管理。

### 本项目不负责

- HTTP 鉴权机制、Token 刷新、通用响应解析和安全存储的重复实现。
- 高德 SDK 初始化、定位权限、POI/逆地理编码、路线规划和外部导航的底层实现。
- 司机听单、接单、车辆、收入、司机位置上报策略和后端派单逻辑。
- 数据库操作、订单定价、派单和权限判定等后端业务规则。

乘客端仅使用 common 暴露的 `GeoPoint`、`Place` 等地图模型；高德业务坐标统一按 `GCJ-02` 处理，不自行混用 GPS `WGS-84` 坐标。

## Android 应用标识与签名

- Android package / application ID：`org.mannerth.starxcar.passenger`
- release signing：与司机端共用本机 `android-signing/starx-car-release.jks` 签名证书。
- `android/key.properties` 与签名证书均已被 Git 忽略，不能提交、删除或分享。
- Android debug 与 release 签名的 SHA-1 不同；申请高德 Android Key 时应使用 release 构建证书的 SHA-1 和上述包名。
