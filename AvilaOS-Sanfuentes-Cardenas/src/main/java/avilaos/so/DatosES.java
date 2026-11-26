package avilaos.so;

import avilaos.enums.Permiso;
import avilaos.enums.TipoOperacionES;
import avilaos.so.archivos.Usuario;

/** Lo que pide un proceso de E/S de archivos; nuevoNombre es null si no renombra y permiso solo se usa al crear. */
public record DatosES(Usuario usuario, TipoOperacionES operacion, String ruta, int bloquesArchivo, String nuevoNombre,
        Permiso permiso) {

    /** CREATE sin bloques significa crear un directorio. */
    public boolean creaDirectorio() {
        return operacion == TipoOperacionES.CREAR && bloquesArchivo == 0;
    }

    /** Indica si es una actualización que cambia el nombre. */
    public boolean renombra() {
        return operacion == TipoOperacionES.ACTUALIZAR && nuevoNombre != null;
    }

    /** Texto corto para el log: "Leer /dir/a" o "Renombrar /dir/a a b". */
    public String describir() {
        return renombra() ? "Renombrar " + ruta + " a " + nuevoNombre : operacion.getNombre() + " " + ruta;
    }
}
