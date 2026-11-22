package avilaos.enums;

/** Políticas de planificación del disco disponibles en ÁvilaOS. */
public enum PoliticaDiscoTipo {
    FIFO("FIFO"),
    SSTF("SSTF"),
    SCAN("SCAN"),
    C_SCAN("C-SCAN"),
    LOOK("LOOK"),
    C_LOOK("C-LOOK");

    private final String nombre;

    PoliticaDiscoTipo(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Busca la política por su nombre o el del enum (FIFO, C-SCAN, C_SCAN...), sin importar mayúsculas; null si no existe. */
    public static PoliticaDiscoTipo desdeTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().toUpperCase().replace('-', '_');
        for (PoliticaDiscoTipo tipo : values()) {
            if (tipo.name().equals(limpio)) {
                return tipo;
            }
        }
        return null;
    }
}
