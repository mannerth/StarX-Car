package org.mannerth.starxcar.car.infrastructure;

import org.mannerth.starxcar.car.domain.Car;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;






@Mapper
public interface CarMapper extends BaseMapper<Car> {
}