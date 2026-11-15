package com.lifeplanner.util;

import com.lifeplanner.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Raw PATCH body that keeps "key absent" (leave unchanged) apart from "explicit null" (clear). Jackson maps both
 * to Optional.empty() when binding records directly. Values are converted with the application ObjectMapper, so
 * the same strict rules apply as for POST bodies.
 */
public final class JsonPatchBody {

    private final ObjectNode object;
    private final ObjectMapper mapper;
    private final String invalidMessage;

    private JsonPatchBody(ObjectNode object, ObjectMapper mapper, String invalidMessage) {
        this.object = object;
        this.mapper = mapper;
        this.invalidMessage = invalidMessage;
    }

    /**
     * @param editable keys that may be changed
     * @param readOnly known keys that exist on the resource but cannot be changed here (→ 400 {@code not_editable})
     */
    public static JsonPatchBody of(JsonNode body, ObjectMapper mapper, Set<String> editable, Set<String> readOnly,
                                   String invalidMessage) {
        if (!(body instanceof ObjectNode object)) {
            throw new ApiException.ValidationException(invalidMessage, null);
        }
        for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (readOnly.contains(name)) {
                throw new ApiException.ValidationException(invalidMessage, Map.of(name, "not_editable"));
            }
            if (!editable.contains(name)) {
                throw new ApiException.ValidationException(invalidMessage, Map.of(name, "unknown_field"));
            }
        }
        return new JsonPatchBody(object, mapper, invalidMessage);
    }

    /** null = key absent; Optional.empty() = explicit null; Optional.of(v) = value. */
    @SuppressWarnings("OptionalAssignedToNull")
    public <T> Optional<T> field(String name, Class<T> type) {
        if (!object.has(name)) {
            return null;
        }
        JsonNode node = object.get(name);
        if (node.isNull()) {
            return Optional.empty();
        }
        if (type == String.class && !node.isTextual()) {
            throw invalid(name);
        }
        try {
            return Optional.of(mapper.treeToValue(node, type));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw invalid(name);
        }
    }

    private ApiException.ValidationException invalid(String field) {
        return new ApiException.ValidationException(invalidMessage, Map.of(field, "invalid_value"));
    }
}
