package it.timewall.app.configuration;

import java.util.Locale;
import java.util.regex.Pattern;

/** Android hex notation: #RRGGBB or #AARRGGBB (alpha first). */
public final class HexColor {
    private static final Pattern VALID = Pattern.compile("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private HexColor() {}

    /**
     * Controlla che il valore sia esattamente un colore #RRGGBB o #AARRGGBB. Rifiuta null, spazi e
     * abbreviazioni a tre cifre.
     *
     * @param value valore da validare, formattare o mostrare
     * @return true per un HEX completo a sei o otto cifre preceduto da #
     */
    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    /**
     * Converte un HEX valido nel colore ARGB Android; per #RRGGBB aggiunge alpha completamente
     * opaco. Un valore non valido causa IllegalArgumentException.
     *
     * @param value valore da validare, formattare o mostrare
     * @return colore ARGB, con alpha 255 quando non è indicato
     */
    public static int parse(String value) {
        if (!isValid(value)) throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB");
        long color = Long.parseLong(value.substring(1), 16);
        return (int) (value.length() == 7 ? color | 0xff000000L : color);
    }

    /**
     * Serializza il colore in HEX maiuscolo, omettendo il canale alpha soltanto quando è
     * completamente opaco.
     *
     * @param color colore ARGB da associare al contenuto
     * @return HEX maiuscolo #RRGGBB oppure #AARRGGBB se il colore non è opaco
     */
    public static String format(int color) {
        return color >>> 24 == 255
                ? String.format(Locale.ROOT, "#%06X", color & 0xffffff)
                : String.format(Locale.ROOT, "#%08X", color);
    }
}
