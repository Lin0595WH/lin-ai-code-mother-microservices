package com.lin.linaicodemother.mapstruct;


import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.vo.AppVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;

/**
 * @Author Lin
 * @Date 2026/1/13 21:37
 * @Descriptions app模块Mapper：管理多个实体→VO的转换
 */
@Mapper(componentModel = "spring",
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL,
        uses = {UserModuleMapper.class}// 引用UserModuleMapper处理User到UserVO
)
public interface AppModuleMapper {
    /**
     * App实体转AppVO（不包含user字段的映射）
     * user字段将在服务层单独设置
     */
    @Mapping(target = "user", ignore = true) // 忽略user字段，由服务层设置
    AppVO appToAppVO(App app);

    /**
     * App列表转AppVO列表
     */
    List<AppVO> appListToAppVOList(List<App> appList);

}
