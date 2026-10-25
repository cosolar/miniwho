package cn.minims.who.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 单行计数器：只读写 site_stats，不引入实体，避免被 MyBatis-Plus 的自增主键策略影响。
 */
@Mapper
public interface SiteStatsMapper {

    @Select("SELECT stat_value FROM site_stats WHERE stat_key = #{key}")
    Long selectValue(@Param("key") String key);

    @Update("UPDATE site_stats SET stat_value = stat_value + 1, updated_at = #{now} WHERE stat_key = #{key}")
    int increment(@Param("key") String key, @Param("now") long now);

    @Insert("INSERT INTO site_stats (stat_key, stat_value, updated_at) VALUES (#{key}, 1, #{now})")
    int insertFirst(@Param("key") String key, @Param("now") long now);
}
