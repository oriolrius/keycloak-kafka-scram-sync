package com.miimetiq.keycloak.sync.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * REST endpoint for receiving Keycloak Admin Events via webhook.
 * <p>
 * This endpoint serves as the entry point for event-driven synchronization.
 * When Keycloak performs administrative operations (user create, update, delete, etc.),
 * it sends webhook notifications to this endpoint for processing.
 * <p>
 * Security: Validates HMAC-SHA256 signatures to prevent unauthorized event injection.
 * <p>
 * Endpoints:
 * - POST /api/kc/events: receive and enqueue Keycloak admin events
 */
@Path("/api/kc/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class KeycloakWebhookResource {

    private static final Logger LOG = Logger.getLogger(KeycloakWebhookResource.class);
    private static final String SIGNATURE_HEADER = "X-Keycloak-Signature";

    @Inject
    WebhookSignatureValidator signatureValidator;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EventQueueService eventQueueService;

    /**
     * Receive a Keycloak admin event via webhook.
     * <p>
     * Accepts JSON payloads representing Keycloak admin events, validates the
     * HMAC signature, then validates and processes the event.
     * <p>
     * Returns 200 OK if the event is successfully received and enqueued.
     * Returns 400 Bad Request if the payload is malformed or invalid.
     * Returns 401 Unauthorized if the signature is missing or invalid.
     * Returns 500 Internal Server Error if enqueueing fails.
     *
     * @param signature the HMAC signature from X-Keycloak-Signature header
     * @param payload   the raw JSON payload as string
     * @return response indicating success or failure
     */
    @POST
    public Response receiveEvent(
            @HeaderParam(SIGNATURE_HEADER) String signature,
            String payload) {

        // Generate correlation ID for tracking
        String correlationId = UUID.randomUUID().toString();

        try {
            // Validate payload exists
            if (payload == null || payload.isBlank()) {
                LOG.warnf("[%s] Received null or empty event payload", correlationId);
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Event payload is required"))
                        .build();
            }

            // Validate HMAC signature
            WebhookSignatureValidator.ValidationResult validationResult =
                    signatureValidator.validate(payload, signature);

            if (!validationResult.isValid()) {
                LOG.warnf("[%s] Signature validation failed: %s", correlationId, validationResult.getErrorMessage());
                return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("Signature validation failed: " + validationResult.getErrorMessage()))
                        .build();
            }

            // Deserialize JSON payload
            KeycloakAdminEvent event;
            try {
                event = objectMapper.readValue(payload, KeycloakAdminEvent.class);
            } catch (Exception e) {
                LOG.warnf(e, "[%s] Failed to parse event payload: %s", correlationId, e.getMessage());
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid JSON payload: " + e.getMessage()))
                        .build();
            }

            // Validate event fields
            if (event == null) {
                LOG.warnf("[%s] Deserialized event is null", correlationId);
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Event payload is required"))
                        .build();
            }

            // Basic validation of required fields
            if (event.getResourceType() == null || event.getOperationType() == null) {
                LOG.warnf("[%s] Received event with missing required fields: %s", correlationId, event);
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Event must contain resourceType and operationType"))
                        .build();
            }

            // Log received event with correlation ID
            LOG.infof("[%s] Received Keycloak admin event: resourceType=%s, operationType=%s, resourcePath=%s",
                    correlationId, event.getResourceType(), event.getOperationType(), event.getResourcePath());

            // Enqueue event for asynchronous processing
            WebhookEvent webhookEvent = new WebhookEvent(correlationId, event);
            boolean enqueued = eventQueueService.enqueue(webhookEvent);

            if (!enqueued) {
                LOG.errorf("[%s] Failed to enqueue event, queue is full", correlationId);
                return Response
                        .status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(new ErrorResponse("Event queue is full, please retry later"))
                        .build();
            }

            LOG.debugf("[%s] Event enqueued for processing: %s", correlationId, event);

            // Return success response
            return Response
                    .ok(new EventResponse(
                            correlationId,
                            "Event received successfully",
                            event.getResourceType(),
                            event.getOperationType()
                    ))
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "[%s] Failed to process webhook event: %s", correlationId, e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to process event: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Response DTO for successful event receipt.
     */
    public static class EventResponse {
        public String correlationId;
        public String message;
        public String resourceType;
        public String operationType;

        public EventResponse() {
        }

        public EventResponse(String correlationId, String message, String resourceType, String operationType) {
            this.correlationId = correlationId;
            this.message = message;
            this.resourceType = resourceType;
            this.operationType = operationType;
        }
    }

    /**
     * Response DTO for errors.
     */
    public static class ErrorResponse {
        public String error;

        public ErrorResponse() {
        }

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
