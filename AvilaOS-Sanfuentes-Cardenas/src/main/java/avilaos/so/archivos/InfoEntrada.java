package avilaos.so.archivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoEntrada;

/** Foto inmutable de un archivo o directorio, para la interfaz (JTree y tabla) y las pruebas. */
public record InfoEntrada(int id, String ruta, String nombre, TipoEntrada tipo, String dueno, Permiso permiso,
        int primerBloque, int bloques, String creador, String ultimaOperacion, int profundidad) {

    /** Indica si es un directorio. */
    public boolean esDirectorio() {
        return tipo == TipoEntrada.DIRECTORIO;
    }
}
