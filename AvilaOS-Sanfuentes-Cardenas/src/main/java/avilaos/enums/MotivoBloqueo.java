package avilaos.enums;

/** Causa por la que un proceso está en estado Bloqueado. */
public enum MotivoBloqueo {
    NINGUNO("Ninguno"),
    ENTRADA_SALIDA("E/S"),
    SEMAFORO("Semáforo"),
    RED("Latencia de red");

    private final String nombre;

    MotivoBloqueo(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }
}
