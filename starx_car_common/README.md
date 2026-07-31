# StarX-Car Flutter 公共包

`starx_car_common` 是 StarX-Car 乘客端与司机端共用的本地 Flutter package，不发布至 pub.dev。它提供跨角色基础设施与基础模型，绝不包含角色页面、路由、状态管理或订单业务。

当前使用 FVM Flutter `3.44.8`、Dart `3.12.2`；两个 App 以 path 依赖引用本包。

## 当前状态

公共认证与网络基础设施已经可供两个 Android App 使用；地图部分目前只有坐标和地点模型，尚未接入高德 SDK。包级静态检查和单元测试均已通过。

## 已实现能力

### 会话与基础用户模型

- `AuthSession`：保存 access token、refresh token、当前用户 ID 与角色；支持 JSON 序列化，供安全存储恢复会话。
- `SessionStore`：用 `flutter_secure_storage` 加密保存、读取与清除会话。两个 App 启动时均通过它恢复登录态，退出时清除本地令牌。
- `UserProfile`：角色无关的用户基础资料模型，包含 ID、用户名、邮箱、性别与角色。

### HTTP 与令牌刷新

- `ApiClient` 基于 Dio，统一使用后端 `/api` 响应信封，成功时返回 `data` 字段。
- 每个请求自动读取安全会话并附加 `Authorization: Bearer <accessToken>`。
- 收到业务请求的 `401` 时，使用 refresh token 请求 `/auth/refresh`；同一时刻的多个 `401` 合并为一次刷新请求，刷新成功后自动重放原请求。
- 刷新失败或不存在会话时清除本地会话；调用方可据此返回登录页。
- `ApiException` 只保留可展示的服务端消息、HTTP 状态和 trace ID，不暴露底层异常详情。

### 通用模型

- `ApiResponse<T>`：对应后端 `{ code, message, traceId, data }` 信封。
- `GeoPoint`、`CoordinateSystem`、`Place`：为后续地图与地点选择提供基础类型。
- 高德业务坐标统一按 `GCJ-02` 使用；设备 GPS 的 `WGS-84` 必须在地图基础层明确转换或标注，业务端不得混用。

## 职责边界

```text
starx_car_passenger ─┐
                     ├──> starx_car_common
starx_car_driver ────┘
```

可放入：网络、会话、令牌刷新、安全存储、通用错误、地图底层能力与跨角色模型。

不得放入：乘客/司机页面与 Riverpod 状态、完整角色 Repository、叫车/接单/支付/评价/收入流程、订单状态机、派单、定价、数据库访问，以及任何高德 Key 或签名资料。

## 依赖与 Android 兼容性

- `dio`：HTTP 传输。
- `flutter_secure_storage 9.2.4`：Android Keystore 安全存储。
- `path_provider_android 2.2.22`：在包与两个 App 中固定该版本，避免新版本引入的 `jni` Android Gradle 插件与当前 Flutter 3.44 Gradle 模板冲突。

## 使用与验证

```yaml
dependencies:
  starx_car_common:
    path: ../starx_car_common
```

```powershell
cd starx_car_common
fvm flutter pub get
fvm flutter analyze
fvm flutter test
```

## 尚未实现

- 高德 Android 地图、定位权限、POI、逆地理编码、路线规划与外部导航。
- WebSocket 的连接、重连、心跳和通用事件信封。
- 分页及更多跨角色展示模型。
