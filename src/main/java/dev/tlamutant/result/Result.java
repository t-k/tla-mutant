package dev.tlamutant.result;

import java.util.Objects;
import java.util.function.Function;

public sealed interface Result<T> permits Result.Success, Result.Failure {
    record Success<T>(T value) implements Result<T> {
        public Success {
            Objects.requireNonNull(value, "value");
        }
    }

    record Failure<T>(String code, String message) implements Result<T> {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(String code, String message) {
        return new Failure<>(code, message);
    }

    default <R> Result<R> map(Function<T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return switch (this) {
            case Success<T>(var value) -> success(mapper.apply(value));
            case Failure<T>(var code, var message) -> failure(code, message);
        };
    }
}
