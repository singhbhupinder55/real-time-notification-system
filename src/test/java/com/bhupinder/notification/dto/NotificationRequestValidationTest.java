package com.bhupinder.notification.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests - no Spring context, no Redis, no WebSocket. Just Jakarta
 * Bean Validation directly against the DTO, the same mechanism @Valid uses
 * under the hood in NotificationController. Fast, focused, and they fail for
 * exactly one reason each.
 */
class NotificationRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        NotificationRequest request = new NotificationRequest("user-1", "hello");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void blankTopicIsRejected() {
        NotificationRequest request = new NotificationRequest("", "hello");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("topic must not be blank");
    }

    @Test
    void whitespaceOnlyTopicIsRejected() {
        // @NotBlank specifically rejects whitespace-only strings, not just
        // empty strings - worth its own test since @NotNull or @NotEmpty
        // would NOT catch this case.
        NotificationRequest request = new NotificationRequest("   ", "hello");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
    }

    @Test
    void nullTopicIsRejected() {
        NotificationRequest request = new NotificationRequest(null, "hello");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
    }

    @Test
    void blankMessageIsRejected() {
        NotificationRequest request = new NotificationRequest("user-1", "");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("message must not be blank");
    }

    @Test
    void nullMessageIsRejected() {
        NotificationRequest request = new NotificationRequest("user-1", null);
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
    }

    @Test
    void bothFieldsBlankProducesTwoViolations() {
        NotificationRequest request = new NotificationRequest("", "");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);
    }

    @Test
    void singleCharacterTopicAndMessagePassValidation() {
        // @NotBlank only requires non-whitespace content - no minimum
        // length beyond that. Confirms we haven't accidentally over-constrained.
        NotificationRequest request = new NotificationRequest("a", "b");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void veryLongMessagePassesValidation() {
        // No @Size constraint exists on message - documents that there is
        // currently NO upper bound enforced at the validation layer. Worth
        // knowing for the README's limitations section: a malicious or
        // buggy client could send an arbitrarily large message today.
        String longMessage = "x".repeat(50_000);
        NotificationRequest request = new NotificationRequest("user-1", longMessage);
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void messageWithSpecialCharactersPassesValidation() {
        NotificationRequest request = new NotificationRequest("user-1", "quotes \" backslash \\ newline \n");
        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
