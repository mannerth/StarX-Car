# StarX-Car 后端

StarX-Car-Backend 是 StarX-Car 网约车系统的 Spring Boot 后端。当前已实现用户认证、乘客/司机资料、司机车辆管理，以及供 Vue 管理端使用的管理员用户管理与密码管理能力。

数据库建表及触发器脚本见 [create_sql.md](./create_sql.md)。接口在线文档在服务启动后可通过 Swagger UI 查看。

## 技术栈

- Java 21
- Spring Boot 3.5.16 / Spring Security 6
- MyBatis-Plus 3.5.7
- MySQL 5.6.50
- JWT（access token + refresh token）
- BCrypt 密码哈希
- springdoc OpenAPI / Swagger UI
- Maven Wrapper

## 当前已实现能力

### 认证与账号

- 乘客注册：创建 `user` 与 `passenger` 记录。
- 司机注册：创建 `user` 与 `driver` 记录。
- 用户名和非空邮箱唯一性校验。
- BCrypt 保存和验证密码，接口不会返回密码字段。
- 用户名密码登录，成功后签发 access token 与 refresh token。
- 刷新令牌：仅接收 `token_type=refresh` 的有效 JWT，并签发新的 access/refresh token 对。
- 登录成功更新 `last_login_time`。
- 当前用户查看和编辑用户名、邮箱、性别。
- 当前用户修改密码：必须验证原密码，之后以 BCrypt 保存新密码。

### 角色与权限

用户角色来自 `user.type`：

| 角色 | 值 | 已实现权限 |
| --- | --- | --- |
| 管理员 | `admin` | 用户查询、编辑、删除乘客/司机、重置任意用户密码 |
| 乘客 | `passenger` | 管理本人乘客资料、注销账号 |
| 司机 | `driver` | 管理本人司机资料、绑定车辆、管理当前绑定车辆、注销账号 |

受保护接口采用 `Authorization: Bearer <accessToken>`。Spring Security 将 JWT 的 `role` 声明映射为 `ROLE_ADMIN`、`ROLE_PASSENGER` 或 `ROLE_DRIVER`。

### 管理员用户管理

- 用户分页查询；支持用户名/邮箱关键字和角色筛选。
- 查看用户基础资料，不返回密码。
- 编辑任意用户的用户名、邮箱、性别；用户名和非空邮箱保持唯一。
- 删除乘客/司机：在事务中先删除 `passenger` 或 `driver` 扩展资料，再删除 `user` 主记录。
- 不允许通过管理接口删除管理员账号或当前登录管理员。
- 任一管理员可重置任意用户密码（包括管理员）为配置的默认密码。

### 乘客、司机与车辆

- 乘客可查看、更新本人的手机号和注销账号。
- 用户可维护真实姓名和头像；管理员可维护账号状态，非 `active` 用户不能登录或刷新令牌。
- 司机可查看、更新手机号、驾驶证号、身份证号码和驾驶证有效期，绑定或解绑车辆，以及注销账号。
- 司机可创建并自动绑定车辆，查看、编辑或停用当前绑定车辆；车辆请求使用省份简称、发牌机关代号和号码部分，服务端自动生成完整车牌号。
- 车辆拆分车牌组合唯一；司机只能操作本人绑定的车辆。车辆绑定和解绑同时记录 `driver_car_binding` 历史。
- 乘客/司机号码的尾号和脱敏展示号由 MySQL 触发器维护。
- 数据库已预留账号实名/状态、司机审核与工作状态、车辆归属地和拆分车牌字段；`driver_car_binding` 保留司机换车的绑定历史，当前 API 仍使用 `driver.car_id` 维护即时绑定关系。

### 图片上传

- 已登录用户可通过 `POST /api/image/upload` 上传单张图片，字段名为 `file`，后端通过服务端配置的图床账号完成上传。
- 仅接受 `image/*` 的非空文件，沿用全局单文件 20 MB、单请求 50 MB 限制；成功返回图片密钥、原始文件名、公开 URL 和缩略图 URL。
- 图床令牌失效时自动重新授权并重试一次；账号未配置返回 503，图床不可用或响应异常返回 502，不暴露上游错误详情。
- 当前用户通过 `PUT /api/users/me` 的 `multipart/form-data` 资料表单上传 `avatar` 文件时，后端会先调用图床服务，再把返回 URL 保存到既有 `user.avatar_url` 字段；未附带文件时保留原头像。客户端无需、也不得单独调用图床接口。

## API 概览

成功响应统一包装为：

```json
{
  "code": 0,
  "message": "success",
  "traceId": "...",
  "data": {}
}
```

认证接口无需 Bearer token：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 用户名密码登录 |
| `POST` | `/api/auth/refresh` | 使用 refresh token 刷新令牌 |
| `POST` | `/api/auth/passengers/register` | 注册乘客 |
| `POST` | `/api/auth/drivers/register` | 注册司机 |

当前用户接口：

