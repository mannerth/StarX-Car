package org.mannerth.starxcar.driver.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;






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
    private String idCardNo;
    private LocalDate licenseExpireDate;
    private String approvalStatus;
    private String workStatus;
}