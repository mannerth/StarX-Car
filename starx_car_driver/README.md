# starx_car_driver

StarX-Car 的司机 Android 应用。当前工程使用仓库内的 `starx_car_common` 本地公共包，并为课程项目配置了独立 Android 包名和共享 release 签名。

## 职责边界

本项目只实现司机角色的 Android 页面、状态和业务流程，依赖 `starx_car_common` 提供的网络、认证和高德地图基础能力。依赖方向为 `starx_car_driver -> starx_car_common`；本项目不得反向修改或让公共模块依赖司机业务。

### 本项目负责

- 司机登录、注册、登录态页面跳转、个人资料、驾驶证和车辆维护。
- 出车/收车、听单、订单列表、接单、拒单、到达乘客点、开始服务和结束服务。
- 根据当前订单展示乘客上车点、目的地和路线，并调用公共能力跳转高德地图导航。
- 根据司机出车和订单状态决定是否、以何种频率上报当前位置；处理断网重试和上报结果。
- 司机订单详情、行程状态、收入和评价相关页面及状态管理。

### 本项目不负责

- HTTP 鉴权机制、Token 刷新、通用响应解析和安全存储的重复实现。
- 高德 SDK 初始化、定位权限、POI/逆地理编码、路线规划和外部导航的底层实现。
- 乘客选点、叫车、支付、乘客订单历史和乘客查看司机位置的业务页面。
- 后端订单状态机、派单、定价、收入计算、权限判定和任何数据库操作。

司机端使用 common 提供的定位能力，但“何时上报、上报哪个订单、间隔多久”是司机业务规则，必须保留在本项目。高德业务坐标统一按 `GCJ-02` 处理，不自行混用 GPS `WGS-84` 坐标。

## Android 应用标识与签名

- Android package / application ID：`org.mannerth.starxcar.driver`
- release signing：与乘客端共用本机 `android-signing/starx-car-release.jks` 签名证书。
- `android/key.properties` 与签名证书均已被 Git 忽略，不能提交、删除或分享。
- Android debug 与 release 签名的 SHA-1 不同；申请高德 Android Key 时应使用 release 构建证书的 SHA-1 和上述包名。
