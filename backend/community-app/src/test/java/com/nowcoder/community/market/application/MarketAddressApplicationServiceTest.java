package com.nowcoder.community.market.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.controller.dto.CreateMarketAddressRequest;
import com.nowcoder.community.market.application.result.MarketAddressResult;
import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketAddressApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketAddressApplicationService marketAddressService;

    @Autowired
    private MarketAddressRepository marketAddressRepository;

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
        var userId = uuid(9);
        CreateMarketAddressRequest first = new CreateMarketAddressRequest();
        first.setReceiverName("张三");
        first.setReceiverPhone("13800000000");
        first.setProvince("上海市");
        first.setCity("上海市");
        first.setDistrict("浦东新区");
        first.setDetailAddress("世纪大道 100 号");
        first.setPostalCode("200120");
        first.setDefaultAddress(true);

        CreateMarketAddressRequest second = new CreateMarketAddressRequest();
        second.setReceiverName("李四");
        second.setReceiverPhone("13900000000");
        second.setProvince("北京市");
        second.setCity("北京市");
        second.setDistrict("海淀区");
        second.setDetailAddress("中关村大街 1 号");
        second.setPostalCode("100080");
        second.setDefaultAddress(true);

        marketAddressService.createAddress(MarketTestCommands.addressCommand(userId, first));
        marketAddressService.createAddress(MarketTestCommands.addressCommand(userId, second));

        assertThat(marketAddressService.listAddresses(userId))
                .filteredOn(MarketAddressResult::isDefault)
                .hasSize(1)
                .first()
                .extracting(MarketAddressResult::receiverName)
                .isEqualTo("李四");
    }

    @Test
    void databaseShouldRejectASecondActiveDefaultAddressForTheSameUser() {
        UUID userId = uuid(9);
        MarketAddress first = address(uuid(91), userId, "first", true);
        MarketAddress second = address(uuid(92), userId, "second", true);

        assertThat(marketAddressRepository.save(first))
                .isEqualTo(MarketAddressRepository.WriteResult.APPLIED);
        assertThat(marketAddressRepository.save(second))
                .isEqualTo(MarketAddressRepository.WriteResult.DEFAULT_CONFLICT);

        Integer activeDefaults = jdbcTemplate.queryForObject(
                "select count(*) from market_address where user_id = ? and status = 'ACTIVE' and is_default = true",
                Integer.class,
                userId
        );
        assertThat(activeDefaults).isOne();
    }

    @Test
    void defaultConstraintConflictShouldMapToMarketConflictError() {
        MarketAddressRepository repository = mock(MarketAddressRepository.class);
        when(repository.save(any(MarketAddress.class)))
                .thenReturn(MarketAddressRepository.WriteResult.DEFAULT_CONFLICT);
        MarketAddressApplicationService service = new MarketAddressApplicationService(
                repository,
                new UuidV7Generator()
        );
        CreateMarketAddressRequest request = addressRequest("conflicting");

        assertThatThrownBy(() -> service.createAddress(MarketTestCommands.addressCommand(uuid(9), request)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.DEFAULT_ADDRESS_CONFLICT));
    }

    @Test
    void createAddressShouldRejectNullCommand() {
        assertThatThrownBy(() -> marketAddressService.createAddress(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void updateAddressShouldRejectNullCommand() {
        assertThatThrownBy(() -> marketAddressService.updateAddress(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    private MarketAddress address(UUID addressId, UUID userId, String receiverName, boolean defaultAddress) {
        MarketAddress address = new MarketAddress();
        address.setAddressId(addressId);
        address.setUserId(userId);
        address.setReceiverName(receiverName);
        address.setReceiverPhone("13800000000");
        address.setProvince("province");
        address.setCity("city");
        address.setDistrict("district");
        address.setDetailAddress("detail");
        address.setDefault(defaultAddress);
        address.setStatus("ACTIVE");
        return address;
    }

    private CreateMarketAddressRequest addressRequest(String receiverName) {
        CreateMarketAddressRequest request = new CreateMarketAddressRequest();
        request.setReceiverName(receiverName);
        request.setReceiverPhone("13800000000");
        request.setProvince("province");
        request.setCity("city");
        request.setDistrict("district");
        request.setDetailAddress("detail");
        request.setDefaultAddress(true);
        return request;
    }
}
