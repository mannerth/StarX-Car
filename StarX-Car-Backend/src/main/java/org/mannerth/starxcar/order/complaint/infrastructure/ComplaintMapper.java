package org.mannerth.starxcar.order.complaint.infrastructure;

import org.mannerth.starxcar.order.complaint.domain.Complaint;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {
    @Select("SELECT * FROM complaint WHERE complaint_no = #{complaintNo} FOR UPDATE")
    Complaint selectByComplaintNoForUpdate(String complaintNo);

    @Update("UPDATE complaint SET status = 'RESOLVED', resolution_type = #{resolutionType}, refund_amount = #{refundAmount}, resolved_by = #{adminId}, resolved_time = #{resolvedTime} WHERE id = #{id} AND status IN ('OPEN', 'PROCESSING')")
    int resolve(Long id, String resolutionType, java.math.BigDecimal refundAmount, Long adminId, java.time.LocalDateTime resolvedTime);
}
