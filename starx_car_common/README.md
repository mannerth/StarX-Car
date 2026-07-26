# StarX-Car Flutter 公共包

`starx_car_common` 是 StarX-Car 数据库课程设计中乘客 Android App 与司机 Android App 共用的本地 Flutter package。它不发布到 pub.dev，也不包含任一角色的页面或订单流程。

两个 App 均使用 FVM Flutter `3.44.8`；本包的 Flutter SDK 约束与其保持一致。

## 当前能力

- 定义与后端统一响应结构对应的 `ApiResponse<T>`。
- 定义跨端登录会话模型 `AuthSession`，用于后续统一 Token 存储与刷新逻辑。
- 定义地图基础模型：`GeoPoint`、`CoordinateSystem`、`Place`。
- 明确高德地图业务坐标使用 `GCJ-02`，避免与设备 GPS 的 `WGS-84` 坐标混用造成地图点位偏移。
- 提供包级测试，校验公共响应和地图坐标模型的基础行为。

## 职责边界

本包是乘客端、司机端共同依赖的“基础设施与跨角色能力层”，不是通用业务层。依赖方向只能是：

```text
starx_car_passenger ─┐
                     ├──> starx_car_common
starx_car_driver ────┘
```

本包不得导入或依赖任一 App。

### 应放入本包的能力

- 网络基础设施：HTTP 客户端、后端统一响应解析、Bearer Token 附加、401 刷新令牌、通用网络异常。
- 认证基础能力：会话、Token 安全存储、登录态恢复、登出清理、当前用户基础资料。
- 高德 Android 地图能力：地图展示、定位权限、当前位置、POI 搜索、地理/逆地理编码、路线规划和跳转高德地图导航。
- 与角色无关的模型：坐标、地点、分页、用户基础信息和统一响应。

### 坐标规范

高德地图业务数据统一使用 `GCJ-02`。设备原始 GPS 位置可能是 `WGS-84`，必须在地图基础能力中明确转换或标注；业务端只使用 `GeoPoint`，不得自行混用坐标系，以免出现地图点位偏移。

### 不得放入本包的内容

- 乘客或司机的页面、路由、状态管理和完整业务 Repository。
- 叫车、取消订单、接单、拒单、到达、开始/结束服务、支付、评价、收入等角色业务流程。
- 订单状态机、派单、定价、司机调度、后端数据库操作和管理员业务。
- 高德 Key、Android Manifest 中的敏感配置、JKS/Keystore 及签名密码。
- 为本次 Android 课程设计无实际需要的多地图供应商适配、复杂依赖注入或多 package 工作区工具。

### 实时位置与订单模型

第一阶段可使用 HTTP 定时上报与轮询。若后续采用 WebSocket，本包只负责连接、鉴权、重连、心跳和通用事件信封；司机何时上报位置、乘客订阅哪个订单/司机，仍属于各自 App。订单仅在字段确实跨角色一致时才放入本包；默认将乘客订单详情和司机订单详情保留在各自业务端。

## 在两个 App 中使用

在乘客端或司机端的 `pubspec.yaml` 增加本地依赖：

```yaml
dependencies:
  starx_car_common:
    path: ../starx_car_common
```

随后在对应 App 目录运行：

```bash
fvm flutter pub get
```

引用公共模型：

```dart
import 'package:starx_car_common/starx_car_common.dart';

const pickup = GeoPoint(latitude: 31.2304, longitude: 121.4737);
```

## 后续建设顺序

1. 完善公共 HTTP、JWT 刷新和 Token 安全存储。
2. 完成高德 Android 地图、定位、POI、路线和外部导航的最小 PoC。
3. 由两个 App 分别实现乘客叫车和司机接单业务；不要将角色流程回流到本包。

## 验证命令

```bash
cd starx_car_common
fvm flutter analyze
fvm flutter test
```
