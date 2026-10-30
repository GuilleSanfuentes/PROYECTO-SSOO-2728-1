package avilaos.enums;

/** Tipos de proceso que puede crear el usuario. */
public enum TipoProceso {
    CPU_BOUND("CPU bound"),
    IO_BOUND("I/O bound"),
    PRODUCTOR("Productor"),
    CONSUMIDOR("Consumidor");

    private final String nombre;

    TipoProceso(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si el proceso trabaja con un buffer. */
    public boolean usaBuffer() {
        return this == PRODUCTOR || this == CONSUMIDOR;
    }
}
