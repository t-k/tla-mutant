package dev.tlamutant.state;

import java.util.Objects;
import tlc2.value.IValue;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.IntervalValue;
import tlc2.value.impl.ModelValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.TupleValue;

public final class TlcValueCanonicalizer {
    public CanonicalValue canonicalize(IValue input) {
        Objects.requireNonNull(input, "input");
        IValue value = input.deepCopy().initialize();
        if (!value.isDefined()) {
            throw new IllegalArgumentException("TLC value is undefined");
        }
        value.deepNormalize();
        if (!value.isNormalized()) {
            throw new IllegalArgumentException("TLC value did not deeply normalize");
        }
        return new CanonicalValue(kind(value), value.toString());
    }

    private static String kind(IValue value) {
        return switch (value) {
            case IntValue ignored -> "integer";
            case BoolValue ignored -> "boolean";
            case StringValue ignored -> "string";
            case ModelValue ignored -> "model-value";
            case SetEnumValue ignored -> "set";
            case TupleValue ignored -> "tuple";
            case RecordValue ignored -> "record";
            case FcnRcdValue ignored -> "function";
            case IntervalValue ignored -> "interval";
            default ->
                throw new IllegalArgumentException(
                        "Unsupported TLC value kind: " + value.getClass().getName());
        };
    }
}
