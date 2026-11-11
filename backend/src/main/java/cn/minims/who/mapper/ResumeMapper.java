package cn.minims.who.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.minims.who.entity.Resume;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 注销清库要连回收站里的简历一起删：实体的 deleted 列带逻辑删除，走 BaseMapper
     * 只会拿到未删除的那些，盘上的附件与图片就成了没人认领的孤儿。
     * 只取清库真正用得上的列，避免把整份 content 读进内存。
     */
    @Select("SELECT id, user_id, avatar_key, is_sample FROM resumes WHERE user_id = #{userId}")
    List<Resume> selectPurgeColumnsByUser(@Param("userId") long userId);

    @Delete("DELETE FROM resumes WHERE user_id = #{userId}")
    int hardDeleteByUser(@Param("userId") long userId);

    /** 后台回收站需要绕过逻辑删除直接读已删行，连用户信息一起带出来。 */
    @Select("SELECT r.id, r.user_id, r.title, r.template_code, r.deleted_at, r.updated_at,"
            + " u.username, u.nickname FROM resumes r LEFT JOIN users u ON u.id = r.user_id"
            + " WHERE r.deleted = 1 ORDER BY r.deleted_at DESC LIMIT #{limit}")
    List<Map<String, Object>> selectDeleted(@Param("limit") int limit);

    /** 单份已删简历，供后台确认后物理清除。 */
    @Select("SELECT * FROM resumes WHERE id = #{id} AND deleted = 1")
    Resume selectDeletedById(@Param("id") long id);

    /** 物理删除：绕过逻辑删除，把这一行真正从表里抹掉。 */
    @Delete("DELETE FROM resumes WHERE id = #{id}")
    int hardDeleteById(@Param("id") long id);

    /** 存储用量要按简历目录归堆，所以只要 id 与属主两列，且必须含回收站里的简历。 */
    @Select("SELECT id, user_id FROM resumes")
    List<Resume> selectOwners();

    @Select("SELECT id FROM resumes WHERE user_id = #{userId}")
    List<Long> selectIdsByUser(@Param("userId") long userId);
}
