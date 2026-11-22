package avilaos.enums;

/** Sentido en que se mueve el cabezal del disco. */
public enum DireccionCabezal {
    SUBIENDO("Hacia bloques mayores", "↑", 1),
    BAJANDO("Hacia bloques menores", "↓", -1);

    private final String nombre;
    private final String simbolo;
    private final int paso;

    DireccionCabezal(String nombre, String simbolo, int paso) {
        this.nombre = nombre;
        this.simbolo = simbolo;
        this.paso = paso;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Flecha para la interfaz. */
    public String getSimbolo() {
        return simbolo;
    }

    /** +1 si sube, -1 si baja. */
    public int getPaso() {
        return paso;
    }

    /** Sentido contrario. */
    public DireccionCabezal opuesta() {
        return this == SUBIENDO ? BAJANDO : SUBIENDO;
    }
}
