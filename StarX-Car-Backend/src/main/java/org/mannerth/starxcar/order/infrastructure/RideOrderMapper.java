package org.mannerth.starxcar.order.infrastructure;

import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.RideOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;






@Mapper
public interface RideOrderMapper extends BaseMapper<RideOrder> {

    @Update("""
            UPDATE ride_order
            SET driver_id = #{driverId}, car_id = #{carId}, status = 'ACCEPTED',
                accepted_time = #{acceptedTime}, version = version + 1
            WHERE id = #{orderId} AND status = 'PENDING' AND driver_id IS NULL AND version = #{version}
            """)
    int acceptPendingOrder(@Param("orderId") Long orderId, @Param("driverId") Long driverId,
                           @Param("carId") Long carId, @Param("acceptedTime") LocalDateTime acceptedTime,
                           @Param("version") Long version);

    @Update("""
            UPDATE ride_order
            SET status = 'PENDING', dispatch_time = #{dispatchTime}, version = version + 1
            WHERE id = #{orderId} AND status = 'SCHEDULED' AND order_type = 'SCHEDULED' AND version = #{version}
            """)
    int dispatchReservation(@Param("orderId") Long orderId, @Param("version") Long version,
                            @Param("dispatchTime") LocalDateTime dispatchTime);

    @Update("""
            UPDATE ride_order
            SET status = 'CANCELLED', cancelled_time = #{cancelledTime}, cancelled_by = #{cancelledBy},
                cancel_reason = #{cancelReason}, version = version + 1
            WHERE id = #{orderId} AND status = #{expectedStatus} AND version = #{version}
            """)
    int cancelOrder(@Param("orderId") Long orderId, @Param("expectedStatus") OrderStatus expectedStatus,
                    @Param("version") Long version, @Param("cancelledTime") LocalDateTime cancelledTime,
                    @Param("cancelledBy") String cancelledBy, @Param("cancelReason") String cancelReason);

    @Update("""
            UPDATE ride_order
            SET status = 'DRIVER_ARRIVED', driver_arrived_time = #{arrivedTime}, version = version + 1
            WHERE id = #{orderId} AND status = 'ACCEPTED' AND driver_id = #{driverId} AND version = #{version}
            """)
    int markDriverArrived(@Param("orderId") Long orderId, @Param("driverId") Long driverId,
                           @Param("version") Long version, @Param("arrivedTime") LocalDateTime arrivedTime);

    @Update("""
            UPDATE ride_order
            SET status = 'IN_SERVICE', service_started_time = #{startedTime}, waiting_duration_s = #{waitingDurationS},
                version = version + 1
            WHERE id = #{orderId} AND status = 'DRIVER_ARRIVED' AND driver_id = #{driverId} AND version = #{version}
            """)
    int startService(@Param("orderId") Long orderId, @Param("driverId") Long driverId,
                     @Param("version") Long version, @Param("startedTime") LocalDateTime startedTime,
                     @Param("waitingDurationS") Long waitingDurationS);

    @Update("""
            UPDATE ride_order
            SET status = 'COMPLETED', completed_time = #{completedTime}, actual_duration_s = #{actualDurationS},
                base_fare = #{baseFare}, distance_fare = #{distanceFare}, driving_time_fare = #{drivingTimeFare},
                waiting_fare = #{waitingFare}, total_fare = #{totalFare}, version = version + 1
            WHERE id = #{orderId} AND status = 'IN_SERVICE' AND driver_id = #{driverId} AND version = #{version}
            """)
    int completeOrder(@Param("orderId") Long orderId, @Param("driverId") Long driverId,
                      @Param("version") Long version, @Param("completedTime") LocalDateTime completedTime,
                      @Param("actualDurationS") Long actualDurationS, @Param("baseFare") BigDecimal baseFare,
                      @Param("distanceFare") BigDecimal distanceFare,
                      @Param("drivingTimeFare") BigDecimal drivingTimeFare,
                      @Param("waitingFare") BigDecimal waitingFare, @Param("totalFare") BigDecimal totalFare);
}