package org.mannerth.starxcar.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("driver_location")
public class DriverLocation {
    @TableId(type = IdType.INPUT)
    private Long driverId;
    private Long activeOrderId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyM;
    private BigDecimal speedMps;
    private BigDecimal bearing;
    private LocalDateTime recordedTime;
    private LocalDateTime receivedTime;
    private LocalDateTime updatedTime;
}