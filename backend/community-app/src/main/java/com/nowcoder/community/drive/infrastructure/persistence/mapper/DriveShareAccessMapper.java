package com.nowcoder.community.drive.infrastructure.persistence.mapper;

import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveShareAccessDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface DriveShareAccessMapper {

    int insert(DriveShareAccessDataObject access);
}
