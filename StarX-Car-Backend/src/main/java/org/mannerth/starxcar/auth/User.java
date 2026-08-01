package org.mannerth.starxcar.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.mannerth.starxcar.constant.GenderEnum;
import org.mannerth.starxcar.constant.UserType;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(
            value = "id",
            type = IdType.AUTO
    )
    private Long id;

    private String username;
    private String password;
    private String email;
    private UserType type;
    private GenderEnum gender;
    private String realName;
    private String avatarUrl;
    private String status;

    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
    private LocalDateTime updateTime;
}
