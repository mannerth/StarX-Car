package org.mannerth.starxcar.order.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("order_operation_note")
public class OrderOperationNote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long adminId;
    private String content;
    private LocalDateTime createdTime;
}