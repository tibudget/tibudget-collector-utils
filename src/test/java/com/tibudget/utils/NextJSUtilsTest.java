package com.tibudget.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tibudget.utils.gson.OffsetDateTimeAdapter;
import com.tibudget.utils.gson.ZonedDateTimeAdapter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NextJSUtilsTest {

    private static Document nextjs01Document;

    private static Gson gson;

    @BeforeAll
    public static void loadTestHtmlFiles() throws IOException {
        gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
                .create();

        byte[] html = Files.readAllBytes(Paths.get("src/test/resources/nextjs-01.html"));
        assertTrue(html.length > 0, "nextjs-01.html should not be empty");

        nextjs01Document = Jsoup.parse(new String(html, StandardCharsets.UTF_8));

        assertFalse(nextjs01Document.text().isBlank(), "Parsed document should not be empty");
    }

    @Test
    public void testExtractNextFlightPushes() {
        Elements scripts = nextjs01Document.select("script");

        System.out.println("Scripts count: " + scripts.size());

        long nextFlightScripts = scripts.stream()
                .filter(script -> script.data().contains("__next_f.push"))
                .count();

        System.out.println("Next Flight scripts: " + nextFlightScripts);

        Map<Integer, List<String>> pushes = NextJSUtils.extractNextFlightPushes(nextjs01Document);

        System.out.println("Push IDs: " + pushes.keySet());
        pushes.forEach((id, values) ->
                System.out.println("Push " + id + ": " + values.size() + " chunks")
        );

        assertEquals(1, pushes.size());
        assertEquals(41, pushes.get(1).size());

        List<InvoiceDto> invoices = NextJSUtils.extractListByKey(gson, pushes, "invoices", InvoiceDto.class);
        assertEquals(11, invoices.size());

        List<LineDto> lines = NextJSUtils.extractListByKey(gson, pushes, "lines", LineDto.class);
        assertEquals(4, lines.size());
    }
}
