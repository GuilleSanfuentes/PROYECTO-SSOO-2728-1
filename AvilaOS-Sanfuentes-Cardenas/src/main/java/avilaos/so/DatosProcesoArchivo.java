package avilaos.so;

import avilaos.enums.TipoOperacionES;

/** Proceso de E/S de archivos tal como lo pide el JSON del Proyecto 2; nuevoNombre es null si no se renombra. */
public record DatosProcesoArchivo(String nombre, int memoria, int deadline, int instrucciones, String ruta,
        TipoOperacionES operacion, int bloquesArchivo, int prioridad, String nuevoNombre) {

    public static final int PRIORIDAD_POR_DEFECTO = 5;
}
