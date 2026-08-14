package com.nowcoder.community.im.core.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class ImCoreTestDatabaseCleaner {

    private ImCoreTestDatabaseCleaner() {
    }

    public static void cleanPrivateMessages(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from im_user_conversation_inbox");
        jdbcTemplate.update("delete from im_conversation_read_state");
        jdbcTemplate.update("delete from im_private_message");
        jdbcTemplate.update("delete from im_conversation");
    }

    public static void cleanAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from im_user_conversation_inbox");
        jdbcTemplate.update("delete from im_user_room_inbox");
        jdbcTemplate.update("delete from im_conversation_read_state");
        jdbcTemplate.update("delete from im_room_read_state");
        jdbcTemplate.update("delete from im_private_message");
        jdbcTemplate.update("delete from im_room_message");
        jdbcTemplate.update("delete from im_membership_version_log");
        jdbcTemplate.update("delete from im_room_member");
        jdbcTemplate.update("delete from im_room");
        jdbcTemplate.update("delete from im_conversation");
        jdbcTemplate.update("update im_membership_version_counter set current_version = 0 where id = 1");
    }
}
