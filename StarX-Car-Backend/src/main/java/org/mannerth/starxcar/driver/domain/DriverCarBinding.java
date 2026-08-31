package org.mannerth.starxcar.driver.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;






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