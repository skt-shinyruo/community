package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.entity.TransferOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.TransferOrderMapper;
import com.nowcoder.community.wallet.model.TransferOrderResult;
import com.nowcoder.community.wallet.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final TransferOrderMapper transferOrderMapper;
    private final WalletAccountService accountService;
    private final WalletLedgerService ledgerService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public TransferService(TransferOrderMapper transferOrderMapper,
                           WalletAccountService accountService,
                           WalletLedgerService ledgerService) {
        this(transferOrderMapper, accountService, ledgerService, new UuidV7Generator());
    }

    TransferService(TransferOrderMapper transferOrderMapper,
                    WalletAccountService accountService,
                    WalletLedgerService ledgerService,
                    UuidV7Generator idGenerator) {
        this.transferOrderMapper = transferOrderMapper;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public TransferOrderResult create(String requestId, UUID fromUserId, UUID toUserId, long amount) {
        validate(requestId, amount);
        requireValidUsers(fromUserId, toUserId);
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSFER, "cannot transfer to self");
        }

        TransferOrder existing = transferOrderMapper.selectByFromUserIdAndRequestId(fromUserId, requestId);
        if (existing != null) {
            ensureReplayMatches(existing, fromUserId, toUserId, amount);
            return TransferOrderResult.from(existing);
        }

        accountService.requireUserWalletActive(fromUserId);

        TransferOrder order = createOrLoad(requestId, fromUserId, toUserId, amount);
        ensureReplayMatches(order, fromUserId, toUserId, amount);
        if (!"SUCCEEDED".equals(order.getStatus())) {
            ledgerService.post(new WalletLedgerCommand(
                    "wallet:transfer:" + order.getOrderId(),
                    WalletTxnType.TRANSFER,
                    WalletTxnType.TRANSFER.name(),
                    order.getOrderId().toString(),
                    List.of(
                            WalletPosting.debit(accountService.ensureUserWallet(fromUserId), amount),
                            WalletPosting.credit(accountService.ensureUserWallet(toUserId), amount)
                    )
            ));
            transferOrderMapper.updateStatus(fromUserId, requestId, "CREATED", "SUCCEEDED");
        }
        return TransferOrderResult.from(requireOrder(fromUserId, requestId));
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "transfer amount must be positive");
        }
    }

    private void requireValidUsers(UUID fromUserId, UUID toUserId) {
        if (fromUserId == null || toUserId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
    }

    private TransferOrder createOrLoad(String requestId, UUID fromUserId, UUID toUserId, long amount) {
        TransferOrder order = new TransferOrder();
        order.setOrderId(idGenerator.next());
        order.setRequestId(requestId);
        order.setFromUserId(fromUserId);
        order.setToUserId(toUserId);
        order.setAmount(amount);
        order.setStatus("CREATED");
        try {
            transferOrderMapper.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            TransferOrder duplicated = transferOrderMapper.selectByFromUserIdAndRequestId(fromUserId, requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private TransferOrder requireOrder(UUID fromUserId, String requestId) {
        TransferOrder order = transferOrderMapper.selectByFromUserIdAndRequestId(fromUserId, requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "transfer order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(TransferOrder order, UUID fromUserId, UUID toUserId, long amount) {
        if (!fromUserId.equals(order.getFromUserId()) || !toUserId.equals(order.getToUserId()) || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
