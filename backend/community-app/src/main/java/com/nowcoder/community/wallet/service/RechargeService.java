package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.entity.RechargeOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.RechargeOrderMapper;
import com.nowcoder.community.wallet.model.RechargeOrderResult;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RechargeService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final WalletAccountService accountService;
    private final WalletLedgerService ledgerService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public RechargeService(RechargeOrderMapper rechargeOrderMapper,
                           WalletAccountService accountService,
                           WalletLedgerService ledgerService) {
        this(rechargeOrderMapper, accountService, ledgerService, new UuidV7Generator());
    }

    RechargeService(RechargeOrderMapper rechargeOrderMapper,
                    WalletAccountService accountService,
                    WalletLedgerService ledgerService,
                    UuidV7Generator idGenerator) {
        this.rechargeOrderMapper = rechargeOrderMapper;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public RechargeOrderResult complete(String requestId, UUID userId, long amount) {
        validate(requestId, amount);

        RechargeOrder existing = rechargeOrderMapper.selectByRequestId(requestId);
        if (existing != null) {
            ensureReplayMatches(existing, userId, amount);
            if ("PAID".equals(existing.getStatus())) {
                return RechargeOrderResult.from(existing);
            }
        }

        RechargeOrder order = existing == null ? createOrLoad(requestId, userId, amount) : existing;
        ensureReplayMatches(order, userId, amount);
        if ("PAID".equals(order.getStatus())) {
            return RechargeOrderResult.from(order);
        }

        ledgerService.post(
                requestId,
                WalletTxnType.RECHARGE,
                List.of(
                        WalletPosting.debit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount),
                        WalletPosting.credit(accountService.ensureUserWallet(userId), amount)
                )
        );
        rechargeOrderMapper.updateStatus(requestId, "CREATED", "PAID");
        return RechargeOrderResult.from(requireOrder(requestId));
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "recharge amount must be positive");
        }
    }

    private RechargeOrder createOrLoad(String requestId, UUID userId, long amount) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(idGenerator.next());
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("CREATED");
        try {
            rechargeOrderMapper.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            RechargeOrder duplicated = rechargeOrderMapper.selectByRequestId(requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private RechargeOrder requireOrder(String requestId) {
        RechargeOrder order = rechargeOrderMapper.selectByRequestId(requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "recharge order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(RechargeOrder order, UUID userId, long amount) {
        if (!userId.equals(order.getUserId()) || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
