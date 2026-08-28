package dev.tlamutant.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.ModelValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;
import util.UniqueString;

final class TlcValueCanonicalizerTest {
    private final TlcValueCanonicalizer canonicalizer = new TlcValueCanonicalizer();

    @BeforeAll
    static void initializeInternTable() {
        UniqueString.initialize();
        ModelValue.init();
    }

    @Test
    void distinguishesTypedAtomicValues() {
        CanonicalValue integer = canonicalizer.canonicalize(IntValue.gen(1));
        CanonicalValue string = canonicalizer.canonicalize(new StringValue("A"));
        CanonicalValue model = canonicalizer.canonicalize(ModelValue.make("A"));

        assertEquals("integer", integer.kind());
        assertEquals("1", integer.tlaText());
        assertNotEquals(string, model);
    }

    @Test
    void deeplyNormalizesSetsRecordsFunctionsAndNestedValues() {
        Value one = IntValue.gen(1);
        Value two = IntValue.gen(2);
        SetEnumValue set12 = new SetEnumValue(new Value[] {one, two}, false);
        SetEnumValue set21 = new SetEnumValue(new Value[] {two, one}, false);
        RecordValue recordAB = new RecordValue(
                new UniqueString[] {UniqueString.of("a"), UniqueString.of("b")}, new Value[] {one, set21}, false);
        RecordValue recordBA = new RecordValue(
                new UniqueString[] {UniqueString.of("b"), UniqueString.of("a")}, new Value[] {set12, one}, false);
        FcnRcdValue function12 =
                new FcnRcdValue(Map.of(one, recordAB, two, new TupleValue(new Value[] {two, one})), false);
        FcnRcdValue function21 =
                new FcnRcdValue(Map.of(two, new TupleValue(new Value[] {two, one}), one, recordBA), false);

        assertEquals(canonicalizer.canonicalize(set12), canonicalizer.canonicalize(set21));
        assertEquals(canonicalizer.canonicalize(recordAB), canonicalizer.canonicalize(recordBA));
        assertEquals(canonicalizer.canonicalize(function12), canonicalizer.canonicalize(function21));
    }
}
