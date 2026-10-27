package cn.minims.who.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.minims.who.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
}
