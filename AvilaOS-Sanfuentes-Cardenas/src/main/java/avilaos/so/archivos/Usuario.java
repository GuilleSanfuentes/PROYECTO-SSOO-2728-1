package avilaos.so.archivos;

import avilaos.enums.ModoUsuario;

/** Quien pide una operación sobre el sistema de archivos: su nombre (dueño de archivos) y su modo. */
public record Usuario(String nombre, ModoUsuario modo) {

    /** Nombre del administrador en los JSON del enunciado. */
    public static final String NOMBRE_ADMIN = "admin";

    /** El administrador del sistema. */
    public static final Usuario ADMIN = new Usuario(NOMBRE_ADMIN, ModoUsuario.ADMINISTRADOR);

    /** Usuario común con el nombre indicado. */
    public static Usuario comun(String nombre) {
        return new Usuario(nombre, ModoUsuario.USUARIO);
    }

    /** Indica si está en modo administrador. */
    public boolean esAdmin() {
        return modo == ModoUsuario.ADMINISTRADOR;
    }
}
