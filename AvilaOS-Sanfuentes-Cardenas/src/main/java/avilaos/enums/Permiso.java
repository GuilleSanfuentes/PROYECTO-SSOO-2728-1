package avilaos.enums;

/** Visibilidad de un archivo o directorio para los usuarios que no son su dueño. */
public enum Permiso {
    PUBLICO("Público", "public"),
    PRIVADO("Privado", "private");

    private final String nombre;
    private final String json;

    Permiso(String nombre, String json) {
        this.nombre = nombre;
        this.json = json;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Valor en el JSON (public o private). */
    public String getJson() {
        return json;
    }

    /** Permiso a partir del texto del JSON (public / private, sin importar mayúsculas); null si no existe. */
    public static Permiso desdeJson(String texto) {
        if (texto != null) {
            for (Permiso permiso : values()) {
                if (permiso.json.equalsIgnoreCase(texto.trim())) {
                    return permiso;
                }
            }
        }
        return null;
    }
}
