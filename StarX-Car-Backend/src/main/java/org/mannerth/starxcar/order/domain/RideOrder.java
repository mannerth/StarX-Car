package org.mannerth.starxcar.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("ride_order")
public class RideOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private OrderType orderType;
    private Long passengerId;
    private Long driverId;
    private Long carId;
    private Long pricingRuleId;
    private Integer pricingRuleVersion;
    private OrderStatus status;
    private Long version;
    private String paymentStatus;
    private String pickupPoiId;
    private String pickupName;
    private String pickupAddress;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String destinationPoiId;
    private String destinationName;
    private String destinationAddress;
    private BigDecimal destinationLat;
    private BigDecimal destinationLng;
    private LocalDateTime scheduledPickupTime;
    private Long estimatedDistanceM;
    private Long estimatedDurationS;
    private Long billingDistanceM;
    private Long actualDurationS;
    private Long waitingDurationS;
    private BigDecimal baseFare;
    private BigDecimal distanceFare;
    private BigDecimal drivingTimeFare;
    private BigDecimal waitingFare;
    private BigDecimal totalFare;
    private LocalDateTime createdTime;
    private LocalDateTime dispatchTime;
    private LocalDateTime acceptedTime;
    private LocalDateTime driverArrivedTime;
    private LocalDateTime serviceStartedTime;
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;
    private String cancelledBy;
    private String cancelReason;
    private LocalDateTime updatedTime;
}