package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.dto.MarketAddressResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketAddressServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketAddressService marketAddressService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_order");
        jdbcTemplate.update("delete from market_inventory_unit");
        jdbcTemplate.update("delete from market_address");
        jdbcTemplate.update("delete from market_listing");
    }

    @Test
    void addressCrudShouldKeepOneDefaultAddressPerUser() {
        CreateMarketAddressRequest first = new CreateMarketAddressRequest();
        first.setReceiverName("张三");
        first.setReceiverPhone("13800000000");
        first.setProvince("上海市");
        first.setCity("上海市");
        first.setDistrict("浦东新区");
        first.setDetailAddress("世纪大道 100 号");
        first.setPostalCode("200120");
        first.setDefault(true);

        CreateMarketAddressRequest second = new CreateMarketAddressRequest();
        second.setReceiverName("李四");
        second.setReceiverPhone("13900000000");
        second.setProvince("北京市");
        second.setCity("北京市");
        second.setDistrict("海淀区");
        second.setDetailAddress("中关村大街 1 号");
        second.setPostalCode("100080");
        second.setDefault(true);

        marketAddressService.createAddress(9, first);
        marketAddressService.createAddress(9, second);

        assertThat(marketAddressService.listAddresses(9))
                .filteredOn(MarketAddressResponse::isDefault)
                .hasSize(1)
                .first()
                .extracting(MarketAddressResponse::receiverName)
                .isEqualTo("李四");
    }
}
