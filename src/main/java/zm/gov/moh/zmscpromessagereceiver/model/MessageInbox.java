package zm.gov.moh.zmscpromessagereceiver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("MessageInboxes")
public class MessageInbox {

    @Id
    @Column("MessageId")
    private UUID messageId;

    @Column("SourceSystem")
    private String sourceSystem;

    @Column("MessageType")
    private String messageType;

    @Column("Payload")
    private String payload;

    @Column("Status")
    private String status;

    @Column("ReceivedAt")
    private LocalDateTime receivedAt;

    @Column("ProcessedAt")
    private LocalDateTime processedAt;
}
