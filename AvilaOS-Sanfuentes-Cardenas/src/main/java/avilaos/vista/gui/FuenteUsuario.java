package avilaos.vista.gui;

import avilaos.so.archivos.Usuario;

/** Quien sabe qué usuario está activo en la interfaz (el selector de modo de la barra). */
public interface FuenteUsuario {

    /** Usuario activo: el administrador o un usuario común. */
    Usuario actual();
}
