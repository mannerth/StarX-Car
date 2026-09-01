# StarX-Car 管理端

StarX-Car-Admin 是网约车系统的后台管理 Web 应用。它面向 `admin` 角色，提供账号登录、订单监控与预约运营、用户管理、司机审核、计费规则管理、投诉售后运营、个人资料维护和密码修改功能。投诉工单支持筛选、关联订单查看及道歉、驳回、部分/全额退款处理。

界面基于 Element Plus 重构：`AdminLayout` 集中管理侧栏导航、管理员菜单和退出登录，运营页面仅负责自身业务内容。路由采用按需加载，桌面与窄屏均复用同一套运营工作台布局。

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

### 订单监控与预约运营

- 订单监控支持按订单号、即时/预约类型、订单状态和乘客/司机关键字分页查询；展示脱敏联系方式、行程、预约/派单时间、金额和最终结算状态，并每 30 秒刷新一次。
- 预约运营页只展示预约订单，每 15 秒刷新；显示预约时间、倒计时、司机接单情况，等待派单的预约订单可填写原因后提前派入待抢池。
- 订单详情每 10 秒刷新，展示订单状态事件、费用明细、追加式运营备注及司机最后位置文本，不接入地图或历史轨迹。
- 管理员仅能取消 `SCHEDULED`、`PENDING` 订单；取消和提前派单均必须填写原因。系统记录订单事件与独立管理员写操作审计，不记录自动轮询读取。

### 个人中心

- 查看并修改当前管理员的用户名、真实姓名、头像地址、邮箱和性别。
- 输入原密码、新密码、确认新密码后修改密码。
- 前端校验新密码长度至少 6 位，以及两次新密码一致；后端会再次校验原密码和参数。
- 个人资料保存后同步更新顶部显示和本地用户资料。

### 司机审核

- 分页查看司机账号、真实姓名、联系方式、驾驶证号、脱敏身份证号、证件有效期、当前车辆、审核状态和工作状态。
- 支持按待审核、已通过、已驳回筛选，并按账号、姓名、邮箱、手机号或证件号搜索。
- 待审核司机可通过或驳回，提交前要求二次确认；已处理申请不会再次显示操作入口。
- 审核接口只允许处理 `pending` 记录，账号不可用、资料不存在或重复审核时会展示后端返回的明确错误。

### 计费方式管理

- 分页查看版本化计费规则，并按草稿、已生效、已停用和已归档状态筛选。
- 展开查看起步价、起步价包含距离、最低消费，以及里程、行程时间和等待时间的完整分段。
- 新建规则时提供可直接调整的默认分段；支持为三种收费类型添加、删除和编辑连续分段，终点留空表示无上限。
- 仅草稿规则可编辑；可选择仅激活或激活并切换为默认规则。
- 非当前默认且未归档的规则可归档，历史订单仍保留原规则版本作为计费依据。

## 页面与路由

| 路由 | 页面 | 访问要求 |
| --- | --- | --- |
| `/login` | 管理员登录 | 公开；已登录管理员会自动前往订单监控 |
| `/orders` | 订单监控 | 管理员 |
| `/reservations` | 预约运营 | 管理员 |
| `/orders/:orderNo` | 订单详情和运营操作 | 管理员 |
| `/users` | 用户管理 | 管理员 |
| `/drivers` | 司机审核 | 管理员 |
| `/pricing` | 计费方式管理 | 管理员 |
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
| `GET` | `/api/admin/orders` | 订单监控筛选和分页 |
| `GET` | `/api/admin/orders/summary` | 订单运营统计摘要 |
| `GET` | `/api/admin/orders/{orderNo}` | 订单详情、状态事件和费用 |
| `GET` | `/api/admin/orders/{orderNo}/fare-items` | 订单计费明细 |
| `GET` / `POST` | `/api/admin/orders/{orderNo}/notes` | 运营备注查询或追加 |
| `GET` | `/api/admin/orders/{orderNo}/driver-location` | 司机最后位置文本数据 |
| `POST` | `/api/admin/orders/{orderNo}/cancel` | 取消等待派单或待抢订单 |
| `POST` | `/api/admin/orders/{orderNo}/dispatch` | 提前派发等待派单预约订单 |
| `GET` | `/api/admin/users` | 用户分页、搜索和筛选 |
| `GET` | `/api/admin/users/{userId}` | 获取用户详情（供后续扩展使用） |
| `PUT` | `/api/admin/users/{userId}` | 编辑用户资料 |
| `POST` | `/api/admin/users/{userId}/reset-password` | 重置用户密码 |
| `DELETE` | `/api/admin/users/{userId}` | 删除乘客或司机账号 |
| `GET` | `/api/admin/drivers` | 分页查询和筛选司机审核资料 |
| `PUT` | `/api/admin/drivers/{driverId}/approval` | 通过或驳回待审核司机 |
| `GET` / `POST` | `/api/admin/pricing-rules` | 分页查询或新建计费规则草稿 |
| `GET` / `PUT` | `/api/admin/pricing-rules/{ruleId}` | 查看或更新计费规则草稿 |
| `POST` | `/api/admin/pricing-rules/{ruleId}/activate` | 激活草稿，或将已启用规则设为新订单默认 |
| `POST` | `/api/admin/pricing-rules/{ruleId}/archive` | 归档非当前默认规则 |
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
  api/          Axios 封装、订单运营、用户、司机审核、计费规则和个人中心 API
  router/       路由定义与管理员访问守卫
  stores/       Pinia 会话状态
  types/        后端响应与领域模型类型
  views/        登录、订单监控、预约运营、订单详情、用户管理、司机审核、计费管理和个人中心页面
  styles.css    全局样式
```

## 注意事项

- 管理端不在浏览器端保存或展示默认密码；默认密码只由后端 `ADMIN_DEFAULT_PASSWORD` 配置决定。
- 令牌保存在 `localStorage` 便于当前单页应用保持会话。生产部署仍应配合 HTTPS、受信任域名与内容安全策略。
- 用户列表的权限、删除限制和密码重置权限均由后端再次校验，前端仅负责交互展示。
- 计费规则中仅“新订单默认”规则用于新订单计价；已启用但非默认的规则可保留，并一键切换为默认，切换不影响历史订单。
- 管理端当前覆盖账号、司机准入、计费规则、订单监控和预约运营；支付、退款、订单轨迹回放、自动派单和司机收入运营能力尚未实现。
