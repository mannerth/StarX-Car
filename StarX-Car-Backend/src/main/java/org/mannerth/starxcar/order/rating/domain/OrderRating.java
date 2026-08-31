package org.mannerth.starxcar.order.rating.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("order_rating")
public class OrderRating {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long passengerId;
    private Long driverId;
    private Integer rating;
    private String content;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}