| 方法 | 地址 | 角色 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/users/me` | 任意登录用户 | 获取基础用户资料 |
| `PUT` | `/api/users/me` | 任意登录用户 | 修改用户名、邮箱、性别 |
| `POST` | `/api/users/me/password` | 任意登录用户 | 验证原密码后修改密码 |
| `GET` / `PUT` / `DELETE` | `/api/passengers/me` | 乘客 | 管理/注销本人乘客资料 |
| `GET` / `PUT` / `DELETE` | `/api/drivers/me` | 司机 | 管理/注销本人司机资料 |
| `PUT` / `DELETE` | `/api/drivers/me/car` | 司机 | 绑定或解绑车辆 |

管理员接口（均需要 `ROLE_ADMIN`）：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/users` | 分页查询；参数为 `page`、`size`、`keyword`、`type` |
| `GET` | `/api/admin/users/{userId}` | 获取用户基础资料 |
| `PUT` | `/api/admin/users/{userId}` | 编辑用户基础资料 |
| `POST` | `/api/admin/users/{userId}/reset-password` | 重置任意用户密码为默认密码 |
| `DELETE` | `/api/admin/users/{userId}` | 删除乘客或司机账号 |

车辆接口（均需要 `ROLE_DRIVER`）：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `/api/cars/me` | 获取当前绑定车辆 |
| `POST` | `/api/cars` | 创建并绑定车辆 |
| `GET` / `PUT` / `DELETE` | `/api/cars/{carId}` | 操作当前司机绑定车辆 |

图片接口（均需要登录）：

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `POST` | `/api/image/upload` | `multipart/form-data`，字段名 `file`；上传单张图片并返回图床访问地址 |
| `PUT` | `/api/users/me` | JSON 更新基础资料；或以 `multipart/form-data` 同时提交基础资料与可选 `avatar` 文件，上传并保存头像 |

完整的请求模型、示例和错误状态码，请访问 Swagger UI：`/swagger-ui.html`。

## 配置

配置文件为 `src/main/resources/application.yaml`。生产环境请使用环境变量，不要提交真实密钥或数据库密码。

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SERVER_PORT` | 服务端口 | `8080` |
| `MYSQL_HOST` | MySQL 主机 | `localhost` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_DATABASE` | 数据库名称 | 无 |
| `MYSQL_USERNAME` | 数据库用户名 | 空 |
| `MYSQL_PASSWORD` | 数据库密码 | 空 |
| `JWT_SECRET` | JWT 签名密钥，必填 | 无 |
| `JWT_EXPIRE` | access token 有效期（秒） | `3600` |
| `JWT_REFRESH_EXPIRE` | refresh token 有效期（秒） | `604800` |
| `ADMIN_DEFAULT_PASSWORD` | 管理员重置密码时使用的默认密码 | 当前配置为 `123456` |
| `AMAP_KEY` | 高德地图 Key，预留 | 空 |
| `UPLOAD_PATH` | 上传目录，预留 | 空 |
| `IMAGE_SERVICE_BASE_URL` | 图床 API 基础地址 | `https://image.foofish.work/api/v1` |
| `IMAGE_SERVICE_EMAIL` | 图床授权邮箱，必填 | 空 |
| `IMAGE_SERVICE_PASSWORD` | 图床授权密码，必填 | 空 |
| `IMAGE_SERVICE_STRATEGY_ID` | 图床存储策略 ID，可选 | 空 |

> 安全提示：`ADMIN_DEFAULT_PASSWORD` 必须在正式环境显式设为强密码。不要继续使用当前开发配置中的 `123456`，也不要将真实密码写入版本控制。

例如，在 Unix-like Shell 中：

```bash
export MYSQL_DATABASE=starx_car
export MYSQL_USERNAME=starx
export MYSQL_PASSWORD='replace-me'
export JWT_SECRET='a-long-random-secret-at-least-32-characters'
export ADMIN_DEFAULT_PASSWORD='a-strong-reset-password'
```

## 数据库初始化

1. 创建 MySQL 数据库，例如 `CREATE DATABASE starx_car DEFAULT CHARACTER SET utf8mb4;`。
2. 执行 [create_sql.md](./create_sql.md) 中的 `user`、`passenger`、`driver`、`car`、`admin` 表和触发器脚本。
3. 创建至少一个 `user.type = 'admin'` 的管理员账号。密码必须是 BCrypt 哈希，不能以明文写入 `user.password`。
4. 设置上述环境变量后再启动服务。

当前管理员鉴权使用 `user.type`；`admin` 从表可按照建表脚本保留，用于维持关系模型。

## 本地运行

### macOS/Linux

```bash
cd StarX-Car-Backend
export JAVA_HOME=/Users/mannerth/.sdkman/candidates/java/21.0.11-oracle
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

服务默认地址为 `http://localhost:8080`。

### 常用命令

```bash
# 运行测试
./mvnw test

# 打包
./mvnw clean package

# 启动
./mvnw spring-boot:run
```

仅使用 Maven Wrapper，不需要安装全局 Maven。

## 文档、日志与错误处理

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 请求支持并返回 `X-Trace-Id`；响应体也会带 `traceId`，可用于日志定位。
- 认证、权限、参数校验、唯一约束等错误返回统一 JSON 结构，不返回密码、JWT、数据库异常详情或堆栈。

## 当前未实现的业务

项目根目录描述了网约车课程设计的完整目标。订单、叫车/预约、派单、行程状态、实时定位、导航、支付、评价、收入统计和调度规则等业务尚未在当前后端实现。本阶段已完成的是账号、资料、车辆及管理端用户管理基础能力。
