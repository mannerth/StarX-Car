## Unreleased

- `AmapMapView` 为地图注册 `EagerGestureRecognizer` 参与 Flutter 手势竞技场；平台视图放入 `ListView` 等滚动容器时不再被父级滚动识别器抢走指针事件，地图可正常拖动、缩放、旋转和倾斜。
- POI 搜索移除固定 5 公里硬边界，改为当前城市优先并按定位点距离排序；同城无结果时自动全国兜底。逆地理编码和 POI 结果增加城市、区县及可用距离信息。

- `AmapMapView` 增加响应式地图视野：单点变化后自动居中，双点或路线变化后按边界缩放，并用不同颜色区分起终点。
- `Place.toJson` 将高德坐标统一四舍五入至 6 位小数，匹配后端 `@Digits` 约束，修复部分 POI 报价时出现“数字的值超出了允许的范围”；增加精度和坐标系测试。

- 修复内置 `amap_flutter_map` 在地图加载完成时主动读取空数组、导致每次首次启动固定抛出 `ArrayIndexOutOfBoundsException` 的问题。
- 将高德定位 SDK 从 5.6.0 升级到仍支持 x86_64 模拟器的 6.5.1，纳入新版 Android 的合规与兼容修复。
- 关闭定位请求中未使用的地址解析，避免一次性当前位置额外触发逆地理编码鉴权和配额消耗；地点名称仍由独立的搜索/逆地理编码能力按需获取。

- 增加 `AmapLocationService.locateOnce` 一次性定位能力，供乘客端选择当前位置；仍由角色端负责权限错误展示和生命周期释放。

- 为公共 `ApiClient` 增加所有请求方法的可选 `CancelToken`，主动取消统一映射为 `ApiException.cancelled`，并补充取消请求测试；角色端无需直接依赖 Dio 即可使用 common 导出的 `CancelToken`。

- Add offline `ApiClient` tests for Bearer injection, response-envelope errors, single-flight concurrent 401 refresh/replay, and session cleanup after refresh failure.

- Preserve server order events in the role-neutral order-detail model, including raw event/operator values for forward-compatible timeline display.

- Add the shared client API contract covering authentication, passenger/driver orders, vehicle binding, GCJ-02 locations, status transitions and known backend contract gaps.

- Add GCJ-02 route polyline support to `AmapMapView`, an external AMap driving-navigation hand-off, and the role-neutral `OrderCompletionResult` settlement model.
- Pass the required AMap privacy declaration to map widgets and preserve raw location failures/accuracy metadata for role-specific handling.
- Add an Android MethodChannel bridge for official AMap driving routes, nearby POI search and reverse geocoding; all returned coordinates remain explicitly GCJ-02.

- Vendor and migrate the AMap map/base/location plugins for Flutter 3.44, and expose common `AmapMapView`/`AmapLocationService` wrappers with GCJ-02 enforcement.


- Add role-neutral order, fare, pagination, driver-location and route-context models. Decimal fare fields remain strings to avoid client-side floating-point rounding, unknown order states map to a safe fallback, and location models preserve the server `stale` flag.
- Add Dio connect/send/receive timeouts for mobile requests and model contract tests covering missing drivers, stale locations, GCJ-02 coordinates and unknown states.

- `ApiClient.putMultipart` 现在按文件名推导 multipart 图片 MIME 类型，修复 `MultipartFile.fromBytes` 默认 `application/octet-stream` 被后端图片校验拒绝的问题。
- 刷新令牌请求标记为跳过 Bearer 拦截器，避免把即将失效的 access token 附加到 `/auth/refresh`。

- 新增 `ApiClient.putMultipart`，为角色端的资料表单提供带鉴权、统一响应解析及 401 刷新的图片文件上传传输能力；不包含任何角色业务流程。

- 扩展共享 `UserProfile`，解析真实姓名、头像地址和账号状态字段。

- Rewrite README to document the implemented session, HTTP, token-refresh, secure-storage and model capabilities, compatibility pinning, boundaries, validation and pending map work.

- Fix the Dio request-header constant used by the shared API client so all three Flutter projects compile.
- Pin `flutter_secure_storage` to the Android-template-compatible 9.x line; 10.x pulls in `jni` 1.x, whose Gradle script is incompatible with the current build.
- Override `path_provider_android` to 2.2.22 for the same Gradle compatibility reason; this removes the remaining Android `jni` plugin from the generated plugin graph.

- Add encrypted session persistence, role-neutral account profile model, API exception model and Dio HTTP transport.
- Add automatic Bearer token injection and single-flight refresh/retry support for 401 responses.

- Document the responsibility boundary of the shared package: common infrastructure and role-neutral models only; passenger and driver workflows remain in their respective apps.
- Document the GCJ-02 coordinate convention and the boundary for future real-time location support.

## 0.0.1

- Initialize the local shared package for the passenger and driver Android apps.
- Add common API response, authentication session, and map location models.
## Unreleased

- 新增 `AmapPermissionService` 与 Android 插件定位权限通道：可查询前台定位权限状态、主动请求 `ACCESS_FINE_LOCATION`，并区分“已授予 / 普通拒绝 / 永久拒绝”，供两个角色端登录后主动申请定位权限。
- `AmapMapView` 支持多条不同颜色的路线折线（`routes`），用于接驾阶段同时绘制司机驾车路线与乘客步行路线；`viewportPoints` 仍可指定以哪条路线为主进行视野缩放。
- `AmapRouteService` 新增 `planWalking` 步行路线规划，`AmapWalkingRoute` 模型解析高德步行路线的距离、时长与 GCJ-02 折线。
- `OrderDetail` 增加 `passengerPhoneTail` 字段（乘客手机尾号后四位），供司机端开始服务前核对乘客身份。

- 高德定位插件启动时从应用清单读取 `com.amap.api.v2.apikey` 并显式设置定位 SDK，避免 Android 初始化时序导致 Key 未生效。

