package avilaos.enums;

/** Operación que una solicitud pide al disco: las CRUD sobre archivos y el intercambio de procesos. */
public enum TipoOperacionES {
    CREAR("Crear", true, "CREATE"),
    LEER("Leer", false, "READ"),
    ACTUALIZAR("Actualizar", true, "UPDATE"),
    ELIMINAR("Eliminar", true, "DELETE"),
    SUSPENDER("Suspender", true, null),
    REACTIVAR("Reactivar", false, null);

    private final String nombre;
    private final boolean escritura;
    private final String json;

    TipoOperacionES(String nombre, boolean escritura, String json) {
        this.nombre = nombre;
        this.escritura = escritura;
        this.json = json;
    }

    /** Valor en el JSON de procesos (CREATE, READ, UPDATE, DELETE); null en las de intercambio. */
    public String getJson() {
        return json;
    }

    /** Operación CRUD a partir del texto del JSON, sin importar mayúsculas; null si no existe. */
    public static TipoOperacionES desdeJson(String texto) {
        if (texto != null) {
            for (TipoOperacionES operacion : values()) {
                if (operacion.json != null && operacion.json.equalsIgnoreCase(texto.trim())) {
                    return operacion;
                }
            }
        }
        return null;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si la operación escribe en el disco (si no, lee). */
    public boolean isEscritura() {
        return escritura;
    }

    /** Indica si la operación es del intercambio (suspender o reactivar un proceso). */
    public boolean isIntercambio() {
        return this == SUSPENDER || this == REACTIVAR;
    }
}
