package org.mannerth.starxcar.car.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("car")
public class Car {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String licenseNumber;
    private String carNumber;
    private String carType;
    private String brand;
    private String color;
    private String plateProvince;
    private String plateLetter;
    private String plateSerial;
    private String registrationPlace;
    private Integer seatCount;
    private Integer vehicleYear;
    private String vehicleStatus;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;
}