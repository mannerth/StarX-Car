package org.mannerth.starxcar.order.admin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.mannerth.starxcar.order.OrderStatus;

import java.time.LocalDateTime;

@Data
@TableName("admin_operation_log")
public class AdminOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private Long orderId;
    private OrderStatus beforeStatus;
    private OrderStatus afterStatus;
    private String reason;
    private String traceId;
    private LocalDateTime createdTime;
}
