package com.tibudget.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NextJSUtilsRobustnessTest {
    private final Gson gson = new Gson();

    private Map<Integer, List<String>> pushes(String script) {
        return NextJSUtils.extractNextFlightPushes(Jsoup.parse("<script>" + script + "</script>"));
    }

    private String push(String chunk) {
        return "self.__next_f.push([1," + gson.toJson(chunk) + "]);";
    }

    @Test
    void preservesDelimitersQuotesUnicodeAndControlCharacters() {
        String chunk = "text ]) [ ] \\\" escaped \\ path\n\r\t\b\f été 😀";
        assertEquals(List.of(chunk), pushes(push(chunk)).get(1));
        assertEquals(List.of("é😀"), pushes("self.__next_f.push([1,\"\\u00e9\\ud83d\\ude00\"])").get(1));
    }

    @Test
    void findsMasterLineAfterDelimiterInText() {
        String chunk = "0:{\"text\":\"some ]) text\"}\n1:{\"lines\":[{\"isMaster\":true,\"state\":\"active\"}]}\n";
        List<Row> lines = NextJSUtils.extractListByKey(gson, pushes(push(chunk)), "lines", Row.class);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).isMaster);
        assertEquals("active", lines.get(0).state);
    }

    @Test
    void preservesChannelsOrderAndNonStringPayloads() {
        Map<Integer, List<String>> result = pushes("self.__next_f.push([0]);"
                + push("first") + "self.__next_f.push([2,{\"value\":[1,2]}]);" + push("second")
                + "self.__next_f.push([3,null]);");
        assertEquals(List.of(1, 2, 3), new ArrayList<>(result.keySet()));
        assertEquals(List.of("first", "second"), result.get(1));
        assertEquals(List.of("{\"value\":[1,2]}"), result.get(2));
        assertEquals(List.of("null"), result.get(3));
    }

    @Test
    void supportsWhitespaceAndMultipleScripts() {
        String html = "<script>self . __next_f . push ( [1,\"first\"] )</script>"
                + "<script>self.__next_f.push([1,\"second\"])</script>";
        assertEquals(List.of("first", "second"), NextJSUtils.extractNextFlightPushes(Jsoup.parse(html)).get(1));
    }

    @Test
    void ignoresCallsInsideStringsAndComments() {
        String fake = "self.__next_f.push([1,\"fake\"]);";
        String script = gson.toJson(fake) + "; '" + fake + "'; `" + fake + "`; // " + fake
                + "\n/* " + fake + " */" + push("real");
        assertEquals(List.of("real"), pushes(script).get(1));
    }

    @Test
    void rejectsTruncatedAndMalformedPushes() {
        for (String script : List.of("self.__next_f.push([1,\"unfinished",
                "self.__next_f.push([1,\"x\"]", "self.__next_f.push([1,undefined]);",
                "self.__next_f.push([\"1\",\"x\"]);", "self.__next_f.push([1.5,\"x\"]);",
                "self.__next_f.push([1,\"\\q\"]);")) {
            assertThrows(IllegalArgumentException.class, () -> pushes(script));
        }
    }

    @Test
    void handlesEveryChunkBoundaryIncludingEscapesAndPropertyNames() {
        String source = "0:{\"text\":\"a ]) marker\",\"lines\" : [ {\"label\":\"escaped \\\"quote\\\" and ]\","
                + "\"nested\":[[1],[2,3]],\"isMaster\":true} ]}";
        for (int i = 0; i <= source.length(); i++) {
            Map<Integer, List<String>> fragments = pushes(push(source.substring(0, i)) + push(source.substring(i)));
            List<Row> rows = NextJSUtils.extractListByKey(gson, fragments, "lines", Row.class);
            assertEquals(1, rows.size(), "Split offset " + i);
            assertEquals("escaped \"quote\" and ]", rows.get(0).label);
            assertTrue(rows.get(0).isMaster);
        }
    }

    @Test
    void matchesExactKeysAndIgnoresKeysInsideStringValues() {
        String source = "0:{\"text\":\"\\\"lines\\\":[{\\\"label\\\":\\\"wrong\\\"}]\","
                + "\"otherlines\":[{\"label\":\"wrong\"}],\"l\\u0069nes\" : [{\"label\":\"right\"}]}";
        List<Row> rows = NextJSUtils.extractListByKey(gson, pushes(push(source)), "lines", Row.class);
        assertEquals(1, rows.size());
        assertEquals("right", rows.get(0).label);
    }

    @Test
    void doesNotJoinDifferentChannels() {
        Map<Integer, List<String>> channels = new LinkedHashMap<>();
        channels.put(2, List.of("{\"li"));
        channels.put(1, List.of("nes\":[{}]}"));
        assertThrows(IllegalArgumentException.class,
                () -> NextJSUtils.extractListByKey(gson, channels, "lines", Row.class));
    }

    @Test
    void distinguishesAbsentEmptyAndMalformedArrays() {
        assertTrue(NextJSUtils.extractListByKey(gson, pushes(push("{}")), "lines", Row.class).isEmpty());
        assertTrue(NextJSUtils.extractListByKey(gson, pushes(push("{\"lines\":[]}")), "lines", Row.class).isEmpty());
        for (String source : List.of("{\"lines\":[{", "{\"lines\":[{\"label\":oops}]}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> NextJSUtils.extractListByKey(gson, pushes(push(source)), "lines", Row.class));
        }
    }

    @Test
    void retainsCallerGsonAdapters() {
        Gson custom = new GsonBuilder().registerTypeAdapter(Row.class,
                (com.google.gson.JsonDeserializer<Row>) (json, type, context) -> {
                    Row row = new Row(); row.label = "adapted"; return row;
                }).create();
        assertEquals("adapted", NextJSUtils.extractListByKey(custom,
                pushes(push("{\"lines\":[{}]}")), "lines", Row.class).get(0).label);
    }

    @Test
    void acceptsMissingDocumentsAndPushes() {
        assertTrue(NextJSUtils.extractNextFlightPushes(null).isEmpty());
        assertTrue(pushes("").isEmpty());
        assertTrue(NextJSUtils.extractListByKey(gson, null, "lines", Row.class).isEmpty());
    }

    static class Row {
        String label;
        String state;
        boolean isMaster;
        List<List<Integer>> nested;
    }
}
