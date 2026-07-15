package com.nowcoder.community.social.contracts.event;

public interface SocialContractEventCodec {

    SocialTypedEvent decode(SocialContractEvent envelope);

    SocialContractEvent encode(SocialTypedEvent event);

    SocialContractEvent deserialize(String json);

    String serialize(SocialTypedEvent event);
}
