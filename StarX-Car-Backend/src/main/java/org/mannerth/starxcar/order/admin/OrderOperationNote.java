package org.mannerth.starxcar.order.admin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
