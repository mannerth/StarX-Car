# StarX-Car

2026数据库课程设计-网约车

## 核心功能

乘客：
-   登录注册
-   选择上车地点和目的地
-   发起即使/预约叫车
-   行程中查看进度
-   支付
-   评价

司机：
-   登录注册
-   接单
-   行程中实施导航/跳转高德地图导航
-   管理收入

系统：
-   派单
-   调控司机
    - 拒单过多时提醒/惩罚
    - 强制派单
-   预约单定时任务



## 打车业务流程

```mermaid
flowchart LR
    A[乘客发起叫车<br/>上传位置/目的地] --> B{有可用司机吗?}
    B -- 否 --> Z[提示无车、等待/稍后再试]
    B -- 是 --> C[计算ETA & 动态定价]
    C --> D[派单：推送订单给司机]
    D --> E{司机响应}
    E -- 接单 --> F[更新司机状态,<br/>通知乘客司机信息]
    E -- 拒单 --> J[重新派单/扩大范围]
    F --> G[行程中：定时上报位置信息]
    G --> H[到达目的地：开始计费]
    H --> I[订单完成：支付并评价]
```

## 派单逻辑

维护一个订单池，订单池有地域标识，初期仅一个，后期可扩展  
订单可要求司机评分？先置空

派单时，优先给*优先级*高的k个司机，m秒内无响应，扩大到2k个...
> 优先级计算可调整、扩展，
> 初期为 *评分 + 100/路程时间*


## 实体

User -> Passenger, Driver, Admin
Car
Order
Comment
OrderPool
City

User:
-   id (primary key)
-   username (not null, unique)
-   password (hashed)
-   gender
-   email (unique)
-   type (enum: PASSENGER, DRIVER, ADMIN)
-   create_time
-   last_login_time

Passenger:
-   id (primary key, foreign key to User)
-   phone_number (not null)
-   phone_tail (not null, generated from phone_number)
-   display_phone (not null, generated from phone_number)

Driver:
-   id (primary key, foreign key to User)
-   phone_number (not null)
-   license_number (not null)
-   rating (default 5.0)
-   car_id (foreign key to Car)
-   display_phone (not null, generated from phone_number)

Admin:
-   id (primary key, foreign key to User)

- Car:
-   id (primary key)
-   license_number (not null)
-   car_number (not null)
-   car_type (not null)
-   brand (not null)
-   color (not null)
-   create_time
-   last_update_time