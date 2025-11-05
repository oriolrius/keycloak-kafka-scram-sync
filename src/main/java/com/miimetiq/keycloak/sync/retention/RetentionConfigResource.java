package com.miimetiq.keycloak.sync.retention;

import com.miimetiq.keycloak.sync.domain.entity.RetentionState;
import com.miimetiq.keycloak.sync.service.RetentionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * REST endpoint for managing retention configuration.
 * <p>
 * Endpoints:
 * - GET /api/config/retention: retrieve current retention configuration and status
 * - PUT /api/config/retention: update retention configuration (max_bytes and/or max_age_days)
 */
@Path("/api/config/retention")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RetentionConfigResource {

    private static final Logger LOG = Logger.getLogger(RetentionConfigResource.class);

    // Validation constants
    private static final long MAX_BYTES_LIMIT = 10_737_418_240L; // 10 GB
    private static final int MAX_AGE_DAYS_LIMIT = 3650; // 10 years

    @Inject
    RetentionService retentionService;

    /**
     * Retrieve current retention configuration and database status.
     * <p>
     * Returns the current retention policy settings (max_bytes, max_age_days)
     * and the approximate current database size.
     *
     * @return retention configuration response
     */
    @GET
    public Response getRetentionConfig() {
        LOG.debug("GET /api/config/retention requested");

        try {
            RetentionState state = retentionService.getRetentionState();

            RetentionConfigResponse response = new RetentionConfigResponse(
                    state.getMaxBytes(),
                    state.getMaxAgeDays(),
                    state.getApproxDbBytes(),
                    state.getUpdatedAt()
            );

            return Response
                    .ok(response)
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to retrieve retention configuration: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve retention configuration: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Update retention configuration.
     * <p>
     * Accepts a JSON payload with max_bytes and max_age_days fields.
     * Both fields must be provided. Pass null to disable a limit.
     * <p>
     * Validation rules:
     * - max_bytes must be non-negative if not null
     * - max_bytes must not exceed 10 GB
     * - max_age_days must be non-negative if not null
     * - max_age_days must not exceed 3650 days (10 years)
     *
     * @param request the update request containing new retention values
     * @return updated retention configuration
     */
    @PUT
    public Response updateRetentionConfig(RetentionConfigUpdateRequest request) {
        LOG.infof("PUT /api/config/retention requested: max_bytes=%s, max_age_days=%s",
                request.maxBytes, request.maxAgeDays);

        try {
            // Validate request
            ValidationResult validation = validateUpdateRequest(request);
            if (!validation.isValid()) {
                LOG.warnf("Invalid retention configuration update request: %s", validation.error);
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse(validation.error))
                        .build();
            }

            // Update configuration with provided values
            retentionService.updateRetentionConfig(request.maxBytes, request.maxAgeDays);

            // Retrieve updated state
            RetentionState updatedState = retentionService.getRetentionState();

            RetentionConfigResponse response = new RetentionConfigResponse(
                    updatedState.getMaxBytes(),
                    updatedState.getMaxAgeDays(),
                    updatedState.getApproxDbBytes(),
                    updatedState.getUpdatedAt()
            );

            LOG.infof("Retention configuration updated successfully: max_bytes=%s, max_age_days=%s",
                    request.maxBytes, request.maxAgeDays);

            return Response
                    .ok(response)
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to update retention configuration: %s", e.getMessage());
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to update retention configuration: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Validates a retention configuration update request.
     *
     * @param request the update request to validate
     * @return validation result with error message if invalid
     */
    private ValidationResult validateUpdateRequest(RetentionConfigUpdateRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request body is required");
        }

        // Validate max_bytes (if not null)
        if (request.maxBytes != null) {
            if (request.maxBytes < 0) {
                return ValidationResult.invalid("max_bytes must be non-negative");
            }
            if (request.maxBytes > MAX_BYTES_LIMIT) {
                return ValidationResult.invalid("max_bytes must not exceed " + MAX_BYTES_LIMIT + " bytes (10 GB)");
            }
        }

        // Validate max_age_days (if not null)
        if (request.maxAgeDays != null) {
            if (request.maxAgeDays < 0) {
                return ValidationResult.invalid("max_age_days must be non-negative");
            }
            if (request.maxAgeDays > MAX_AGE_DAYS_LIMIT) {
                return ValidationResult.invalid("max_age_days must not exceed " + MAX_AGE_DAYS_LIMIT + " days (10 years)");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Request DTO for updating retention configuration.
     * <p>
     * Both fields should be provided. Pass null to disable a limit.
     */
    public static class RetentionConfigUpdateRequest {
        public Long maxBytes;
        public Integer maxAgeDays;

        public RetentionConfigUpdateRequest() {
        }

        public RetentionConfigUpdateRequest(Long maxBytes, Integer maxAgeDays) {
            this.maxBytes = maxBytes;
            this.maxAgeDays = maxAgeDays;
        }
    }

    /**
     * Response DTO for retention configuration.
     */
    public static class RetentionConfigResponse {
        public Long maxBytes;
        public Integer maxAgeDays;
        public Long approxDbBytes;
        public LocalDateTime updatedAt;

        public RetentionConfigResponse() {
        }

        public RetentionConfigResponse(Long maxBytes, Integer maxAgeDays, Long approxDbBytes, LocalDateTime updatedAt) {
            this.maxBytes = maxBytes;
            this.maxAgeDays = maxAgeDays;
            this.approxDbBytes = approxDbBytes;
            this.updatedAt = updatedAt;
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

    /**
     * Internal validation result holder.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public boolean isValid() {
            return valid;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
    }
}
