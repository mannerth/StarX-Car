package org.mannerth.starxcar.order.admin.infrastructure;

import org.mannerth.starxcar.order.admin.domain.AdminOperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;






@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {
}