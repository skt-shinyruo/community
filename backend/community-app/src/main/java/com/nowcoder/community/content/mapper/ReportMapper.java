// 举报表 MyBatis Mapper：提供举报写入、去重查询、分页列表与状态更新。
package com.nowcoder.community.content.mapper;

import com.nowcoder.community.content.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReportMapper {

    int insertReport(Report report);

    UUID selectReportIdByDedupeKey(
            @Param("reporterId") UUID reporterId,
            @Param("targetType") int targetType,
            @Param("targetId") UUID targetId
    );

    Report selectReportById(@Param("id") UUID id);

    List<Report> selectReports(
            @Param("status") Integer status,
            @Param("targetType") Integer targetType,
            @Param("reporterId") UUID reporterId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int updateStatus(@Param("id") UUID id, @Param("status") int status);
}
