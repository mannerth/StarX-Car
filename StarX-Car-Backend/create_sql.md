User:

```sql
create table user
(
    id              int auto_increment
        primary key,
    username        varchar(50)                        not null,
    password        varchar(256)                       not null,
    gender          varchar(12)                        null,
    email           varchar(50)                        null,
    type            varchar(10)                        not null,
    create_time     DATETIME default CURRENT_TIMESTAMP null,
    last_login_time DATETIME                           null,
    real_name       varchar(50)                        null comment '真实姓名',
    avatar_url      varchar(255)                       null comment '头像地址',
    status          varchar(20) default 'active'       not null comment '账号状态：active、disabled、cancelled',
    update_time     DATETIME default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP comment '最后更新时间',
    constraint uk_user_email
        unique (email),
    constraint check_gender
        check (gender in ('male', 'female')),
    constraint check_type
        check (type in ('admin', 'passenger', 'driver'))
);

create unique index ui_user_username
    on user (username);
```

Admin:

```sql
CREATE TABLE admin
(
    id INT PRIMARY KEY,                          -- 与 user.id 对应
    CONSTRAINT fk_admin_user FOREIGN KEY (id) REFERENCES user(id)
);
```

```sql
CREATE TABLE car
(
    id                INT AUTO_INCREMENT PRIMARY KEY,
    license_number    VARCHAR(20)  NOT NULL, -- 行驶证号
    car_number        VARCHAR(20)  NOT NULL, -- 车牌号（建议加 UNIQUE）
    car_type          VARCHAR(20)  NOT NULL, -- 车型
    brand             VARCHAR(50)  NOT NULL, -- 品牌
    color             VARCHAR(20)  NOT NULL, -- 颜色
    plate_province    CHAR(1)      NOT NULL, -- 车牌省份简称，例如粤、京、沪
    plate_letter      CHAR(1)      NOT NULL, -- 车牌发牌机关代号，例如A、B
    plate_serial      VARCHAR(6)   NOT NULL, -- 车牌号码数字和字母部分
    registration_place VARCHAR(100) NULL,    -- 车辆登记归属地，例如广东省深圳市
    seat_count        TINYINT UNSIGNED NOT NULL DEFAULT 5, -- 核定载客数
    vehicle_year      YEAR NULL,              -- 车辆出厂年份
    vehicle_status    VARCHAR(20) NOT NULL DEFAULT 'active', -- active、disabled、scrapped
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

create unique index car_car_number_uindex
    on car (car_number);

create unique index uk_car_plate
    on car (plate_province, plate_letter, plate_serial);

```

```sql
CREATE TABLE passenger
(
    id             INT PRIMARY KEY,
    phone_number   CHAR(11)     NOT NULL,
    phone_tail     CHAR(4)      NOT NULL,  -- 普通列，触发器填充
    display_phone  VARCHAR(11)  NOT NULL,  -- 普通列，触发器填充
    CONSTRAINT fk_passenger_user FOREIGN KEY (id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //

-- INSERT 触发器
CREATE TRIGGER trg_passenger_insert
    BEFORE INSERT ON passenger
    FOR EACH ROW
BEGIN
    SET NEW.phone_tail = RIGHT(NEW.phone_number, 4);
    SET NEW.display_phone = CONCAT(LEFT(NEW.phone_number, 3), '****', RIGHT(NEW.phone_number, 4));
END//

-- UPDATE 触发器（防止手机号变更导致数据不一致）
CREATE TRIGGER trg_passenger_update
    BEFORE UPDATE ON passenger
    FOR EACH ROW
BEGIN
    IF NEW.phone_number <> OLD.phone_number THEN
        SET NEW.phone_tail = RIGHT(NEW.phone_number, 4);
        SET NEW.display_phone = CONCAT(LEFT(NEW.phone_number, 3), '****', RIGHT(NEW.phone_number, 4));
END IF;
END//

DELIMITER ;
```

