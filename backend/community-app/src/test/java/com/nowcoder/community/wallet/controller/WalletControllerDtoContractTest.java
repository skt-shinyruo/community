package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService.WalletSummaryResult;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService.RechargeOrderResult;
import com.nowcoder.community.wallet.application.WalletTestCreditCapabilityApplicationService.WalletCapabilitiesResult;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService.TransferOrderResult;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService.WithdrawOrderResult;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.controller.dto.AdminFreezeWalletRequest;
import com.nowcoder.community.wallet.controller.dto.AdminReverseTxnRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class WalletControllerDtoContractTest {

    private final ObjectMapper objectMapper = JacksonJsonCodec.standardMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void directApplicationResultsShouldPreserveWalletResponseJsonFields() {
        JsonNode summary = objectMapper.valueToTree(new WalletSummaryResult(uuid(1), 2300L, "ACTIVE"));
        JsonNode recharge = objectMapper.valueToTree(
                new RechargeOrderResult(uuid(11), "recharge:1", uuid(1), 1200L, "PAID")
        );
        JsonNode withdraw = objectMapper.valueToTree(
                new WithdrawOrderResult(uuid(12), "withdraw:1", uuid(1), 500L, "SUCCEEDED")
        );
        JsonNode transfer = objectMapper.valueToTree(
                new TransferOrderResult(uuid(13), "transfer:1", uuid(1), uuid(2), 300L, "SUCCEEDED")
        );

        assertThat(fieldNames(summary)).containsExactlyInAnyOrder("userId", "balance", "status");
        assertThat(fieldNames(recharge)).containsExactlyInAnyOrder(
                "orderId", "requestId", "userId", "amount", "status"
        );
        assertThat(fieldNames(withdraw)).containsExactlyInAnyOrder(
                "orderId", "requestId", "userId", "amount", "status"
        );
        assertThat(fieldNames(transfer)).containsExactlyInAnyOrder(
                "orderId", "requestId", "fromUserId", "toUserId", "amount", "status"
        );
    }

    @Test
    void directTransactionResultShouldPreserveWalletResponseJsonFields() {
        JsonNode transaction = objectMapper.valueToTree(new WalletTransactionResult(
                uuid(21),
                "transfer:1",
                "TRANSFER",
                "TRANSFER",
                "order:1",
                "SUCCEEDED",
                -300L,
                700L,
                "user-2",
                "test",
                new Date(1234L)
        ));

        assertThat(fieldNames(transaction)).containsExactlyInAnyOrder(
                "txnId",
                "txnRef",
                "txnType",
                "bizType",
                "bizId",
                "status",
                "amount",
                "balanceAfter",
                "counterpartLabel",
                "remark",
                "createTime"
        );
    }

    @Test
    void nestedCapabilitiesShouldPreserveWalletResponseJsonFields() {
        WalletCapabilitiesResult.Action action =
                new WalletCapabilitiesResult.Action(true, 1000L, 5000L, 1200L, 3800L);
        JsonNode json = objectMapper.valueToTree(new WalletCapabilitiesResult(
                "INTERNAL_TEST_CREDIT",
                false,
                false,
                new WalletCapabilitiesResult.TestCredits(true, action, action)
        ));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "balanceUnit", "realPaymentsSupported", "realPayoutsSupported", "testCredits"
        );
        assertThat(fieldNames(json.path("testCredits"))).containsExactlyInAnyOrder(
                "enabled", "grant", "discard"
        );
        assertThat(fieldNames(json.path("testCredits").path("grant"))).containsExactlyInAnyOrder(
                "enabled", "maxAmountPerRequest", "totalQuota", "usedAmount", "remainingAmount"
        );
    }

    @Test
    void adminRequestRecordsShouldPreserveJsonAndValidationContract() throws Exception {
        AdminFreezeWalletRequest freeze = objectMapper.readValue(
                "{\"userId\":\"00000000-0000-7000-8000-000000000001\",\"reason\":\"risk\",\"unknown\":true}",
                AdminFreezeWalletRequest.class
        );
        AdminReverseTxnRequest reverse = objectMapper.readValue(
                "{\"txnRef\":\"transfer:1\",\"reason\":\"fraud\",\"unknown\":true}",
                AdminReverseTxnRequest.class
        );

        assertThat(freeze).isEqualTo(new AdminFreezeWalletRequest(uuid(1), "risk"));
        assertThat(reverse).isEqualTo(new AdminReverseTxnRequest("transfer:1", "fraud"));
        assertThat(objectMapper.valueToTree(freeze).fieldNames()).toIterable()
                .containsExactly("userId", "reason");
        assertThat(objectMapper.valueToTree(reverse).fieldNames()).toIterable()
                .containsExactly("txnRef", "reason");

        assertThat(validator.validate(new AdminFreezeWalletRequest(null, "")))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("userId", "reason");
        assertThat(validator.validate(new AdminReverseTxnRequest("", "")))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("txnRef", "reason");
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
