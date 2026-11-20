package it.timewall.app.configuration;

public final class Palette {
    public final int background, accent, text, secondaryText, card, highlight, border;

    /**
     * Memorizza i sette ruoli cromatici della palette senza applicarli alle viste.
     *
     * @param background colore ARGB del fondo su cui calcolare il contrasto
     * @param accent colore ARGB principale
     * @param text colore ARGB del testo principale
     * @param secondaryText colore ARGB del testo secondario
     * @param card colore ARGB dei riquadri ordinari
     * @param highlight colore ARGB dei riquadri in evidenza
     * @param border colore ARGB dei bordi e divisori
     */
    public Palette(
            int background,
            int accent,
            int text,
            int secondaryText,
            int card,
            int highlight,
            int border) {
        this.background = background;
        this.accent = accent;
        this.text = text;
        this.secondaryText = secondaryText;
        this.card = card;
        this.highlight = highlight;
        this.border = border;
    }
}
