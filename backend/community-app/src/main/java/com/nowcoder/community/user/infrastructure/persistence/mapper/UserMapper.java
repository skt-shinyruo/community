package com.nowcoder.community.user.infrastructure.persistence.mapper;

import com.nowcoder.community.user.infrastructure.persistence.dataobject.UserDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface UserMapper {

    UserDataObject selectById(UUID id);

    UserDataObject selectByIdForUpdate(UUID id);

    UserDataObject selectByName(String username);

    String selectUsernameAuthenticationSubjectDigest(@Param("username") String username);

    UserDataObject selectByEmail(String email);

    int insertUser(UserDataObject user);

    int updateStatus(@Param("id") UUID id, @Param("status") int status, @Param("securityVersion") long securityVersion);

    int updateHeader(UUID id, String headerUrl);

    int updatePassword(@Param("id") UUID id, @Param("password") String password, @Param("securityVersion") long securityVersion);

    int updatePasswordIfSecurityVersion(
            @Param("id") UUID id,
            @Param("password") String password,
            @Param("securityVersion") long securityVersion,
            @Param("expectedSecurityVersion") long expectedSecurityVersion
    );

    int updateType(@Param("id") UUID id, @Param("type") int type, @Param("securityVersion") long securityVersion);

    int updateModerationUntil(
            @Param("id") UUID id,
            @Param("muteUntil") java.util.Date muteUntil,
            @Param("banUntil") java.util.Date banUntil,
            @Param("policyVersion") long policyVersion,
            @Param("securityVersion") long securityVersion,
            @Param("expectedPolicyVersion") long expectedPolicyVersion
    );

    List<UserDataObject> selectModerationUsersAtVersionAfterId(
            @Param("snapshotVersion") long snapshotVersion,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit
    );

    int insertPolicyVersionLog(
            @Param("version") long version,
            @Param("userId") UUID userId,
            @Param("userExists") boolean userExists,
            @Param("muteUntil") java.util.Date muteUntil,
            @Param("banUntil") java.util.Date banUntil
    );

    long selectPolicyVersionCounterForUpdate(@Param("id") int id);

    int updatePolicyVersionCounter(@Param("id") int id, @Param("version") long version);

    long selectPolicyVersionCounter(@Param("id") int id);

    long selectSecurityVersionCounterForUpdate(@Param("id") int id);

    Long selectSecurityVersionById(@Param("id") UUID id);

    int updateSecurityVersionCounter(@Param("id") int id, @Param("version") long version);

    long selectSecurityVersionCounter(@Param("id") int id);

    /**
     * internal 批量用户摘要：用于下游聚合接口避免 N+1 模块调用。
     */
    List<UserDataObject> selectUserSummariesByIds(@Param("ids") List<UUID> ids);

}
