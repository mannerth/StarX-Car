package org.mannerth.starxcar.order.payment.infrastructure;

import org.mannerth.starxcar.order.payment.domain.BalanceTransaction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BalanceTransactionMapper extends BaseMapper<BalanceTransaction> {
}
