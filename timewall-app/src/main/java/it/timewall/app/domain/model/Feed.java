package it.timewall.app.domain.model;

public final class Feed {
    public final String name, address;
    public final int color;

    /**
     * Memorizza nome, indirizzo e colore assegnati a un calendario configurato.
     *
     * @param name nome della voce, del campo o della località
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param color colore ARGB da associare al contenuto
     */
    public Feed(String name, String address, int color) {
        this.name = name;
        this.address = address;
        this.color = color;
    }
}
