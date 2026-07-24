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
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

create unique index car_car_number_uindex
    on car (car_number);

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