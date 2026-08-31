package org.mannerth.starxcar.passenger.infrastructure;

import org.mannerth.starxcar.passenger.domain.Passenger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;




@Mapper
public interface PassengerMapper extends BaseMapper<Passenger> {

    @Select("SELECT id, phone_number, phone_tail, display_phone FROM passenger WHERE id = #{passengerId} FOR UPDATE")
    Passenger lockById(Long passengerId);
}