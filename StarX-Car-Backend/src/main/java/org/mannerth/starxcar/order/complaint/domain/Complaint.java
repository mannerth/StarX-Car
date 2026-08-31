package org.mannerth.starxcar.order.complaint.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("complaint")
public class Complaint {
    @TableId(type = IdType.AUTO) private Long id;
    private String complaintNo;
    private Long orderId;
    private Long complainantUserId;
    private String complaintType;
    private String content;
    private String status;
    private String resolutionType;
    private BigDecimal refundAmount;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
