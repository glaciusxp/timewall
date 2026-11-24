package it.timewall.app.localization;

import static org.junit.Assert.*;

import it.timewall.app.configuration.DashboardFont;

import org.junit.Test;
import org.w3c.dom.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.parsers.*;

public class ResourcesTest {
    /**
     * Ensures every localized credit retains its clickable brand/license labels and privacy contact
     * placeholders.
     */
    @Test
    public void privacyAndAttributionContractsHoldInEveryLanguage() throws Exception {
        for (String folder :
                new String[] {
                    "values",
                    "values-it",
                    "values-es",
                    "values-pt",
                    "values-fr",
                    "values-de",
                    "values-zh",
                    "values-ja",
                    "values-ru"
                }) {
            Map<String, Element> resources = load(Paths.get("src/main/res", folder, "strings.xml"));
            String credit = resources.get("string:weather_attribution").getTextContent();
            for (String marker : new String[] {"Open-Meteo", "CC BY 4.0"}) {
                assertTrue(folder, credit.contains(marker));
                assertEquals(folder, credit.indexOf(marker), credit.lastIndexOf(marker));
            }
            String identity =
                    String.format(
                            java.util.Locale.ROOT,
                            resources.get("string:privacy_identity").getTextContent(),
                            PrivacyNotice.CONTROLLER,
                            PrivacyNotice.CONTACT);
            assertTrue(folder, identity.contains("Andrea Persano"));
            assertTrue(folder, identity.contains("andreapersano@gmail.com"));
            for (String key :
                    new String[] {
                        "privacy_service",
                        "privacy_calendars",
                        "privacy_weather",
                        "privacy_local",
                        "privacy_retention",
                        "privacy_security",
                        "privacy_rights",
                        "privacy_web_pending"
                    }) {
                assertFalse(
                        folder + " " + key,
                        resources.get("string:" + key).getTextContent().trim().isEmpty());
            }
        }
    }

    /**
     * Rejects missing literal UI keys, including Info navigation, before they cause a runtime
     * failure.
     */
    @Test
    public void literalUiKeysExistInDefaultResources() throws Exception {
        Map<String, Element> resources = load(Paths.get("src/main/res/values/strings.xml"));
        Pattern references = Pattern.compile("I18n\\.text\\([^,]+,\\s*\"([^\"]+)\"");
        try (java.util.stream.Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file :
                    (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher =
                        references.matcher(
                                new String(
                                        Files.readAllBytes(file),
                                        java.nio.charset.StandardCharsets.UTF_8));
                while (matcher.find())
                    assertTrue(
                            file + ": " + matcher.group(1),
                            resources.containsKey("string:" + matcher.group(1)));
            }
        }
        for (String key :
                new String[] {
                    "timewall_info",
                    "privacy",
                    "licenses_credits",
                    "privacy_identity",
                    "credits_summary",
                    "font_conversion_notice",
                    "licenses_unavailable"
                }) {
            assertTrue(key, resources.containsKey("string:" + key));
        }
    }

    /**
     * Verifica che nessun font selezionabile richieda il supporto ai font variabili di Android
     * recenti.
     */
    @Test
    public void runtimeFontsAreStaticSfntFiles() throws Exception {
        for (DashboardFont font : DashboardFont.values()) {
            if (font.file.isEmpty()) continue;
            java.nio.ByteBuffer data =
                    java.nio.ByteBuffer.wrap(
                            Files.readAllBytes(
                                    Paths.get(
                                            font.availableInFreeEdition()
                                                    ? "src/main/assets"
                                                    : "src/complete/assets",
                                            font.file)));
            int signature = data.getInt();
            assertTrue(font.file, signature == 0x00010000 || signature == 0x4f54544f);
            int tables = data.getShort() & 0xffff;
            for (int index = 0; index < tables; index++) {
                data.position(12 + index * 16);
                assertNotEquals(font.file + " contains fvar", 0x66766172, data.getInt());
            }
        }
        assertTrue(Files.exists(Paths.get("src/main/assets/font-awesome-solid.ttf")));
    }

    /**
     * Legge il file XML delle risorse e indicizza gli elementi per tipo e nome, fallendo se trova
     * chiavi duplicate.
     *
     * @param file nome dell’asset persistito; vuoto per il font di sistema
     * @return risorse indicizzate per chiave, dopo aver verificato l’assenza di duplicati
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    private Map<String, Element> load(Path file) throws Exception {
        Document doc =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
        Map<String, Element> result = new TreeMap<>();
        NodeList nodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++)
            if (nodes.item(i) instanceof Element) {
                Element e = (Element) nodes.item(i);
                String key = e.getTagName() + ":" + e.getAttribute("name");
                assertNull("Duplicate " + key, result.put(key, e));
            }
        return result;
    }

    /**
     * Estrae i segnaposto di formato testuali e numerici per confrontare il contratto delle
     * stringhe tradotte.
     *
     * @param text testo da visualizzare o analizzare
     * @return segnaposto di formato trovati nel testo, senza duplicati
     */
    private Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = Pattern.compile("%([0-9]+\\$)?[sdf]").matcher(text);
        while (m.find()) found.add(m.group());
        return found;
    }

    /**
     * Verifica chiavi e segnaposto di formattazione delle nove lingue, segnalando la lingua e la
     * risorsa divergenti.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void allNineLanguagesKeepKeysAndFormatArgumentsAligned() throws Exception {
        Map<String, Element> english = load(Paths.get("src/main/res/values/strings.xml"));
        for (String lang : new String[] {"it", "es", "pt", "fr", "de", "zh", "ja", "ru"}) {
            Map<String, Element> other =
                    load(Paths.get("src/main/res/values-" + lang + "/strings.xml"));
            assertEquals(lang, english.keySet(), other.keySet());
            for (String key : english.keySet())
                assertEquals(
                        lang + " " + key,
                        placeholders(english.get(key).getTextContent()),
                        placeholders(other.get(key).getTextContent()));
        }
    }

    /** Verifica la presenza dei font selezionabili e degli SVG originali delle icone meteo. */
    @Test
    public void allRuntimeFontsAndSourceWeatherIconsArePresent() {
        for (DashboardFont font : DashboardFont.values())
            if (!font.file.isEmpty())
                assertTrue(
                        font.file,
                        Files.exists(
                                Paths.get(
                                        font.availableInFreeEdition()
                                                ? "src/main/assets"
                                                : "src/complete/assets",
                                        font.file)));
        for (String name :
                new String[] {
                    "sole",
                    "sole-nuvola",
                    "nuvola",
                    "nebbia",
                    "pioggia",
                    "pioggia-forte",
                    "temporale",
                    "neve",
                    "pioggia-neve"
                }) assertTrue(name, Files.exists(Paths.get("design/icons/weather", name + ".svg")));
    }

    /**
     * Verifica che i sorgenti ufficiali non importino classi dell’app diagnostica.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void productionDoesNotDependOnDiagnosticCode() throws Exception {
        try (java.util.stream.Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file :
                    (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String source =
                        new String(
                                Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(
                        file.toString(),
                        source.matches("(?s).*import\\s+it\\.timewall\\.diagnostics\\..*"));
            }
        }
    }
}
