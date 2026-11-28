package it.timewall.app.domain.model;

public final class Feed {
    public final String name, address;
    public final int color;
    private static final String DEVICE_PREFIX = "device:";

    public static String deviceAddress(long id) {
        return DEVICE_PREFIX + id;
    }

    public static long deviceId(String address) {
        if (!address.startsWith(DEVICE_PREFIX)) return -1;
        try {
            long id = Long.parseLong(address.substring(DEVICE_PREFIX.length()));
            return id > 0 && deviceAddress(id).equals(address) ? id : -1;
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    public boolean isDeviceCalendar() {
        return deviceId(address) > 0;
    }

    /**
     * Memorizza nome, indirizzo e colore assegnati a un calendario configurato.
     *
     * @param name nome della voce, del campo o della località
     * @param address URL HTTPS o identificativo del calendario sul dispositivo
     * @param color colore ARGB da associare al contenuto
     */
    public Feed(String name, String address, int color) {
        this.name = name;
        this.address = address;
        this.color = color;
    }
}
