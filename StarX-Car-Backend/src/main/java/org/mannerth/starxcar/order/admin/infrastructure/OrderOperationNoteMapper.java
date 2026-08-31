package org.mannerth.starxcar.order.admin.infrastructure;

import org.mannerth.starxcar.order.admin.domain.OrderOperationNote;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;






@Mapper
public interface OrderOperationNoteMapper extends BaseMapper<OrderOperationNote> {
}