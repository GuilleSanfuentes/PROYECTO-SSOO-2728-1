package avilaos.vista.gui;

import avilaos.enums.TipoOperacionES;

/** Opciones del clic derecho sobre el árbol de archivos; cada una crea un proceso de E/S con su operación. */
public enum AccionArchivo {
    LEER("Leer", TipoOperacionES.LEER),
    ACTUALIZAR("Actualizar (escribir)", TipoOperacionES.ACTUALIZAR),
    RENOMBRAR("Renombrar...", TipoOperacionES.ACTUALIZAR),
    ELIMINAR("Eliminar", TipoOperacionES.ELIMINAR),
    CREAR_ARCHIVO("Crear archivo aquí...", TipoOperacionES.CREAR),
    CREAR_DIRECTORIO("Crear directorio aquí...", TipoOperacionES.CREAR);

    private final String texto;
    private final TipoOperacionES operacion;

    AccionArchivo(String texto, TipoOperacionES operacion) {
        this.texto = texto;
        this.operacion = operacion;
    }

    /** Texto del menú. */
    public String getTexto() {
        return texto;
    }

    /** Operación de E/S que pide el proceso. */
    public TipoOperacionES getOperacion() {
        return operacion;
    }

    /** Indica si crea algo nuevo dentro del directorio seleccionado. */
    public boolean creaAlgo() {
        return this == CREAR_ARCHIVO || this == CREAR_DIRECTORIO;
    }
}
