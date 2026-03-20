package zm.gov.moh.zmscpromessagereceiver.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import zm.gov.moh.zmscpromessagereceiver.model.MessageInbox;

import java.util.UUID;

@Repository
public interface MessageInboxRepository extends ReactiveCrudRepository<MessageInbox, UUID> {
}
