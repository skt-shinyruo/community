package com.nowcoder.community.content.contracts.event;

public interface ContentContractEventCodec {

    ContentTypedEvent decode(ContentContractEvent envelope);

    ContentContractEvent encode(ContentTypedEvent event);

    ContentContractEvent deserialize(String json);

    String serialize(ContentTypedEvent event);
}
