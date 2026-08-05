package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DriverMapper extends BaseMapper<Driver> {

    @Update("UPDATE driver SET work_status = 'busy' WHERE id = #{driverId} AND work_status = 'online'")
    int markBusyIfOnline(@Param("driverId") Long driverId);

    @Update("UPDATE driver SET work_status = 'online' WHERE id = #{driverId} AND work_status = 'busy'")
    int markOnlineIfBusy(@Param("driverId") Long driverId);
}
