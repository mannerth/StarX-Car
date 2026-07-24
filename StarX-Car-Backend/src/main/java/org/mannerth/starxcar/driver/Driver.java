package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("driver")
public class Driver {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String phoneNumber;
    private String licenseNumber;
    private BigDecimal rating;
    private Long carId;
    private String displayPhone;
}
