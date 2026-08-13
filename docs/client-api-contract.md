# StarX-Car 客户端接口契约

本文档面向 `starx_car_passenger` 和 `starx_car_driver` Android 客户端，依据当前
后端 Controller、请求/响应 DTO 和安全配置整理。路径包含 `/api` 前缀；客户端的
`API_BASE_URL` 应以 `/api` 结尾，例如 `http://10.0.2.2:8080/api`。

### Android 模拟器本地联调

- 标准 Android Emulator 访问宿主机使用 `10.0.2.2`；真机应改用开发机局域网 IP。
- API 地址通过编译期参数注入：
  `fvm flutter run --release --dart-define=API_BASE_URL=http://10.0.2.2:8080/api`。
- 两个客户端的 Android Manifest 已允许开发环境 HTTP 明文流量；生产环境必须使用 HTTPS。
- 若出现“无法连接服务器”，先确认后端监听 `8080`，并使用 FVM 在对应客户端目录重新构建，避免运行旧 APK。后端收到请求但认证失败时，客户端应显示服务端返回的业务错误，而不是连接错误。

## 1. 通用约定

### 1.1 请求与响应

- JSON 请求使用 `Content-Type: application/json`。
- 受保护接口发送 `Authorization: Bearer <accessToken>`。
- 成功响应由后端统一包装：

```json
{
  "code": 0,
  "message": "success",
  "traceId": "trace-id",
  "data": {}
}
```

- 创建订单的 HTTP 状态为 `201`，其余普通成功响应通常为 `200`；客户端应以
  HTTP 状态和 `code` 同时判断成功。
- 错误也使用同一信封，`code` 通常等于 HTTP 状态码：

```json
{
  "code": 409,
  "message": "当前存在未结束订单",
  "traceId": "trace-id",
  "data": null
}
```

- `traceId` 只用于日志和问题反馈，不向日志输出令牌、手机号、身份证或完整坐标轨迹。
- 时间均为 `Asia/Shanghai`，格式为 `yyyy-MM-dd HH:mm:ss`，不带时区后缀。
- JSON 字段使用 camelCase；金额使用十进制字符串或 JSON number，客户端解析为
  `Decimal`/字符串后再展示，不能使用二进制浮点进行金额计算。
- 后端可能增加枚举值。客户端遇到未知状态必须保留原始值并显示通用状态，不得崩溃。

### 1.2 坐标

地点和位置字段全部是高德 `GCJ-02`：

```json
{
  "poiId": "B000A83M61",
  "name": "天安门",
  "address": "北京市东城区东长安街",
  "latitude": 39.916527,
  "longitude": 116.397128
}
```

`poiId` 和 `address` 可以为空；`name`、`latitude`、`longitude` 必填。纬度范围为
`[-90, 90]`，经度范围为 `[-180, 180]`，坐标最多 6 位小数。设备原始定位不得绕过
公共地图层直接写入业务请求。

### 1.3 认证角色

`user.type` 的值为 `passenger`、`driver` 或 `admin`。乘客 App 只接受 `passenger`，
司机 App 只接受 `driver`；角色不匹配时清理刚保存的会话并回到登录页。

## 2. 认证与用户资料

### POST `/auth/login`

请求：`{"username":"...","password":"..."}`。