```sql
CREATE TABLE driver
(
    id               INT PRIMARY KEY,
    phone_number     CHAR(11)     NOT NULL,
    license_number   VARCHAR(30)  NOT NULL,
    rating           DECIMAL(2,1) NOT NULL DEFAULT 5.0 COMMENT '缓存平均评分；无评价时保持 5.0',
    rating_sum       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '累计评分总和，用于精确重算平均分',
    rating_count     INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '累计评价次数',
    car_id           INT NULL COMMENT '当前实际使用的车辆 ID；司机可有多辆有效绑定车辆',
    display_phone    VARCHAR(11)  NOT NULL,  -- 普通列，触发器填充
    id_card_no       VARCHAR(18)  NULL,      -- 身份证号码
    license_expire_date DATE       NULL,      -- 驾驶证有效期
    approval_status  VARCHAR(20)  NOT NULL DEFAULT 'pending', -- pending、approved、rejected
    work_status      VARCHAR(20)  NOT NULL DEFAULT 'offline', -- offline、online、busy
    CONSTRAINT fk_driver_user FOREIGN KEY (id) REFERENCES user(id),
    CONSTRAINT fk_driver_car  FOREIGN KEY (car_id) REFERENCES car(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //

-- INSERT 触发器
CREATE TRIGGER trg_driver_insert
    BEFORE INSERT ON driver
    FOR EACH ROW
BEGIN
    SET NEW.display_phone = CONCAT(LEFT(NEW.phone_number, 3), '****', RIGHT(NEW.phone_number, 4));
END//

-- UPDATE 触发器
CREATE TRIGGER trg_driver_update
    BEFORE UPDATE ON driver
    FOR EACH ROW
BEGIN
    IF NEW.phone_number <> OLD.phone_number THEN
        SET NEW.display_phone = CONCAT(LEFT(NEW.phone_number, 3), '****', RIGHT(NEW.phone_number, 4));
END IF;
END//

DELIMITER ;
```

```sql
CREATE TABLE driver_car_binding
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    driver_id    INT NOT NULL, -- 司机用户ID；同一司机可有多条有效绑定记录
    car_id       INT NOT NULL, -- 车辆ID；同一车辆同一时间只能有效绑定一名司机
    bind_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unbind_time  DATETIME NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'active', -- active 表示有效绑定，不等同于 driver.car_id 指向的当前用车；inactive 表示已解绑历史
    CONSTRAINT fk_binding_driver FOREIGN KEY (driver_id) REFERENCES driver(id),
    CONSTRAINT fk_binding_car FOREIGN KEY (car_id) REFERENCES car(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_binding_driver_status
    ON driver_car_binding (driver_id, status);

CREATE INDEX idx_binding_car_status
    ON driver_car_binding (car_id, status);
```

计价规则：

