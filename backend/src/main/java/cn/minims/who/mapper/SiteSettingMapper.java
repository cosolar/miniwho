package cn.minims.who.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 站点开关读写：只三条固定语句，不引实体，避免 MyBatis-Plus 的自增主键策略插进一张主键是字符串的表。
 */
@Mapper
public interface SiteSettingMapper {

    @Select("SELECT setting_value FROM site_settings WHERE setting_key = #{key}")
    String selectValue(@Param("key") String key);

    @Update("UPDATE site_settings SET setting_value = #{value}, updated_at = #{now} WHERE setting_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value, @Param("now") long now);

    @Insert("INSERT INTO site_settings (setting_key, setting_value, updated_at) VALUES (#{key}, #{value}, #{now})")
    int insertValue(@Param("key") String key, @Param("value") String value, @Param("now") long now);
}
