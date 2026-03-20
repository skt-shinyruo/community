// 治理处置表 MyBatis Mapper：用于审计追溯与后台查询。
package com.nowcoder.community.content.mapper;

import com.nowcoder.community.content.entity.ModerationAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModerationActionMapper {

    int insertAction(ModerationAction action);

    List<ModerationAction> selectActionsByReportId(@Param("reportId") int reportId);

    List<ModerationAction> selectActions(
            @Param("actorId") Integer actorId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}

