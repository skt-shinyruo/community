package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface PostCounterSnapshotMapper {

    PostCounterSnapshot selectByPostId(@Param("postId") UUID postId);

    int upsertCounterSnapshot(
            @Param("postId") UUID postId,
            @Param("viewCount") long viewCount,
            @Param("likeCount") long likeCount,
            @Param("commentCount") long commentCount,
            @Param("bookmarkCount") long bookmarkCount,
            @Param("revision") long revision
    );

    int upsertScoreSnapshot(
            @Param("postId") UUID postId,
            @Param("score") double score,
            @Param("rankVersion") String rankVersion,
            @Param("revision") long revision
    );
}
