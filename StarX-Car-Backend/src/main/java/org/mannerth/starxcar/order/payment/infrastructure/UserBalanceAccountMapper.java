package org.mannerth.starxcar.order.payment.infrastructure;

import org.mannerth.starxcar.order.payment.domain.UserBalanceAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserBalanceAccountMapper extends BaseMapper<UserBalanceAccount> {
    @Insert("INSERT IGNORE INTO user_balance_account (user_id, balance) VALUES (#{userId}, 0.00)")
    int ensureAccount(@Param("userId") Long userId);

    @Select("SELECT * FROM user_balance_account WHERE user_id = #{userId} FOR UPDATE")
    UserBalanceAccount selectByUserIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE user_balance_account SET balance = #{balance}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int updateBalance(@Param("id") Long id, @Param("version") Long version, @Param("balance") BigDecimal balance);
}
