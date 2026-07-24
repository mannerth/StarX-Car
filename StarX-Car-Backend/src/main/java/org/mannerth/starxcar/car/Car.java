package org.mannerth.starxcar.car;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;
}
