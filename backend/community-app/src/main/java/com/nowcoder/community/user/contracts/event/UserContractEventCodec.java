package com.nowcoder.community.user.contracts.event;

public interface UserContractEventCodec {

    UserTypedEvent decode(UserContractEvent envelope);

    UserContractEvent encode(UserTypedEvent event);

    UserContractEvent deserialize(String json);

    String serialize(UserTypedEvent event);
}
