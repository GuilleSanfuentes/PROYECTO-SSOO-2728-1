package avilaos.so.archivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoEntrada;

/** Archivo o directorio del árbol; solo lo modifica el SistemaArchivos con su mutex tomado. */
final class EntradaFS {

    final int id;
    final TipoEntrada tipo;
    final String dueno;
    final Permiso permiso;
    final int primerBloque;
    final int bloques;
    final String creador;
    String nombre;
    String ultimaOperacion;

    EntradaFS(int id, TipoEntrada tipo, String nombre, String dueno, Permiso permiso, int primerBloque, int bloques, String creador) {
        this.id = id;
        this.tipo = tipo;
        this.nombre = nombre;
        this.dueno = dueno;
        this.permiso = permiso;
        this.primerBloque = primerBloque;
        this.bloques = bloques;
        this.creador = creador;
        this.ultimaOperacion = "Crear";
    }

    /** Indica si es un directorio. */
    boolean esDirectorio() {
        return tipo == TipoEntrada.DIRECTORIO;
    }
}