成功 `data` 为：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "expiresIn": 7200,
  "user": {
    "id": 1,
    "username": "rider",
    "email": "rider@example.com",
    "gender": "male",
    "type": "passenger",
    "realName": "张三",
    "avatarUrl": null,
    "status": "active",
    "createTime": "2026-08-05 10:00:00",
    "lastLoginTime": "2026-08-05 10:00:00",
    "updateTime": "2026-08-05 10:00:00"
  }
}
```

失败：`400` 参数无效，`401` 用户名/密码错误或账号不可用。

### POST `/auth/refresh`

请求：`{"refreshToken":"..."}`。不附加 access token。成功返回与登录相同的
`TokenResponse`，并轮换两种令牌；`400/401` 时清理本地会话并要求重新登录。

### POST `/auth/passengers/register`

请求字段：`username`（3-50）、`password`（6-72）、`email?`、`gender`（`male|female`）、
`phoneNumber`（`1` 开头的 11 位手机号）、`realName?`、`avatarUrl?`。成功直接返回令牌。
失败：`400` 参数无效，`409` 用户名或邮箱冲突。

### POST `/auth/drivers/register`

在乘客注册字段基础上增加必填 `licenseNumber`（最多 30 字符）。成功直接返回令牌；
失败：`400` 参数无效，`409` 用户名或邮箱冲突。

### GET `/users/me`

返回当前用户的 `UserResponse`（不含密码）。失败：`401` 令牌无效，`404` 用户不存在。

### PUT `/users/me`（JSON）

请求字段：必填 `username`、`gender`；可选 `email`、`realName`、`avatarUrl`。成功返回
更新后的 `UserResponse`。失败：`400` 格式错误，`401` 未登录，`404` 用户不存在，`409`
用户名或邮箱冲突。

### PUT `/users/me`（multipart/form-data）

字段与 JSON 版本相同，可附加 `avatar` 图片文件。后端上传图片并保存返回 URL；图床
异常返回 `502`，图床未配置返回 `503`。客户端图片字段名固定为 `avatar`。

### POST `/users/me/password`

请求：`{"currentPassword":"...","newPassword":"..."}`，新密码长度 6-72。成功
响应 `data` 为 `null`；原密码错误返回 `401`。

### GET/PUT `/passengers/me`

仅 `passenger`。GET 返回 `id`、`phoneNumber`、`phoneTail`、`displayPhone`；PUT 请求
`{"phoneNumber":"13800138000"}`，手机号更新后由数据库触发器维护脱敏字段。
失败：`400/401/403/404`。

### DELETE `/passengers/me`

仅 `passenger`。注销会将 `user.status` 标记为 `cancelled`，保留乘客资料及历史订单
关联；账号不可再登录或刷新令牌，注销不可恢复。当前接口不做个人资料脱敏，也不允许
使用同一用户名/邮箱重新注册，这两项属于后续产品策略。

## 3. 乘客即时订单

### POST `/orders/quote`

仅 `passenger`。请求为 `{"pickup": <Place>, "destination": <Place>}`。成功返回：

```json
{
  "pricingRuleId": 1,
  "pricingRuleVersion": 1,
  "estimatedDistanceM": 12001,
  "estimatedDurationS": 1801,
  "fare": {
    "baseFare": 5.00,
    "distanceFare": 30.00,
    "drivingTimeFare": 6.50,
    "waitingFare": 0.00,
    "totalFare": 41.50
  }
}
```

报价由服务端路线和当前规则计算，仅用于展示；路线/规则不可用返回 `503`。

### POST `/orders`

仅 `passenger`。请求与报价相同，客户端不得提交或依赖客户端计算的金额。服务端会
重新规划、计价并创建 `PENDING` 订单，HTTP `201` 返回 `orderNo`、`status` 和 `quote`。
失败：`400/401/403/404/409/503`；`409` 表示当前乘客存在未结束订单。

### GET `/passengers/me/orders?page=&size=&status=`

仅 `passenger`，按创建时间倒序。`page` 从 1 开始，`size` 为 1-100（默认 20），
`status` 可省略或使用订单状态枚举。返回：

```json
{"page":1,"size":20,"total":1,"records":[
  {"orderNo":"O...","status":"PENDING","pickupName":"...",
   "destinationName":"...","totalFare":41.50,
   "createdTime":"2026-08-05 10:00:00","estimatedFare":true}
]}
```

### GET `/orders/{orderNo}`

乘客只能读取自己的订单，司机只能读取自己已接订单；无权限时统一按 `404` 处理以
避免泄露订单存在性。详情字段包括地点坐标、`estimatedDistanceM`、`estimatedDurationS`、
`billingDistanceM`、`actualDurationS?`、`waitingDurationS?`、`fare`、`estimatedFare`、
`passengerPhoneTail`（乘客手机尾号后四位，司机开始服务前用于身份核对）、司机脱敏资料、
各状态时间、取消信息和事件列表。

### POST `/orders/{orderNo}/cancel`

乘客只能在 `PENDING` 或 `ACCEPTED` 取消；请求体可省略或为
`{"reason":"行程计划变更"}`（最多 255 字符）。`DRIVER_ARRIVED`、`IN_SERVICE` 和
终态返回 `409`。司机取消只适用于自己已接的 `ACCEPTED` 订单。

### GET `/orders/{orderNo}/driver-location`

仅订单乘客，订单状态为 `ACCEPTED`、`DRIVER_ARRIVED` 或 `IN_SERVICE`。返回司机最新
位置：`latitude`、`longitude`、`accuracyM?`、`speedMps?`、`bearing?`、`recordedTime`、
`receivedTime`、`stale`。建议客户端串行每 3-5 秒轮询；`stale=true` 只能显示“最后已知位置”。
暂无位置为 `404`，状态不允许查询为 `409`。

### GET `/orders/{orderNo}/route-context`

仅订单乘客且状态为 `IN_SERVICE`。返回 `driverLocation`、目的地 POI/名称/地址/GCJ-02
坐标，以及 `recommendedRefreshDistanceM`（默认 100）和
`recommendedRefreshSeconds`（默认 60）。客户端用高德 Android SDK 自行规划路线，后端
不保存路线点或轨迹；状态不符为 `409`。

## 4. 司机资料、车辆和位置

### GET `/drivers/me`

仅 `driver`。返回司机资料：`id`、手机号及脱敏字段、驾驶证、评分、`carId`、审核状态
`approvalStatus` 和工作状态 `workStatus`。`carId` 是当前使用车辆，不代表全部绑定车辆。

### GET `/drivers/me/cars`

仅 `driver`。返回当前仍有效绑定到本人的 `Car[]`。车辆对象含 `id`、`licenseNumber`、
`carNumber`、`carType`、`brand`、`color`、车牌拆分字段、座位数、年份、`vehicleStatus`、
创建和更新时间。已解绑/停用的历史绑定不返回。

### PUT `/drivers/me/car`

请求：`{"carId":7}`。绑定可用车辆，并同时写入 `driver.car_id` 作为当前用车；一名司机
可以绑定多辆车，一辆车不能同时绑定其他司机。冲突返回 `409`。

### PUT `/drivers/me/current-car`

请求：`{"carId":7}`。只能选择本人有效绑定且车辆 `vehicleStatus=active` 的车辆，只切换
`driver.car_id`，不新增或删除绑定。未绑定返回 `403`，车辆不可用返回 `409`。

### DELETE `/drivers/me/car`

解绑当前车辆并保留绑定历史；不会影响其他有效绑定。成功 `data=null`。

### DELETE `/drivers/me`

仅 `driver`。注销会将 `user.status` 标记为 `cancelled`，解除当前用车并结束有效车辆
绑定，保留司机资料、车辆绑定和历史订单关联；存在 `ACCEPTED`、`DRIVER_ARRIVED` 或
`IN_SERVICE` 订单时返回 `409`，必须先完成或取消；账号不可再登录或刷新令牌，注销不可恢复。

### PUT `/drivers/me/work-status`

请求：`{"status":"online"}` 或 `{"status":"offline"}`。上线要求账号有效、审核通过、
有当前有效车辆；下线不能有进行中订单。`busy` 由接单服务维护，客户端不能直接设置。
失败可能为 `400/401/403/404/409`。

### PUT `/drivers/me/location`

仅 `online` 或 `busy` 司机。请求字段：GCJ-02 `latitude`、`longitude`，可选
`accuracyM`（米）、`speedMps`（米/秒）、`bearing`（0-360），必填
`recordedTime`（`yyyy-MM-dd HH:mm:ss`）。服务端维护接收时间，只保留最新位置，不保存轨迹。
成功 `data=null`；未出车返回 `409`。

## 5. 司机抢单与行程

### GET `/drivers/me/orders/pending?page=&size=`

仅满足在线、审核通过、当前车辆有效且位置新鲜条件的司机。返回尚未接单的 `PENDING`
即时订单分页，字段为订单摘要和接单所需地点/预估信息。资格或位置不满足时返回 `409`。

### POST `/orders/{orderNo}/accept`

司机抢单。服务端以订单版本、`PENDING` 状态和空司机字段做原子竞争；成功返回
`orderNo`、`status=ACCEPTED`、本次 `carId` 和 `acceptedTime`。订单已被他人抢走或司机
不满足资格返回 `409`。

### GET `/drivers/me/orders?page=&size=&status=`

仅返回当前司机已接订单，分页参数与乘客列表相同。

### POST `/orders/{orderNo}/arrive`

仅已接订单司机，要求 `ACCEPTED`；服务端写入到达时间和审计事件，成功状态为
`DRIVER_ARRIVED`。乱序或重复操作返回 `409`。

### POST `/orders/{orderNo}/start-service`

仅已接订单司机，要求 `DRIVER_ARRIVED`；请求体必须携带 `{"phoneTail":"1234"}`（乘客
手机尾号后四位数字），服务端与乘客资料核对一致后写入开始时间并计算等待秒数，成功状态为
`IN_SERVICE`，响应的 `waitingDurationS` 为服务端计算值。尾号缺失/格式错误/不匹配返回
`400`；状态/时间异常返回 `409`。

### POST `/orders/{orderNo}/complete`

仅已接订单司机，要求 `IN_SERVICE`。服务端按订单锁定的规则、计费距离、实际服务秒数和
等待秒数结算，返回 `OrderCompletionResponse`：`orderNo`、`status=COMPLETED`、
`completedTime`、`actualDurationS`、`waitingDurationS` 和最终 `fare`。客户端不得重算金额。

## 6. 状态机与客户端行为

```text
PENDING -> ACCEPTED -> DRIVER_ARRIVED -> IN_SERVICE -> COMPLETED
    |          |
    +----------+----------> CANCELLED
