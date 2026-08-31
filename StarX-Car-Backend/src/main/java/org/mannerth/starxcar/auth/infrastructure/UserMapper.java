package org.mannerth.starxcar.auth.infrastructure;

import org.mannerth.starxcar.auth.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;






@Mapper
public interface UserMapper extends BaseMapper<User> {

}