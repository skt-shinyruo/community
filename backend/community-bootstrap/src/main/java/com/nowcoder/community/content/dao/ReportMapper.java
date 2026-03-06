// 举报表 MyBatis Mapper：提供举报写入、去重查询、分页列表与状态更新。
package com.nowcoder.community.content.dao;

import com.nowcoder.community.content.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    int insertReport(Report report);

    Integer selectReportIdByDedupeKey(
            @Param("reporterId") int reporterId,
            @Param("targetType") int targetType,
            @Param("targetId") int targetId
    );

    Report selectReportById(@Param("id") int id);

    List<Report> selectReports(
            @Param("status") Integer status,
            @Param("targetType") Integer targetType,
            @Param("reporterId") Integer reporterId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int updateStatus(@Param("id") int id, @Param("status") int status);
}

