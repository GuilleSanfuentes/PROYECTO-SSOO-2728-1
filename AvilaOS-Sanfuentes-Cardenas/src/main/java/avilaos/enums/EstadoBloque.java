package avilaos.enums;

/** Estado de un bloque del disco: libre u ocupado, en la zona de archivos o en la de intercambio. */
public enum EstadoBloque {
    LIBRE("Libre", false, false),
    ARCHIVO("Archivo", true, false),
    SWAP_LIBRE("Swap libre", false, true),
    SWAP_OCUPADO("Swap ocupado", true, true);

    private final String nombre;
    private final boolean ocupado;
    private final boolean swap;

    EstadoBloque(String nombre, boolean ocupado, boolean swap) {
        this.nombre = nombre;
        this.ocupado = ocupado;
        this.swap = swap;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si el bloque tiene dueño. */
    public boolean isOcupado() {
        return ocupado;
    }

    /** Indica si el bloque pertenece al área de intercambio. */
    public boolean isSwap() {
        return swap;
    }

    /** Estado libre de la misma zona (archivos o swap). */
    public EstadoBloque libre() {
        return swap ? SWAP_LIBRE : LIBRE;
    }

    /** Estado ocupado de la misma zona (archivos o swap). */
    public EstadoBloque ocupado() {
        return swap ? SWAP_OCUPADO : ARCHIVO;
    }
}
