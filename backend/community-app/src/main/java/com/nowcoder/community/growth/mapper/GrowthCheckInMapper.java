package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.GrowthCheckIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@Mapper
public interface GrowthCheckInMapper {

    GrowthCheckIn selectByUserAndDate(@Param("userId") int userId, @Param("bizDate") LocalDate bizDate);

    GrowthCheckIn selectByUserAndDateForUpdate(@Param("userId") int userId, @Param("bizDate") LocalDate bizDate);

    GrowthCheckIn selectLatestByUserId(int userId);

    GrowthCheckIn selectLatestBeforeDate(@Param("userId") int userId, @Param("bizDate") LocalDate bizDate);

    GrowthCheckIn selectLatestBeforeDateForUpdate(@Param("userId") int userId, @Param("bizDate") LocalDate bizDate);

    int insert(@Param("userId") int userId, @Param("bizDate") LocalDate bizDate, @Param("streakCount") int streakCount);

    int countByUserId(int userId);

    Integer maxStreakByUserId(int userId);

    List<LocalDate> selectBizDatesBetween(@Param("userId") int userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
