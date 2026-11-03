// PROTOTYPE: execute the unchanged config declaration classes with a recording builder.
import java.util.*;
import java.util.function.*;

class ConfigCapture {
    static final List<Map<String, Object>> fields = new ArrayList<>();
    static String localizationPath(String path) { return "config.sollimepie." + path; }
    static class ConfigValue<T> {}
    static class IntValue extends ConfigValue<Integer> {}
    static class DoubleValue extends ConfigValue<Double> {}
    static class BooleanValue extends ConfigValue<Boolean> {}
    static class Lists {
        @SafeVarargs static <T> ArrayList<T> newArrayList(T... values) {
            return new ArrayList<>(Arrays.asList(values));
        }
    }
    static class Builder {
        final String scope;
        final List<String> groups = new ArrayList<>();
        String comment = "", translation = "";
        Builder(String scope) { this.scope = scope; }
        Builder push(String group) { groups.add(group); return this; }
        Builder pop() { groups.removeLast(); return this; }
        Builder comment(String... text) { comment = String.join("\n", text).strip(); return this; }
        Builder translation(String key) { translation = key; return this; }
        Map<String, Object> record(String key, Object value, String type) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("scope", scope);
            field.put("group", String.join(".", groups));
            field.put("path", new ArrayList<>(groups));
            field.put("key", key);
            field.put("default", value);
            field.put("type", type);
            field.put("comment", comment);
            field.put("translation", translation);
            fields.add(field);
            comment = translation = "";
            return field;
        }
        BooleanValue define(String key, boolean value) {
            record(key, value, "boolean"); return new BooleanValue();
        }
        IntValue defineInRange(String key, int value, int min, int max) {
            Map<String, Object> f = record(key, value, "integer");
            f.put("min", min); f.put("max", max); return new IntValue();
        }
        DoubleValue defineInRange(String key, double value, double min, double max) {
            Map<String, Object> f = record(key, value, "number");
            f.put("min", min); f.put("max", max); return new DoubleValue();
        }
        <T> ConfigValue<List<? extends T>> list(String key, List<T> value,
                Supplier<T> sample, Predicate<Object> validator, boolean allowEmpty) {
            T example = sample.get();
            if (!validator.test(example) && !value.isEmpty())
                throw new IllegalArgumentException("Invalid list sample: " + key);
            Map<String, Object> f = record(key, value, "list");
            f.put("itemType", example instanceof Number ? "number" : "string");
            f.put("minItems", allowEmpty ? 0 : 1);
            return new ConfigValue<>();
        }
        <T> ConfigValue<List<? extends T>> defineList(String key, List<T> value,
                Supplier<T> sample, Predicate<Object> validator) {
            return list(key, value, sample, validator, false);
        }
        <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String key, List<T> value,
                Supplier<T> sample, Predicate<Object> validator) {
            return list(key, value, sample, validator, true);
        }
    }

    /* CONFIG_CLASSES */

    static String json(Object value) {
        if (value instanceof String s) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32) out.append(String.format("\\u%04x", (int)c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((k, v) -> entries.add(json(k) + ":" + json(v)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof List<?> list)
            return "[" + String.join(",", list.stream().map(ConfigCapture::json).toList()) + "]";
        return String.valueOf(value);
    }
    public static void main(String[] args) {
        new Server(new Builder("server"));
        new Client(new Builder("client"));
        System.out.println(json(fields));
    }
}
