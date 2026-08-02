# Changelog

## 2026-08-01

- 司机车辆模型调整为多车辆有效绑定：`driver_car_binding` 保留司机全部有效及历史绑定，`driver.car_id` 仅指向当前实际用车；创建或绑定新车辆不再结束其他车辆绑定。
- 新增 `GET /api/drivers/me/cars` 查询当前司机全部有效绑定车辆，以及 `PUT /api/drivers/me/current-car` 在已绑定车辆中切换当前用车；原 `PUT /api/drivers/me/car` 保持兼容，仍执行绑定并设为当前用车。
- 司机注销改为将 `user.status` 标记为 `cancelled`，结束有效绑定并清空当前用车，但保留 `driver` 资料及 `driver_car_binding` 历史记录。
- 更新 `create_sql.md` 的 `driver.car_id` 与 `driver_car_binding.status` 注释，明确前者是当前用车、后者表示有效/历史绑定而非当前用车。

- 移除管理员密码重置的 `123456` 弱默认值：`ADMIN_DEFAULT_PASSWORD` 现在为必填环境变量，并继续由配置校验约束为 6 至 72 个字符；同时将 README 的部署配置说明更新为无默认值。
- 将 MyBatis-Plus 全局主键策略改为 `auto`，与现有 MySQL 自增主键及实体上的 `IdType.AUTO` 约定一致；`driver` 和 `passenger` 仍通过实体的 `IdType.INPUT` 使用对应 `user.id`。
- 请求访问日志只记录 `Authentication.getName()` 的用户 ID，不再序列化整个 Spring Security 认证主体，避免日志包含无关认证对象信息。
- 补全认证、当前用户、乘客、司机和车辆接口的 OpenAPI 成功、参数校验、认证、权限、资源不存在及冲突状态码说明，使 Swagger UI 与实际错误契约一致。

- `PUT /api/users/me` 新增 Multipart 资料更新形式：接收可选 `avatar` 图片文件，调用内部图床服务并将成功返回的 URL 保存至现有 `user.avatar_url`；图床失败时不执行用户资料更新。
- 新增受 JWT 保护的 `POST /api/image/upload` 接口，接收 `multipart/form-data` 的 `file` 图片字段，并返回图床图片密钥、原始文件名、公开 URL 和缩略图 URL。
- 接入 `image.foofish.work` 图床：后端以环境变量配置账号和可选存储策略，获取并线程安全缓存图床 Bearer Token；上游返回 401 时清除缓存、重新授权并重试一次上传。
- 增加图片空文件、非图片 MIME 类型、图床未配置和上游失败的明确错误处理；账号、密码、Token 和上游错误正文均不会暴露给客户端或写入日志。
- 添加图片上传控制器回归测试，并在 README 记录接口、文件限制与 `IMAGE_SERVICE_*` 配置项。

## 2026-07-31

- 调整用户、司机与车辆基础表：补充真实姓名、头像、账号状态、司机审核/工作状态、证件有效期及车辆车牌拆分、归属地和载客信息字段。
- 新增 `driver_car_binding` 表及司机、车辆当前/历史绑定查询索引；保留现有 `driver.car_id`，以保证既有后端功能不受立即影响。
- 为 `car` 新增按省份简称、发牌机关代号和号码部分组成的联合唯一车牌约束。
- 后端接口同步支持新增用户、司机和车辆资料字段；注册、个人资料、管理员用户管理和返回资料均可读写或展示相应字段。
- 车辆创建、绑定、解绑和停用流程同步维护 `driver_car_binding`，并继续维护 `driver.car_id` 以兼容现有查询；物理删除车辆改为标记 `disabled`，保留绑定历史。
- 账号状态为 `disabled` 或 `cancelled` 时拒绝登录和刷新令牌。
- 在管理端和两端 Flutter 客户端切换至拆分车牌请求后，移除 `CarRequest` 中旧版 `carNumber` 入参；完整车牌仍由服务端生成并持久化。
