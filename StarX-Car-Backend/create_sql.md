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
CREATE TABLE Admin
(
    id INT PRIMARY KEY,                          -- 与 User.id 对应
    CONSTRAINT fk_admin_user FOREIGN KEY (id) REFERENCES User(id)
);
```

```sql
CREATE TABLE Car
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
CREATE TABLE Passenger
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
    rating           DECIMAL(2,1) NOT NULL DEFAULT 5.0,
    car_id           INT,
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
    driver_id    INT NOT NULL, -- 司机用户ID
    car_id       INT NOT NULL, -- 车辆ID
    bind_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unbind_time  DATETIME NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'active', -- active、inactive
    CONSTRAINT fk_binding_driver FOREIGN KEY (driver_id) REFERENCES driver(id),
    CONSTRAINT fk_binding_car FOREIGN KEY (car_id) REFERENCES car(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_binding_driver_status
    ON driver_car_binding (driver_id, status);

CREATE INDEX idx_binding_car_status
    ON driver_car_binding (car_id, status);
```
