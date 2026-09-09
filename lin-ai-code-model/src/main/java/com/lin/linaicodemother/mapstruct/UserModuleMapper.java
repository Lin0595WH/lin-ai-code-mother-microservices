package com.lin.linaicodemother.mapstruct;


import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.vo.LoginUserVO;
import com.lin.linaicodemother.model.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;

/**
 * @Author Lin
 * @Date 2026/1/5 20:53
 * @Descriptions 通用用户模块Mapper：管理多个实体→VO的转换
 */
@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface UserModuleMapper {

    /**
     * 单个 User 转 UserVO
     * （字段名完全一致时，MapStruct 会自动映射，无需手动指定）
     */
    UserVO userToUserVO(User user);

    /**
     * 批量 User 转 UserVO 列表
     * （MapStruct 会自动复用上面的单个转换逻辑）
     */
    List<UserVO> userListToUserVOList(List<User> userList);

    /**
     * 单个 User 转 LoginUserVO
     * （字段名完全一致时，MapStruct 会自动映射，无需手动指定）
     */
    LoginUserVO userToLoginUserVO(User user);

}
