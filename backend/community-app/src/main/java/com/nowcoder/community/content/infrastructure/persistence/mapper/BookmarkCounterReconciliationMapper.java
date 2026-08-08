package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.infrastructure.persistence.dataobject.BookmarkCounterReconciliationDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BookmarkCounterReconciliationMapper {

    int upsertRevision(@Param("postId") UUID postId);

    List<BookmarkCounterReconciliationDataObject> selectPending(@Param("limit") int limit);

    int clearIfRevision(@Param("postId") UUID postId, @Param("revision") long revision);

    int deferIfRevision(@Param("postId") UUID postId, @Param("revision") long revision);
}
