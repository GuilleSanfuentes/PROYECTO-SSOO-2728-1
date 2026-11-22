package avilaos.so.archivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoEntrada;

/** Entrada ya validada del JSON del sistema de archivos; primerBloque -1 si no lo indica y cadena null si no la trae. */
public record DatosEntrada(int indice, String ruta, TipoEntrada tipo, String dueno, Permiso permiso, int primerBloque,
        int bloques, String creador, int[] cadena) {
}
