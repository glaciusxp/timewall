package it.timewall.app.domain.model;

public final class WeatherPlace {
    public final String name;
    public final String detail;
    public final double latitude, longitude;

    /**
     * Memorizza nome, dettagli geografici e coordinate di una località, senza eseguire geocoding.
     *
     * @param name nome della voce, del campo o della località
     * @param detail dettagli geografici da affiancare al nome
     * @param latitude latitudine in gradi
     * @param longitude longitudine in gradi
     */
    public WeatherPlace(String name, String detail, double latitude, double longitude) {
        this.name = name;
        this.detail = detail;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Compone il nome della località con i dettagli geografici, separati da un trattino, omettendo
     * il separatore quando i dettagli sono vuoti.
     *
     * @return nome della località, con dettagli geografici se presenti
     */
    public String label() {
        return detail.isEmpty() ? name : name + " — " + detail;
    }
}
