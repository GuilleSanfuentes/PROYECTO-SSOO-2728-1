package avilaos.enums;

/** Tipo de elemento del sistema de archivos; el texto es el que usa el JSON del enunciado. */
public enum TipoEntrada {
    DIRECTORIO("Directorio", "DIRECTORY"),
    ARCHIVO("Archivo", "FILE");

    private final String nombre;
    private final String json;

    TipoEntrada(String nombre, String json) {
        this.nombre = nombre;
        this.json = json;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Valor en el JSON (DIRECTORY o FILE). */
    public String getJson() {
        return json;
    }

    /** Tipo a partir del texto del JSON (DIRECTORY / FILE, sin importar mayúsculas); null si no existe. */
    public static TipoEntrada desdeJson(String texto) {
        if (texto != null) {
            for (TipoEntrada tipo : values()) {
                if (tipo.json.equalsIgnoreCase(texto.trim())) {
                    return tipo;
                }
            }
        }
        return null;
    }
}
