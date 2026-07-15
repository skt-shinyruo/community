package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketAddress;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketShipment;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketAddressDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketShipmentDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketAddressMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketListingMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketShipmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketPersistenceTest {

    private static final UuidV7Generator ID_GENERATOR = new UuidV7Generator();

    @Autowired
    private MarketListingMapper marketListingMapper;

    @Autowired
    private MarketAddressMapper marketAddressMapper;

    @Autowired
    private MarketOrderMapper marketOrderMapper;

    @Autowired
    private MarketShipmentMapper marketShipmentMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @Test
    void insertListingsAndPhysicalShipmentShouldPersistUnifiedGoodsTypeFacts() throws Exception {
        UUID sellerUserId = uuid(7);
        UUID buyerUserId = uuid(9);
        MarketListing listing = new MarketListing();
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setListingId(ID_GENERATOR.next());
        listing.setUnitPrice(12_900L);
        listing.setStockTotal(3);
        listing.setStockAvailable(3);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("ACTIVE");
        marketListingMapper.insert(MarketListingDataObject.from(listing));

        MarketAddress address = new MarketAddress();
        address.setUserId(buyerUserId);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setProvince("上海市");
        address.setCity("上海市");
        address.setAddressId(ID_GENERATOR.next());
        address.setDistrict("浦东新区");
        address.setDetailAddress("世纪大道 100 号");
        address.setPostalCode("200120");
        address.setDefault(true);
        address.setStatus("ACTIVE");
        marketAddressMapper.insert(MarketAddressDataObject.from(address));

        MarketOrder order = order(ID_GENERATOR.next())
                .requestId("physical:req-1")
                .listingId(listing.getListingId())
                .goodsType("PHYSICAL")
                .sellerUserId(sellerUserId)
                .buyerUserId(buyerUserId)
                .quantity(1)
                .unitPriceSnapshot(12_900L)
                .totalAmount(12_900L)
                .deliveryModeSnapshot(null)
                .listingTitleSnapshot("二手键盘")
                .status("SHIPPED")
                .addressSnapshot(
                        null,
                        "张三",
                        "13800000000",
                        "上海市",
                        "上海市",
                        "浦东新区",
                        "世纪大道 100 号",
                        "200120"
                )
                .build();
        marketOrderMapper.insert(MarketOrderDataObject.from(order));

        MarketShipment shipment = new MarketShipment();
        shipment.setShipmentId(ID_GENERATOR.next());
        shipment.setOrderId(order.getOrderId());
        shipment.setSellerUserId(sellerUserId);
        shipment.setCarrierName("顺丰");
        shipment.setTrackingNo("SF1234567890");
        shipment.setShippingRemark("工作日派送");
        marketShipmentMapper.insert(MarketShipmentDataObject.from(shipment));

        assertThat(marketListingMapper.selectById(listing.getListingId()).getGoodsType()).isEqualTo("PHYSICAL");
        assertThat(marketOrderMapper.selectById(order.getOrderId()).getReceiverNameSnapshot()).isEqualTo("张三");
        assertThat(marketShipmentMapper.selectByOrderId(order.getOrderId()).getTrackingNo()).isEqualTo("SF1234567890");

        assertUuidProperty(MarketListing.class.getMethod("getListingId").invoke(listing));
        assertUuidProperty(MarketAddress.class.getMethod("getAddressId").invoke(address));
        assertUuidProperty(MarketOrder.class.getMethod("getOrderId").invoke(order));
        assertUuidProperty(MarketShipment.class.getMethod("getShipmentId").invoke(shipment));
    }

    private void assertUuidProperty(Object value) {
        assertThat(value).isInstanceOf(UUID.class);
        assertThat(((UUID) value).version()).isEqualTo(7);
    }
}
