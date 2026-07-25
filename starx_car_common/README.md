# StarX-Car Flutter 公共包

`starx_car_common` 是 StarX-Car 数据库课程设计中乘客 Android App 与司机 Android App 共用的本地 Flutter package。它不发布到 pub.dev，也不包含任一角色的页面或订单流程。

两个 App 均使用 FVM Flutter `3.44.8`；本包的 Flutter SDK 约束与其保持一致。

## 当前能力

- 定义与后端统一响应结构对应的 `ApiResponse<T>`。
- 定义跨端登录会话模型 `AuthSession`，用于后续统一 Token 存储与刷新逻辑。
- 定义地图基础模型：`GeoPoint`、`CoordinateSystem`、`Place`。
- 明确高德地图业务坐标使用 `GCJ-02`，避免与设备 GPS 的 `WGS-84` 坐标混用造成地图点位偏移。
- 提供包级测试，校验公共响应和地图坐标模型的基础行为。

## 有意不包含的内容

- 不引入高德 Flutter SDK、地图 Key 或 Android Manifest 配置；在确认具体 SDK/插件并完成 Android PoC 后再集中接入。
- 不实现乘客叫车、司机接单、订单状态机、实时位置上报等角色业务。
- 不引入复杂依赖注入、状态管理、代码生成或多 package 工作区工具。

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

## 后续扩展边界

后续公共实现应仅限于以下可复用能力：HTTP 客户端和 JWT 刷新、Token 安全存储、高德地图展示/定位/POI/路线，以及角色无关的数据模型。乘客和司机的页面、状态管理及订单流程保留在各自 App 中。

## 验证命令

```bash
cd starx_car_common
fvm flutter analyze
fvm flutter test
```
