package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface DriverLocationMapper extends BaseMapper<DriverLocation> {

    @Insert("""
            INSERT INTO driver_location
                (driver_id, active_order_id, latitude, longitude, accuracy_m, speed_mps, bearing, recorded_time, received_time)
            VALUES
                (#{location.driverId}, NULL, #{location.latitude}, #{location.longitude}, #{location.accuracyM},
                 #{location.speedMps}, #{location.bearing}, #{location.recordedTime}, #{location.receivedTime})
            ON DUPLICATE KEY UPDATE
                latitude = VALUES(latitude), longitude = VALUES(longitude), accuracy_m = VALUES(accuracy_m),
                speed_mps = VALUES(speed_mps), bearing = VALUES(bearing), recorded_time = VALUES(recorded_time),
                received_time = VALUES(received_time)
            """)
    int upsertLatestLocation(@Param("location") DriverLocation location);

    @Update("""
            UPDATE driver_location
            SET active_order_id = #{orderId}
            WHERE driver_id = #{driverId} AND active_order_id IS NULL AND received_time >= #{freshSince}
            """)
    int attachActiveOrder(@Param("driverId") Long driverId, @Param("orderId") Long orderId,
                          @Param("freshSince") LocalDateTime freshSince);

    @Update("UPDATE driver_location SET active_order_id = NULL WHERE driver_id = #{driverId} AND active_order_id = #{orderId}")
    int clearActiveOrder(@Param("driverId") Long driverId, @Param("orderId") Long orderId);
}
