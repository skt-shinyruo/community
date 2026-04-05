package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.dto.CreateTransferResponse;
import com.nowcoder.community.wallet.entity.TransferOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.TransferOrderMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransferService {

    private final TransferOrderMapper transferOrderMapper;
    private final WalletAccountService accountService;
    private final WalletLedgerService ledgerService;

    public TransferService(TransferOrderMapper transferOrderMapper,
                           WalletAccountService accountService,
                           WalletLedgerService ledgerService) {
        this.transferOrderMapper = transferOrderMapper;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public CreateTransferResponse create(String requestId, int fromUserId, int toUserId, long amount) {
        validate(requestId, amount);
        requirePositiveUsers(fromUserId, toUserId);
        if (fromUserId == toUserId) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSFER, "cannot transfer to self");
        }

        TransferOrder existing = transferOrderMapper.selectByRequestId(requestId);
        if (existing != null) {
            ensureReplayMatches(existing, fromUserId, toUserId, amount);
            return CreateTransferResponse.from(existing);
        }

        accountService.requireUserWalletActive(fromUserId);

        ledgerService.post(
                requestId,
                WalletTxnType.TRANSFER,
                List.of(
                        WalletPosting.debit(accountService.ensureUserWallet(fromUserId), amount),
                        WalletPosting.credit(accountService.ensureUserWallet(toUserId), amount)
                )
        );

        TransferOrder order = createOrLoad(requestId, fromUserId, toUserId, amount);
        ensureReplayMatches(order, fromUserId, toUserId, amount);
        return CreateTransferResponse.from(order);
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "transfer amount must be positive");
        }
    }

    private void requirePositiveUsers(int fromUserId, int toUserId) {
        if (fromUserId <= 0 || toUserId <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must be positive");
        }
    }

    private TransferOrder createOrLoad(String requestId, int fromUserId, int toUserId, long amount) {
        TransferOrder order = new TransferOrder();
        order.setRequestId(requestId);
        order.setFromUserId(fromUserId);
        order.setToUserId(toUserId);
        order.setAmount(amount);
        order.setStatus("SUCCEEDED");
        try {
            transferOrderMapper.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            TransferOrder duplicated = transferOrderMapper.selectByRequestId(requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private void ensureReplayMatches(TransferOrder order, int fromUserId, int toUserId, long amount) {
        if (order.getFromUserId() != fromUserId || order.getToUserId() != toUserId || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
