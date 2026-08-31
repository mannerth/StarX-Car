package org.mannerth.starxcar.order.payment.infrastructure;

import org.mannerth.starxcar.order.payment.domain.OrderPayment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderPaymentMapper extends BaseMapper<OrderPayment> {
    @Select("SELECT * FROM order_payment WHERE order_id = #{orderId} FOR UPDATE")
    OrderPayment selectByOrderIdForUpdate(Long orderId);

    @Update("UPDATE order_payment SET refunded_amount = #{refundedAmount}, status = #{status}, completed_time = #{completedTime}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int updateRefund(Long id, Long version, java.math.BigDecimal refundedAmount, String status, java.time.LocalDateTime completedTime);
}
