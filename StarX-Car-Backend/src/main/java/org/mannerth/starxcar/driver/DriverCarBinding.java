package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("driver_car_binding")
public class DriverCarBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long driverId;
    private Long carId;
    private LocalDateTime bindTime;
    private LocalDateTime unbindTime;
    private String status;
}
