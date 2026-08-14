package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface DriverMapper extends BaseMapper<Driver> {

    @Update("UPDATE driver SET work_status = 'busy' WHERE id = #{driverId} AND work_status = 'online'")
    int markBusyIfOnline(@Param("driverId") Long driverId);

    @Update("UPDATE driver SET work_status = 'online' WHERE id = #{driverId} AND work_status = 'busy'")
    int markOnlineIfBusy(@Param("driverId") Long driverId);

    @Update("""
            UPDATE driver
            SET rating_sum = rating_sum + #{score},
                rating_count = rating_count + 1,
                rating = ROUND(rating_sum / rating_count, 1)
            WHERE id = #{driverId}
            """)
    int addRating(@Param("driverId") Long driverId, @Param("score") int score);

    @Select("SELECT rating, rating_sum, rating_count FROM driver WHERE id = #{driverId}")
    Map<String, Object> selectRatingAggregate(@Param("driverId") Long driverId);
}