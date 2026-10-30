package avilaos.enums;

/** Tipos de semáforo de ÁvilaOS. */
public enum TipoSemaforo {
    CONTEO("Conteo"),
    BINARIO("Binario");

    private final String nombre;

    TipoSemaforo(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }
}
