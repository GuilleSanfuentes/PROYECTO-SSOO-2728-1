package avilaos.vista.gui;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;

/** Foto inmutable del sistema de archivos: todas las entradas en preorden (para el JTree) y los dueños conocidos. */
public record FotoArchivos(InfoEntrada[] entradas, String[] usuarios) {

    /** Toma la foto del sistema de archivos (null si no hay). */
    public static FotoArchivos de(SistemaArchivos fs) {
        if (fs == null) {
            return null;
        }
        ListaEnlazada<InfoEntrada> lista = fs.listarTodo();
        InfoEntrada[] entradas = new InfoEntrada[lista.tamano()];
        Nodo<InfoEntrada> nodo = lista.getCabeza();
        for (int i = 0; i < entradas.length && nodo != null; i++) {
            entradas[i] = nodo.getDato();
            nodo = nodo.getSiguiente();
        }
        ListaEnlazada<String> duenos = fs.duenos();
        int comunes = 0;
        Nodo<String> d = duenos.getCabeza();
        while (d != null) {
            comunes += Usuario.NOMBRE_ADMIN.equals(d.getDato()) ? 0 : 1;
            d = d.getSiguiente();
        }
        String[] usuarios = new String[comunes];
        int k = 0;
        d = duenos.getCabeza();
        while (d != null) {
            if (!Usuario.NOMBRE_ADMIN.equals(d.getDato())) {
                usuarios[k++] = d.getDato();
            }
            d = d.getSiguiente();
        }
        return new FotoArchivos(entradas, usuarios);
    }

    /** Solo los archivos, en el mismo orden (filas de la tabla de asignación). */
    public InfoEntrada[] archivos() {
        int n = 0;
        for (InfoEntrada e : entradas) {
            n += e.esDirectorio() ? 0 : 1;
        }
        InfoEntrada[] archivos = new InfoEntrada[n];
        int k = 0;
        for (InfoEntrada e : entradas) {
            if (!e.esDirectorio()) {
                archivos[k++] = e;
            }
        }
        return archivos;
    }

    /** Entrada con esa ruta, o null. */
    public InfoEntrada buscar(String ruta) {
        for (InfoEntrada e : entradas) {
            if (e.ruta().equals(ruta)) {
                return e;
            }
        }
        return null;
    }

    /** Firma corta de la estructura (rutas y nombres) para saber si el árbol cambió y hay que redibujarlo. */
    public String firma() {
        StringBuilder sb = new StringBuilder();
        for (InfoEntrada e : entradas) {
            sb.append(e.id()).append(':').append(e.ruta()).append(';');
        }
        return sb.toString();
    }
}
