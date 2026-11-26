package avilaos.so.archivos;

import avilaos.enums.Permiso;

/** Archivo cuyos bloques ya están apartados pero que aún no aparece en el árbol, a la espera del disco. */
public record ReservaArchivo(int id, int idPadre, String nombre, String dueno, Permiso permiso, int[] bloques, String creador) {
}
