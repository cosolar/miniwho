package cn.minims.who.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.minims.who.entity.Resume;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /** 存储用量要按简历目录归堆，所以只要 id 与属主两列，且必须含回收站里的简历。 */
    @Select("SELECT id, user_id FROM resumes")
    List<Resume> selectOwners();

    @Select("SELECT id FROM resumes WHERE user_id = #{userId}")
    List<Long> selectIdsByUser(@Param("userId") long userId);
}
