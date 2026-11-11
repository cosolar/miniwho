package cn.minims.who.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.minims.who.entity.AuditLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /** 按时间上限清理：before 为 0 时清空全部。 */
    @Delete("<script>DELETE FROM audit_logs"
            + "<if test='before > 0'> WHERE created_at &lt; #{before}</if>"
            + "</script>")
    int purgeBefore(@Param("before") long before);
}
