package zm.gov.moh.zmscpromessagereceiver.service;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.grpc.server.service.GrpcService;
import zm.gov.moh.zmscpromessagereceiver.grpc.MessageReceiverServiceGrpc;
import zm.gov.moh.zmscpromessagereceiver.grpc.MessageRequest;
import zm.gov.moh.zmscpromessagereceiver.grpc.MessageResponse;
import zm.gov.moh.zmscpromessagereceiver.model.MessageInbox;
import zm.gov.moh.zmscpromessagereceiver.repository.MessageInboxRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MessageReceiverGrpcService
        extends MessageReceiverServiceGrpc.MessageReceiverServiceImplBase {

    private final MessageInboxRepository repository;

    @Override
    public StreamObserver<MessageRequest> receiveMessages(
            StreamObserver<MessageResponse> responseObserver) {

        return new StreamObserver<>() {

            @Override
            public void onNext(MessageRequest request) {
                String rawMessageId = request.getMessageId();
                log.debug("Received message: id={}, source={}, type={}",
                        rawMessageId, request.getSourceSystem(), request.getMessageType());

                // Validate that message_id is present and is a valid UUID
                if (rawMessageId.isBlank()) {
                    log.warn("Rejected message: message_id is blank");
                    sendResponse(responseObserver, rawMessageId, false,
                            "message_id is required and must not be blank");
                    return;
                }

                UUID messageId;
                try {
                    messageId = UUID.fromString(rawMessageId);
                } catch (IllegalArgumentException e) {
                    log.warn("Rejected message: message_id '{}' is not a valid UUID", rawMessageId);
                    sendResponse(responseObserver, rawMessageId, false,
                            "message_id '" + rawMessageId + "' is not a valid UUID");
                    return;
                }

                LocalDateTime now = LocalDateTime.now();

                MessageInbox inbox = MessageInbox.builder()
                        .messageId(messageId)
                        .sourceSystem(request.getSourceSystem())
                        .messageType(request.getMessageType())
                        .payload(request.getPayload())
                        .status(request.getStatus())
                        .receivedAt(now)
                        .processedAt(now)
                        .build();

                try {
                    repository.save(inbox).block();
                    log.info("Saved message: id={}", messageId);
                    sendResponse(responseObserver, rawMessageId, true, null);

                } catch (DataIntegrityViolationException e) {
                    // FK violation: MessageId does not exist in MessageOutboxes
                    String cause = e.getMessage() != null ? e.getMessage() : "";
                    if (isForeignKeyViolation(cause)) {
                        String msg = "message_id '" + rawMessageId +
                                "' does not exist in MessageOutboxes — insert rejected";
                        log.warn(msg);
                        sendResponse(responseObserver, rawMessageId, false, msg);
                    } else {
                        // Other data integrity issue (duplicate PK, constraint, etc.)
                        log.error("Data integrity error for message_id={}: {}", rawMessageId, cause);
                        sendResponse(responseObserver, rawMessageId, false,
                                "Data integrity error: " + cause);
                    }
                } catch (Exception e) {
                    log.error("Unexpected error saving message_id={}", rawMessageId, e);
                    sendResponse(responseObserver, rawMessageId, false,
                            "Internal error: " + e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Stream error from client: {}", t.getMessage(), t);
                // The client closed with an error; no response can be sent.
            }

            @Override
            public void onCompleted() {
                log.info("Client stream completed");
                responseObserver.onCompleted();
            }
        };
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static void sendResponse(StreamObserver<MessageResponse> observer,
                                     String messageId, boolean success, String error) {
        MessageResponse.Builder builder = MessageResponse.newBuilder()
                .setSuccess(success);
        if (messageId != null) {
            builder.setMessageId(messageId);
        }
        if (error != null) {
            builder.setError(error);
        }
        observer.onNext(builder.build());
    }

    /**
     * SQL Server raises error 547 for FK violations.
     * The message typically contains "FOREIGN KEY constraint" or "FK_".
     */
    private static boolean isForeignKeyViolation(String message) {
        String lower = message.toLowerCase();
        return lower.contains("foreign key") || lower.contains("fk_") || lower.contains("547");
    }
}
