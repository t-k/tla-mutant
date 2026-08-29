package dev.tlamutant.prospective;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ProspectiveSemanticComparator {
    private final ObjectMapper mapper = new ObjectMapper();

    public Comparison compare(ProspectiveDiscoveryReport left, ProspectiveDiscoveryReport right) {
        JsonNode leftProjection = sanitize(mapper.valueToTree(Objects.requireNonNull(left, "left")));
        JsonNode rightProjection = sanitize(mapper.valueToTree(Objects.requireNonNull(right, "right")));
        String difference = firstDifference(leftProjection, rightProjection, "");
        return difference == null ? new Comparison(true, "") : new Comparison(false, difference);
    }

    private static JsonNode sanitize(JsonNode node) {
        JsonNode copy = node.deepCopy();
        removeDiagnostics(copy);
        return copy;
    }

    private static void removeDiagnostics(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove(List.of("diagnostic", "diagnosticReason"));
            object.elements().forEachRemaining(ProspectiveSemanticComparator::removeDiagnostics);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(ProspectiveSemanticComparator::removeDiagnostics);
        }
    }

    private static String firstDifference(JsonNode left, JsonNode right, String pointer) {
        if (left.getNodeType() != right.getNodeType()) {
            return pointer.isEmpty() ? "/" : pointer;
        }
        if (left.isObject()) {
            List<String> names = new ArrayList<>();
            left.fieldNames().forEachRemaining(names::add);
            right.fieldNames().forEachRemaining(name -> {
                if (!names.contains(name)) {
                    names.add(name);
                }
            });
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                if (!left.has(name) || !right.has(name)) {
                    return pointer + "/" + escape(name);
                }
                String difference = firstDifference(left.get(name), right.get(name), pointer + "/" + escape(name));
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        if (left.isArray()) {
            if (left.size() != right.size()) {
                return pointer + "/length";
            }
            for (int index = 0; index < left.size(); index++) {
                String difference = firstDifference(left.get(index), right.get(index), pointer + "/" + index);
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        return left.equals(right) ? null : (pointer.isEmpty() ? "/" : pointer);
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    public record Comparison(boolean equal, String firstDifference) {}
}