```

- 乘客可取消：`PENDING`、`ACCEPTED`。
- 司机可取消：自己已接的 `ACCEPTED`。
- 司机位置查询：`ACCEPTED`、`DRIVER_ARRIVED`、`IN_SERVICE`。
- 路线上下文：仅 `IN_SERVICE`。
- `COMPLETED`、`CANCELLED` 为终态；进入终态后停止轮询、位置上报和路线规划。
- 等待费、行程时间费和距离费均由后端计价；等待前 5 分钟是否免费及分段价格以订单
  规则快照为准，客户端只展示响应字段。

## 7. 当前契约偏差与待确认项

1. 当前后端没有 `/auth/logout` 接口。客户端退出应先清理本地 access/refresh token；
   若需要服务端撤销 refresh token，需要另行设计接口。
2. 乘客注销已改为软注销并保留订单外键关联；个人资料脱敏、同用户名/邮箱重新注册和注销
   后历史订单可见范围仍需产品确认。
3. 司机 `GET /drivers/me/cars` 返回有效绑定车辆，但返回字段为持久化车辆 DTO；客户端不得
   把其中身份证明或内部字段直接展示给乘客。

## 8. 客户端配置

- `API_BASE_URL`：通过 `--dart-define` 覆盖，生产必须 HTTPS。
- `AMAP_ANDROID_KEY`：当前由两个 App 各自的 `android/amap.properties` 注入 Manifest，
  不进入 common、Dart 常量或版本库；高德控制台应限制包名和签名 SHA-1。

