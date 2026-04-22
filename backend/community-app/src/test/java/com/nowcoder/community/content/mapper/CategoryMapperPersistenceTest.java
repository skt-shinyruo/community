package com.nowcoder.community.content.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class CategoryMapperPersistenceTest {

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-7000-8000-000000000301");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryMapper categoryMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_subscription_category");
        jdbcTemplate.update("delete from discuss_post");
        jdbcTemplate.update("delete from category");
    }

    @Test
    void selectCategoryShouldDecodeBinaryUuidPrimaryKey() {
        jdbcTemplate.update(
                "insert into category(id, name, description, position, create_time) values (?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(CATEGORY_ID),
                "公告",
                "官方公告",
                1,
                Timestamp.from(Instant.parse("2026-04-21T00:00:00Z"))
        );

        Category category = categoryMapper.selectCategoryById(CATEGORY_ID);
        assertThat(category).isNotNull();
        assertThat(category.getId()).isEqualTo(CATEGORY_ID);

        List<Category> categories = categoryMapper.selectCategories();
        assertThat(categories).extracting(Category::getId).containsExactly(CATEGORY_ID);
    }
}
