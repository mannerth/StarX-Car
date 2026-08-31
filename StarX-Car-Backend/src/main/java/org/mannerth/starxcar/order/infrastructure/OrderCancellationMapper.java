package org.mannerth.starxcar.order.infrastructure;

import org.mannerth.starxcar.order.domain.OrderCancellation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderCancellationMapper extends BaseMapper<OrderCancellation> {
}
