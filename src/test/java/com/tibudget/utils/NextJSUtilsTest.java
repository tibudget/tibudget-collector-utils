package com.tibudget.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tibudget.utils.gson.OffsetDateTimeAdapter;
import com.tibudget.utils.gson.ZonedDateTimeAdapter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NextJSUtilsTest {

    private static Document nextjs01Document;

    private static Gson gson;

    @BeforeAll
    public static void loadTestHtmlFiles() throws IOException {
        gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
                .create();

        nextjs01Document = Jsoup.parse(new String(Files.readAllBytes(Paths.get("src/test/resources/nextjs-01.html"))));
    }

    @Test
    public void testExtractNextFlightPushes() {
        Map<Integer, List<String>> pushes = NextJSUtils.extractNextFlightPushes(nextjs01Document);
        assertEquals(1, pushes.size());
        assertEquals(41, pushes.get(1).size());

        List<InvoiceDto> invoices = NextJSUtils.extractListByKey(gson, pushes, "invoices", InvoiceDto.class);
        assertEquals(11, invoices.size());
        invoices.forEach(System.out::println);

        List<LineDto> lines = NextJSUtils.extractListByKey(gson, pushes, "lines", LineDto.class);
        assertEquals(4, lines.size());
        lines.forEach(System.out::println);
    }
}
