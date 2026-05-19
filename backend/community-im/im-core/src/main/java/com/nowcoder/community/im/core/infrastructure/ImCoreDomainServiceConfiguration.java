package com.nowcoder.community.im.core.infrastructure;

import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import com.nowcoder.community.im.core.domain.repository.UnreadRepository;
import com.nowcoder.community.im.core.domain.service.PrivateMessageDomainService;
import com.nowcoder.community.im.core.domain.service.RoomMembershipDomainService;
import com.nowcoder.community.im.core.domain.service.RoomMessageDomainService;
import com.nowcoder.community.im.core.domain.service.SeqAllocator;
import com.nowcoder.community.im.core.domain.service.UnreadDomainService;
import com.nowcoder.community.im.core.support.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImCoreDomainServiceConfiguration {

    @Bean
    public SeqAllocator seqAllocator(RoomRepository roomRepository, ConversationRepository conversationRepository) {
        return new SeqAllocator(roomRepository, conversationRepository);
    }

    @Bean
    public PrivateMessageDomainService privateMessageDomainService(
            ConversationRepository conversationRepository,
            PrivateMessageRepository privateMessageRepository,
            ConversationReadStateRepository readStateRepository,
            SeqAllocator seqAllocator,
            IdGenerator idGenerator,
            @Value("${im.message.max-chars:10000}") int maxContentChars
    ) {
        return new PrivateMessageDomainService(
                conversationRepository,
                privateMessageRepository,
                readStateRepository,
                seqAllocator,
                idGenerator,
                maxContentChars
        );
    }

    @Bean
    public RoomMembershipDomainService roomMembershipDomainService(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            IdGenerator idGenerator,
            @Value("${im.room.max-members:10000}") int maxMembersPerRoom
    ) {
        return new RoomMembershipDomainService(
                roomRepository,
                roomMemberRepository,
                idGenerator,
                maxMembersPerRoom
        );
    }

    @Bean
    public RoomMessageDomainService roomMessageDomainService(
            RoomRepository roomRepository,
            RoomMembershipDomainService membershipService,
            RoomMessageRepository roomMessageRepository,
            RoomReadStateRepository readStateRepository,
            SeqAllocator seqAllocator,
            IdGenerator idGenerator,
            @Value("${im.message.max-chars:10000}") int maxContentChars
    ) {
        return new RoomMessageDomainService(
                roomRepository,
                membershipService,
                roomMessageRepository,
                readStateRepository,
                seqAllocator,
                idGenerator,
                maxContentChars
        );
    }

    @Bean
    public UnreadDomainService unreadDomainService(UnreadRepository unreadRepository) {
        return new UnreadDomainService(unreadRepository);
    }
}
