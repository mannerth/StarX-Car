# StarX-Car 管理端

StarX-Car-Admin 是网约车系统的后台管理 Web 应用。它面向 `admin` 角色，提供账号登录、用户管理、个人资料维护、密码修改和管理员密码重置功能。

后端项目位于相邻的 `../StarX-Car-Backend` 目录；默认开发环境通过 Vite 代理将 `/api` 请求转发到 `http://localhost:8080`。

## 技术栈

- Vue 3 + TypeScript + Composition API
- Vite 8
- Vue Router 5：路由与登录守卫
- Pinia：登录用户和令牌状态
- Axios：统一 HTTP 客户端、令牌附加与自动刷新
- pnpm：包管理和锁定依赖版本

## 已实现功能

### 管理员认证

- 使用后端 `POST /api/auth/login` 登录。
- 仅允许返回用户类型为 `admin` 的账号进入管理端；乘客和司机账号会被拒绝。
- access token、refresh token 和当前用户资料保存于浏览器 `localStorage`。
- 未登录或非管理员访问受保护路由时自动跳转到 `/login`。
- 可以从侧边栏主动退出，退出会清除本地会话。

### Axios 与令牌刷新

- 每个 API 请求会自动带上 `Authorization: Bearer <accessToken>`。
- 业务接口返回 `401` 时，客户端会调用 `POST /api/auth/refresh`，提交已保存的 refresh token。
- 刷新成功时，会替换 access token、refresh token 和用户资料，并自动重放最初失败的请求。
- 同一时刻多个请求发生 `401` 时，只会发送一个刷新请求，其余请求等待该结果，避免重复刷新。
- 缺少 refresh token、刷新失败或 refresh 接口本身失败时，会清空会话并回到登录页。

### 用户管理

- 分页查看全部用户。
- 按用户名或邮箱关键字查询。
- 按管理员、乘客、司机类型筛选。
- 显示用户 ID、用户名、真实姓名、邮箱、性别、角色、账号状态和最近登录时间。
- 编辑用户名、真实姓名、头像地址、邮箱、性别和账号状态；账号状态为停用或注销时后端会拒绝登录和令牌刷新。
- 删除乘客、司机账号；管理员账号不显示删除入口。
- 重置任意用户密码为后端配置的默认值，包括管理员账号；前端不会读取或展示默认密码。
- 删除和重置密码前均要求浏览器二次确认。

### 个人中心

- 查看并修改当前管理员的用户名、真实姓名、头像地址、邮箱和性别。
- 输入原密码、新密码、确认新密码后修改密码。
- 前端校验新密码长度至少 6 位，以及两次新密码一致；后端会再次校验原密码和参数。
- 个人资料保存后同步更新顶部显示和本地用户资料。

## 页面与路由

| 路由 | 页面 | 访问要求 |
| --- | --- | --- |
| `/login` | 管理员登录 | 公开；已登录管理员会自动前往用户管理 |
| `/users` | 用户管理 | 管理员 |
| `/profile` | 个人中心 | 管理员 |

## API 对接

所有成功响应遵循后端统一结构：

```json
{
  "code": 0,
  "message": "success",
  "traceId": "...",
  "data": {}
}
```

管理端已对接的接口：

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 管理员登录 |
| `POST` | `/api/auth/refresh` | 刷新令牌 |
| `GET` | `/api/admin/users` | 用户分页、搜索和筛选 |
| `GET` | `/api/admin/users/{userId}` | 获取用户详情（供后续扩展使用） |
| `PUT` | `/api/admin/users/{userId}` | 编辑用户资料 |
| `POST` | `/api/admin/users/{userId}/reset-password` | 重置用户密码 |
| `DELETE` | `/api/admin/users/{userId}` | 删除乘客或司机账号 |
| `GET` | `/api/users/me` | 获取当前管理员资料 |
| `PUT` | `/api/users/me` | 修改当前管理员资料 |
| `POST` | `/api/users/me/password` | 验证原密码后修改密码 |

## 本地运行

### 前置条件

- Node.js：项目声明支持 `^22.18.0` 或 `>=24.12.0`
- pnpm 10+
- 已启动的后端服务，默认监听 `http://localhost:8080`

### 安装和启动

```bash
cd StarX-Car-Admin
pnpm install
pnpm dev
```

Vite 会打印本地访问地址，通常为 `http://localhost:5173`。

### 生产构建与类型检查

```bash
pnpm build
```

该命令同时执行 `vue-tsc --build` 和 `vite build`。产物位于 `dist/`。

## 后端地址配置

开发环境默认代理配置在 `vite.config.ts`：

```ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080' },
  },
}
```

如管理端部署到与后端不同的域名，可在构建时设置 `VITE_API_BASE_URL`，例如：

```bash
VITE_API_BASE_URL=https://api.example.com/api pnpm build
```

该变量应包含 `/api` 前缀。

## 目录说明

```text
src/
  api/          Axios 封装、用户管理和个人中心 API
  router/       路由定义与管理员访问守卫
  stores/       Pinia 会话状态
  types/        后端响应与领域模型类型
  views/        登录、用户管理、个人中心页面
  styles.css    全局样式
```

## 注意事项

- 管理端不在浏览器端保存或展示默认密码；默认密码只由后端 `ADMIN_DEFAULT_PASSWORD` 配置决定。
- 令牌保存在 `localStorage` 便于当前单页应用保持会话。生产部署仍应配合 HTTPS、受信任域名与内容安全策略。
- 用户列表的权限、删除限制和密码重置权限均由后端再次校验，前端仅负责交互展示。
- 本项目当前实现的是管理端与账号/资料/车辆相关能力；订单、派单、支付、评价、实时定位等核心网约车业务尚未在此管理端实现。