```sql
CREATE TABLE pricing_rule
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(50)    NOT NULL COMMENT '规则名称',
    version             INT            NOT NULL DEFAULT 1 COMMENT '同一规则名称的版本号',
    status              VARCHAR(20)    NOT NULL DEFAULT 'draft' COMMENT 'draft、active、inactive、archived',
    is_default          TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否当前默认规则；服务层保证至多一条 active 默认规则',
    base_fare           DECIMAL(10, 2) NOT NULL COMMENT '起步价',
    included_distance_m INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '起步价包含距离，单位米',
    minimum_fare        DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '最低消费',
    created_by          INT            NULL COMMENT '创建管理员 user.id',
    updated_by          INT            NULL COMMENT '最后修改管理员 user.id',
    created_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_rule_creator FOREIGN KEY (created_by) REFERENCES user (id),
    CONSTRAINT fk_pricing_rule_updater FOREIGN KEY (updated_by) REFERENCES user (id),
    CONSTRAINT uk_pricing_rule_name_version UNIQUE (name, version),
    KEY idx_pricing_rule_status_default (status, is_default)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pricing_rule_tier
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    rule_id       INT            NOT NULL,
    charge_type   VARCHAR(20)    NOT NULL COMMENT 'DISTANCE、DRIVING_TIME、WAITING_TIME',
    range_start   INT UNSIGNED   NOT NULL COMMENT '累计区间起点；距离单位米，时间单位秒，包含起点',
    range_end     INT UNSIGNED   NULL COMMENT '累计区间终点；NULL 表示无上限，不包含终点',
    unit_size     INT UNSIGNED   NOT NULL COMMENT '计费单位；如 1000 米或 60 秒',
    unit_price    DECIMAL(10, 2) NOT NULL COMMENT '每个计费单位的价格',
    rounding_mode VARCHAR(20)    NOT NULL DEFAULT 'CEIL' COMMENT '不足一单位按一单位计',
    sort_no       INT UNSIGNED   NOT NULL COMMENT '同一计费类型内的计算顺序',
    CONSTRAINT fk_pricing_rule_tier_rule FOREIGN KEY (rule_id) REFERENCES pricing_rule (id),
    CONSTRAINT uk_pricing_rule_tier UNIQUE (rule_id, charge_type, sort_no),
    KEY idx_pricing_rule_tier_range (rule_id, charge_type, range_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

订单、计费明细和最新司机位置：

```sql
CREATE TABLE ride_order
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no             VARCHAR(32)    NOT NULL COMMENT '对外订单号',
    passenger_id         INT            NOT NULL,
    driver_id            INT            NULL COMMENT '抢单成功后写入',
    car_id               INT            NULL COMMENT '抢单成功时写入 driver.car_id 快照',
    pricing_rule_id      INT            NOT NULL COMMENT '下单时采用的规则',
    pricing_rule_version INT            NOT NULL COMMENT '下单时规则版本快照',
    status               VARCHAR(30)    NOT NULL COMMENT 'PENDING、ACCEPTED、DRIVER_ARRIVED、IN_SERVICE、COMPLETED、CANCELLED',
    version              INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '乐观锁版本，防止多人同时抢单',
    payment_status       VARCHAR(20)    NOT NULL DEFAULT 'unpaid' COMMENT '一期固定为 unpaid',

    pickup_poi_id        VARCHAR(64)    NULL,
    pickup_name          VARCHAR(100)   NOT NULL,
    pickup_address       VARCHAR(255)   NULL,
    pickup_lat           DECIMAL(10, 6) NOT NULL COMMENT 'GCJ-02 纬度',
    pickup_lng           DECIMAL(10, 6) NOT NULL COMMENT 'GCJ-02 经度',
    destination_poi_id   VARCHAR(64)    NULL,
    destination_name     VARCHAR(100)   NOT NULL,
    destination_address  VARCHAR(255)   NULL,
    destination_lat      DECIMAL(10, 6) NOT NULL COMMENT 'GCJ-02 纬度',
    destination_lng      DECIMAL(10, 6) NOT NULL COMMENT 'GCJ-02 经度',

    estimated_distance_m INT UNSIGNED   NOT NULL COMMENT '高德规划距离',
    estimated_duration_s INT UNSIGNED   NOT NULL COMMENT '高德规划时长',
    billing_distance_m   INT UNSIGNED   NOT NULL COMMENT '一期按下单时高德规划距离计费',
    actual_duration_s    INT UNSIGNED   NULL COMMENT '开始服务至完成的实际时长',
    waiting_duration_s   INT UNSIGNED   NULL COMMENT '司机到达到开始服务的等待时长',
    base_fare            DECIMAL(10, 2) NOT NULL DEFAULT 0,
    distance_fare        DECIMAL(10, 2) NOT NULL DEFAULT 0,
    driving_time_fare    DECIMAL(10, 2) NOT NULL DEFAULT 0,
    waiting_fare         DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_fare           DECIMAL(10, 2) NOT NULL DEFAULT 0,

    created_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_time        DATETIME       NULL,
    driver_arrived_time  DATETIME       NULL,
    service_started_time DATETIME       NULL,
    completed_time       DATETIME       NULL,
    cancelled_time       DATETIME       NULL,
    cancelled_by         VARCHAR(20)    NULL COMMENT 'passenger、driver',
    cancel_reason        VARCHAR(255)   NULL,
    updated_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_ride_order_passenger FOREIGN KEY (passenger_id) REFERENCES passenger (id),
    CONSTRAINT fk_ride_order_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    CONSTRAINT fk_ride_order_car FOREIGN KEY (car_id) REFERENCES car (id),
    CONSTRAINT fk_ride_order_pricing_rule FOREIGN KEY (pricing_rule_id) REFERENCES pricing_rule (id),
    CONSTRAINT uk_ride_order_no UNIQUE (order_no),
    KEY idx_ride_order_passenger_created (passenger_id, created_time),
    KEY idx_ride_order_driver_status_created (driver_id, status, created_time),
    KEY idx_ride_order_status_created (status, created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ride_order_event
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT       NOT NULL,
    event_type    VARCHAR(30)  NOT NULL COMMENT 'CREATE、ACCEPT、ARRIVE、START_SERVICE、COMPLETE、CANCEL',
    from_status   VARCHAR(30)  NULL,
    to_status     VARCHAR(30)  NOT NULL,
    operator_type VARCHAR(20)  NOT NULL COMMENT 'passenger、driver、system、admin',
    operator_id   INT          NULL COMMENT 'system 时为空',
    remark        VARCHAR(255) NULL,
    created_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ride_order_event_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
    KEY idx_ride_order_event_order_time (order_id, created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_fare_item
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT         NOT NULL,
    charge_type   VARCHAR(20)    NOT NULL COMMENT 'BASE、DISTANCE、DRIVING_TIME、WAITING_TIME',
    rule_id       INT            NOT NULL,
    rule_version  INT            NOT NULL,
    tier_id       INT            NULL COMMENT 'BASE 项为空',
    range_start   INT UNSIGNED   NULL COMMENT '规则快照区间起点',
    range_end     INT UNSIGNED   NULL COMMENT '规则快照区间终点',
    actual_usage  INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '参与计费的米数或秒数',
    unit_size     INT UNSIGNED   NULL,
    unit_price    DECIMAL(10, 2) NOT NULL DEFAULT 0,
    charged_units INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT 'CEIL 后的单位数',
    amount        DECIMAL(10, 2) NOT NULL,
    created_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_fare_item_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
    CONSTRAINT fk_order_fare_item_rule FOREIGN KEY (rule_id) REFERENCES pricing_rule (id),
    CONSTRAINT fk_order_fare_item_tier FOREIGN KEY (tier_id) REFERENCES pricing_rule_tier (id),
    KEY idx_order_fare_item_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE driver_location
(
    driver_id       INT PRIMARY KEY,
    active_order_id BIGINT         NULL,
    latitude        DECIMAL(10, 6) NOT NULL COMMENT 'GCJ-02 纬度',
    longitude       DECIMAL(10, 6) NOT NULL COMMENT 'GCJ-02 经度',
    accuracy_m      DECIMAL(8, 2)  NULL COMMENT '定位精度，单位米',
    speed_mps       DECIMAL(8, 2)  NULL,
    bearing         DECIMAL(6, 2)  NULL COMMENT '朝向，0 至 360',
    recorded_time   DATETIME       NOT NULL COMMENT '设备定位时间',
    received_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务端接收时间',
    updated_time    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_driver_location_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    CONSTRAINT fk_driver_location_order FOREIGN KEY (active_order_id) REFERENCES ride_order (id),
    KEY idx_driver_location_active_order (active_order_id),
    KEY idx_driver_location_updated_time (updated_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

评价：
```sql
CREATE TABLE order_rating
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT          NOT NULL COMMENT '被评价订单；一个订单仅一条乘客评价',
    passenger_id INT             NOT NULL COMMENT '评价乘客 user.id，与 ride_order.passenger_id 一致',
    driver_id    INT             NOT NULL COMMENT '被评价司机 user.id，与 ride_order.driver_id 一致',
    rating       TINYINT UNSIGNED NOT NULL COMMENT '1-5 星整数评分，服务层校验范围',
    content      VARCHAR(500)    NULL COMMENT '评价文字，可选',
    created_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价提交时间',
    updated_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间（预留）',
    CONSTRAINT fk_order_rating_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
    CONSTRAINT fk_order_rating_passenger FOREIGN KEY (passenger_id) REFERENCES passenger (id),
    CONSTRAINT fk_order_rating_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    CONSTRAINT uk_order_rating_order UNIQUE (order_id),
    KEY idx_order_rating_driver_created (driver_id, created_time),
    KEY idx_order_rating_passenger_created (passenger_id, created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

存量库升级（2026-08-13 评价系统；新建库已包含上述结构，无需执行）：

```sql
ALTER TABLE driver
    ADD COLUMN rating_sum   DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '累计评分总和，用于精确重算平均分' AFTER rating,
    ADD COLUMN rating_count INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '累计评价次数' AFTER rating_sum;

CREATE TABLE order_rating
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT          NOT NULL COMMENT '被评价订单；一个订单仅一条乘客评价',
    passenger_id INT             NOT NULL COMMENT '评价乘客 user.id，与 ride_order.passenger_id 一致',
    driver_id    INT             NOT NULL COMMENT '被评价司机 user.id，与 ride_order.driver_id 一致',
    rating       TINYINT UNSIGNED NOT NULL COMMENT '1-5 星整数评分，服务层校验范围',
    content      VARCHAR(500)    NULL COMMENT '评价文字，可选',
    created_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价提交时间',
    updated_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间（预留）',
    CONSTRAINT fk_order_rating_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
    CONSTRAINT fk_order_rating_passenger FOREIGN KEY (passenger_id) REFERENCES passenger (id),
    CONSTRAINT fk_order_rating_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    CONSTRAINT uk_order_rating_order UNIQUE (order_id),
    KEY idx_order_rating_driver_created (driver_id, created_time),
    KEY idx_order_rating_passenger_created (passenger_id, created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

默认即时叫车计价规则：

```sql
INSERT INTO pricing_rule
    (name, version, status, is_default, base_fare, included_distance_m, minimum_fare)
VALUES
    ('默认即时叫车规则', 1, 'active', 1, 5.00, 2000, 5.00);

SET @default_pricing_rule_id = LAST_INSERT_ID();

INSERT INTO pricing_rule_tier
    (rule_id, charge_type, range_start, range_end, unit_size, unit_price, rounding_mode, sort_no)
VALUES
    (@default_pricing_rule_id, 'DISTANCE', 0, 2000, 1000, 0.00, 'CEIL', 10),
    (@default_pricing_rule_id, 'DISTANCE', 2000, 12000, 1000, 3.00, 'CEIL', 20),
    (@default_pricing_rule_id, 'DISTANCE', 12000, NULL, 1000, 1.20, 'CEIL', 30),
    (@default_pricing_rule_id, 'DRIVING_TIME', 0, 1800, 60, 0.20, 'CEIL', 10),
    (@default_pricing_rule_id, 'DRIVING_TIME', 1800, NULL, 60, 0.50, 'CEIL', 20),
    (@default_pricing_rule_id, 'WAITING_TIME', 0, 300, 60, 0.00, 'CEIL', 10),
    (@default_pricing_rule_id, 'WAITING_TIME', 300, NULL, 60, 0.50, 'CEIL', 20);
```
