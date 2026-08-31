package org.mannerth.starxcar.passenger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;




@Data
@TableName("passenger")
public class Passenger {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String phoneNumber;
    private String phoneTail;
    private String displayPhone;
}