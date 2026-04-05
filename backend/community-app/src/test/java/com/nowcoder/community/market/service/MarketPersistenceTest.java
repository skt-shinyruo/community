package com.nowcoder.community.market.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.entity.MarketAddress;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.entity.MarketOrder;
import com.nowcoder.community.market.entity.MarketShipment;
import com.nowcoder.community.market.mapper.MarketAddressMapper;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import com.nowcoder.community.market.mapper.MarketOrderMapper;
import com.nowcoder.community.market.mapper.MarketShipmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketPersistenceTest {

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
    void insertListingsAndPhysicalShipmentShouldPersistUnifiedGoodsTypeFacts() {
        MarketListing listing = new MarketListing();
        listing.setSellerUserId(7);
        listing.setGoodsType("PHYSICAL");
        listing.setTitle("二手键盘");
        listing.setDescription("九成新");
        listing.setUnitPrice(12_900L);
        listing.setStockTotal(3);
        listing.setStockAvailable(3);
        listing.setMinPurchaseQuantity(1);
        listing.setMaxPurchaseQuantity(1);
        listing.setStatus("ACTIVE");
        marketListingMapper.insert(listing);

        MarketAddress address = new MarketAddress();
        address.setUserId(9);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setProvince("上海市");
        address.setCity("上海市");
        address.setDistrict("浦东新区");
        address.setDetailAddress("世纪大道 100 号");
        address.setPostalCode("200120");
        address.setDefault(true);
        address.setStatus("ACTIVE");
        marketAddressMapper.insert(address);

        MarketOrder order = new MarketOrder();
        order.setRequestId("physical:req-1");
        order.setListingId(listing.getListingId());
        order.setGoodsType("PHYSICAL");
        order.setSellerUserId(7);
        order.setBuyerUserId(9);
        order.setQuantity(1);
        order.setUnitPriceSnapshot(12_900L);
        order.setTotalAmount(12_900L);
        order.setListingTitleSnapshot("二手键盘");
        order.setStatus("SHIPPED");
        order.setReceiverNameSnapshot("张三");
        order.setReceiverPhoneSnapshot("13800000000");
        order.setProvinceSnapshot("上海市");
        order.setCitySnapshot("上海市");
        order.setDistrictSnapshot("浦东新区");
        order.setDetailAddressSnapshot("世纪大道 100 号");
        order.setPostalCodeSnapshot("200120");
        marketOrderMapper.insert(order);

        MarketShipment shipment = new MarketShipment();
        shipment.setOrderId(order.getOrderId());
        shipment.setSellerUserId(7);
        shipment.setCarrierName("顺丰");
        shipment.setTrackingNo("SF1234567890");
        shipment.setShippingRemark("工作日派送");
        marketShipmentMapper.insert(shipment);

        assertThat(marketListingMapper.selectById(listing.getListingId()).getGoodsType()).isEqualTo("PHYSICAL");
        assertThat(marketOrderMapper.selectById(order.getOrderId()).getReceiverNameSnapshot()).isEqualTo("张三");
        assertThat(marketShipmentMapper.selectByOrderId(order.getOrderId()).getTrackingNo()).isEqualTo("SF1234567890");
    }
}
