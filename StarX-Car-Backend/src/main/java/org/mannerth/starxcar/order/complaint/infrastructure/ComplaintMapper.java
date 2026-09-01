package org.mannerth.starxcar.order.complaint.infrastructure;

import org.mannerth.starxcar.order.complaint.domain.Complaint;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {
    @Select("SELECT * FROM complaint WHERE complaint_no = #{complaintNo} FOR UPDATE")
    Complaint selectByComplaintNoForUpdate(String complaintNo);

    @Update("UPDATE complaint SET status = 'RESOLVED', resolution_type = #{resolutionType}, refund_amount = #{refundAmount}, resolved_by = #{adminId}, resolved_time = #{resolvedTime} WHERE id = #{id} AND status IN ('OPEN', 'PROCESSING')")
    int resolve(Long id, String resolutionType, java.math.BigDecimal refundAmount, Long adminId, java.time.LocalDateTime resolvedTime);

    @Insert("INSERT INTO complaint_handle_log (complaint_id, admin_id, action, content, refund_amount, created_time) VALUES (#{complaintId}, #{adminId}, #{action}, #{content}, #{refundAmount}, #{createdTime})")
    int insertHandleLog(Long complaintId, Long adminId, String action, String content,
                        java.math.BigDecimal refundAmount, java.time.LocalDateTime createdTime);

    @Select("SELECT content FROM complaint_handle_log WHERE complaint_id = #{complaintId} ORDER BY created_time DESC, id DESC LIMIT 1")
    String selectLatestHandleContent(Long complaintId);
}
