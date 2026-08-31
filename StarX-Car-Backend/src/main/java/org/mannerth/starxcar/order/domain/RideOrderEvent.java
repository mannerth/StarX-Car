package org.mannerth.starxcar.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("ride_order_event")
public class RideOrderEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private OrderEventType eventType;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private OrderOperatorType operatorType;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdTime;
}