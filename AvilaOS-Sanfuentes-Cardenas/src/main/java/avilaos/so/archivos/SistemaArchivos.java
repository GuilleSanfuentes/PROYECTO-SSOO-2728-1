package avilaos.so.archivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoEntrada;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.estructuras.NodoArbol;
import avilaos.hardware.Bloque;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.so.disco.AsignadorBloques;
import java.util.concurrent.Semaphore;

/** Sistema de archivos jerárquico sobre el SD: árbol de directorios, CRUD, permisos y asignación encadenada. */
public class SistemaArchivos {

    public static final String RAIZ = "/";
    public static final int MAX_NOMBRE = 40;
    public static final String CREADOR_CARGA = "carga inicial";
    private static final String PREFIJO_BLOQUE = "F";

    private final String nombre;
    private final AsignadorBloques asignador;
    private final Semaphore mutex = new Semaphore(1);
    private final NodoArbol<EntradaFS> raiz;
    private int siguienteId = 1;

    /** Crea un sistema de archivos vacío (solo la raíz, del administrador y pública) sobre el asignador del SD. */
    public SistemaArchivos(String nombre, AsignadorBloques asignador) {
        this.nombre = nombre;
        this.asignador = asignador;
        this.raiz = new NodoArbol<>(new EntradaFS(0, TipoEntrada.DIRECTORIO, "", Usuario.NOMBRE_ADMIN, Permiso.PUBLICO,
                Bloque.FIN, 0, "sistema"));
    }

    // ---------------------------------------------------------------- permisos

    /** Motivo por el que el usuario no puede crear ese tipo de elemento en el directorio, o null si puede. */
    public String motivoCrear(Usuario usuario, String rutaPadre, TipoEntrada tipo) {
        mutex.acquireUninterruptibly();
        try {
            return motivoCrearInterno(usuario, rutaPadre, tipo);
        } finally {
            mutex.release();
        }
    }

    /** Motivo por el que el usuario no puede leer la ruta, o null si puede. */
    public String motivoLeer(Usuario usuario, String ruta) {
        mutex.acquireUninterruptibly();
        try {
            return motivoSobreExistente(usuario, ruta, Accion.LEER);
        } finally {
            mutex.release();
        }
    }

    /** Motivo por el que el usuario no puede cambiar el nombre, o null si puede. */
    public String motivoRenombrar(Usuario usuario, String ruta) {
        mutex.acquireUninterruptibly();
        try {
            return motivoSobreExistente(usuario, ruta, Accion.RENOMBRAR);
        } finally {
            mutex.release();
        }
    }

    /** Motivo por el que el usuario no puede escribir el archivo (actualizar sin renombrar), o null si puede. */
    public String motivoEscribir(Usuario usuario, String ruta) {
        mutex.acquireUninterruptibly();
        try {
            return motivoSobreExistente(usuario, ruta, Accion.ESCRIBIR);
        } finally {
            mutex.release();
        }
    }

    /** Motivo por el que el usuario no puede eliminar la ruta, o null si puede. */
    public String motivoEliminar(Usuario usuario, String ruta) {
        mutex.acquireUninterruptibly();
        try {
            return motivoSobreExistente(usuario, ruta, Accion.ELIMINAR);
        } finally {
            mutex.release();
        }
    }

    // ---------------------------------------------------------------- operaciones

    /** Crea un directorio; el administrador elige su dueño y su permiso. */
    public ResultadoFS crearDirectorio(Usuario usuario, String rutaPadre, String nombreNuevo, String dueno, Permiso permiso) {
        mutex.acquireUninterruptibly();
        try {
            String motivo = motivoCrearInterno(usuario, rutaPadre, TipoEntrada.DIRECTORIO);
            if (motivo == null) {
                motivo = motivoNuevo(rutaPadre, nombreNuevo, dueno, permiso);
            }
            if (motivo != null) {
                return rechazo("crear directorio", motivo);
            }
            NodoArbol<EntradaFS> padre = buscarNodo(normalizar(rutaPadre));
            EntradaFS entrada = new EntradaFS(siguienteId++, TipoEntrada.DIRECTORIO, nombreNuevo, dueno.trim(), permiso,
                    Bloque.FIN, 0, usuario.nombre());
            NodoArbol<EntradaFS> nodo = new NodoArbol<>(entrada);
            padre.agregarHijo(nodo);
            Debug.ok(nombre, "directorio creado: " + ruta(nodo) + " (dueño " + entrada.dueno + ")");
            return ResultadoFS.hecho(info(nodo), 0);
        } finally {
            mutex.release();
        }
    }

    /** Crea un archivo y le asigna sus bloques encadenados; dueño null = quien lo crea. Un usuario común solo crea a su nombre. */
    public ResultadoFS crearArchivo(Usuario usuario, String rutaPadre, String nombreNuevo, int bloques, String dueno,
            Permiso permiso, String creador) {
        mutex.acquireUninterruptibly();
        try {
            String motivo = motivoCrearInterno(usuario, rutaPadre, TipoEntrada.ARCHIVO);
            if (dueno == null && usuario != null) {
                dueno = usuario.nombre();
            }
            if (motivo == null && !usuario.esAdmin() && !usuario.nombre().equals(dueno)) {
                motivo = "el usuario " + usuario.nombre() + " solo puede crear archivos a su nombre";
            }
            if (motivo == null) {
                motivo = motivoNuevo(rutaPadre, nombreNuevo, dueno, permiso);
            }
            if (motivo == null && bloques <= 0) {
                motivo = "el tamaño debe ser de al menos 1 bloque (pidió " + bloques + ")";
            }
            if (motivo != null) {
                return rechazo("crear archivo", motivo);
            }
            return agregarArchivo(normalizar(rutaPadre), nombreNuevo, dueno, permiso, bloques, Bloque.FIN, null, creador);
        } finally {
            mutex.release();
        }
    }

    /** Lectura: no cambia la estructura, solo deja constancia de la última operación. */
    public ResultadoFS leer(Usuario usuario, String ruta) {
        return operarSobreExistente(usuario, ruta, Accion.LEER, null);
    }

    /** Actualizar sin nombre nuevo: escritura sobre los bloques del archivo (no cambia la estructura). */
    public ResultadoFS escribir(Usuario usuario, String ruta) {
        return operarSobreExistente(usuario, ruta, Accion.ESCRIBIR, null);
    }

    /** Actualizar con nombre nuevo: cambia el nombre del archivo o directorio (solo el administrador). */
    public ResultadoFS renombrar(Usuario usuario, String ruta, String nuevoNombre) {
        return operarSobreExistente(usuario, ruta, Accion.RENOMBRAR, nuevoNombre);
    }

    /** Elimina el archivo o el directorio con todo su contenido y libera sus bloques en el SD. */
    public ResultadoFS eliminar(Usuario usuario, String ruta) {
        return operarSobreExistente(usuario, ruta, Accion.ELIMINAR, null);
    }

    /** Carga entradas ya validadas del JSON: primero los directorios por profundidad, luego los archivos. */
    public int cargar(ListaEnlazada<DatosEntrada> entradas, ListaEnlazada<String> problemas) {
        int cargadas = 0;
        int maxProfundidad = 0;
        Nodo<DatosEntrada> nodo = entradas.getCabeza();
        while (nodo != null) {
            maxProfundidad = Math.max(maxProfundidad, profundidadDe(nodo.getDato().ruta()));
            nodo = nodo.getSiguiente();
        }
        for (int nivel = 1; nivel <= maxProfundidad; nivel++) {
            nodo = entradas.getCabeza();
            while (nodo != null) {
                DatosEntrada d = nodo.getDato();
                if (d.tipo() == TipoEntrada.DIRECTORIO && profundidadDe(d.ruta()) == nivel) {
                    cargadas += cargarUna(d, problemas) ? 1 : 0;
                }
                nodo = nodo.getSiguiente();
            }
        }
        nodo = entradas.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().tipo() == TipoEntrada.ARCHIVO) {
                cargadas += cargarUna(nodo.getDato(), problemas) ? 1 : 0;
            }
            nodo = nodo.getSiguiente();
        }
        Debug.ok(nombre, "carga del sistema de archivos: " + cargadas + " de " + entradas.tamano() + " entradas");
        return cargadas;
    }

    // ---------------------------------------------------------------- consultas

    /** Foto de la entrada en la ruta, o null si no existe. */
    public InfoEntrada buscar(String ruta) {
        mutex.acquireUninterruptibly();
        try {
            NodoArbol<EntradaFS> nodo = buscarNodo(normalizar(ruta));
            return nodo == null ? null : info(nodo);
        } finally {
            mutex.release();
        }
    }

    /** Indica si la ruta existe. */
    public boolean existe(String ruta) {
        return buscar(ruta) != null;
    }

    /** Números de los bloques del archivo en orden de la cadena; vacío si no es un archivo. */
    public int[] bloquesDe(String ruta) {
        mutex.acquireUninterruptibly();
        try {
            NodoArbol<EntradaFS> nodo = buscarNodo(normalizar(ruta));
            if (nodo == null || nodo.getDato().esDirectorio()) {
                return new int[0];
            }
            ListaEnlazada<Integer> cadena = asignador.recorrer(nodo.getDato().primerBloque);
            int[] bloques = new int[cadena.tamano()];
            for (int i = 0; i < bloques.length; i++) {
                bloques[i] = cadena.obtener(i);
            }
            return bloques;
        } finally {
            mutex.release();
        }
    }

    /** Todas las entradas en preorden (raíz primero), como se dibujan en el JTree. */
    public ListaEnlazada<InfoEntrada> listarTodo() {
        ListaEnlazada<InfoEntrada> lista = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            preorden(raiz, lista, false);
        } finally {
            mutex.release();
        }
        return lista;
    }

    /** Solo los archivos, en preorden (filas de la tabla de asignación). */
    public ListaEnlazada<InfoEntrada> listarArchivos() {
        ListaEnlazada<InfoEntrada> lista = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            preorden(raiz, lista, true);
        } finally {
            mutex.release();
        }
        return lista;
    }

    /** Hijos directos de un directorio, en orden de creación; vacío si no existe o es un archivo. */
    public ListaEnlazada<InfoEntrada> hijos(String ruta) {
        ListaEnlazada<InfoEntrada> lista = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            NodoArbol<EntradaFS> nodo = buscarNodo(normalizar(ruta));
            if (nodo != null) {
                Nodo<NodoArbol<EntradaFS>> hijo = nodo.getHijos().getCabeza();
                while (hijo != null) {
                    lista.agregarFinal(info(hijo.getDato()));
                    hijo = hijo.getSiguiente();
                }
            }
        } finally {
            mutex.release();
        }
        return lista;
    }

    /** Nombres de los dueños que aparecen en el sistema (para el selector de usuario), sin repetir. */
    public ListaEnlazada<String> duenos() {
        ListaEnlazada<String> nombres = new ListaEnlazada<>();
        Nodo<InfoEntrada> nodo = listarTodo().getCabeza();
        while (nodo != null) {
            if (!contieneTexto(nombres, nodo.getDato().dueno())) {
                nombres.agregarFinal(nodo.getDato().dueno());
            }
            nodo = nodo.getSiguiente();
        }
        return nombres;
    }

    /** Indica si la lista tiene un texto igual (por contenido, no por referencia). */
    private static boolean contieneTexto(ListaEnlazada<String> lista, String texto) {
        Nodo<String> nodo = lista.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().equals(texto)) {
                return true;
            }
            nodo = nodo.getSiguiente();
        }
        return false;
    }

    /** Ruta del archivo dueño de un bloque (su marca "F7" en el disco), o null si no es de ningún archivo. */
    public String rutaDeBloque(String duenoBloque) {
        if (duenoBloque == null || !duenoBloque.startsWith(PREFIJO_BLOQUE)) {
            return null;
        }
        mutex.acquireUninterruptibly();
        try {
            NodoArbol<EntradaFS> nodo = buscarPorMarca(raiz, duenoBloque);
            return nodo == null ? null : ruta(nodo);
        } finally {
            mutex.release();
        }
    }

    /** Comprueba que cada archivo tiene una cadena de su largo y con su marca, y que no sobran bloques de archivo. */
    public boolean verificarIntegridad() {
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<InfoEntrada> archivos = new ListaEnlazada<>();
            preorden(raiz, archivos, true);
            int bloquesArchivos = 0;
            DiscoSD disco = asignador.getDisco();
            Nodo<InfoEntrada> nodo = archivos.getCabeza();
            while (nodo != null) {
                InfoEntrada a = nodo.getDato();
                ListaEnlazada<Integer> cadena = asignador.recorrer(a.primerBloque());
                if (cadena.tamano() != a.bloques()) {
                    return integridadRota(a.ruta() + " tiene " + cadena.tamano() + " bloques en el disco y " + a.bloques() + " en el árbol");
                }
                Nodo<Integer> b = cadena.getCabeza();
                while (b != null) {
                    if (!marca(a.id()).equals(disco.leer(b.getDato()).dueno())) {
                        return integridadRota("el bloque " + b.getDato() + " de " + a.ruta() + " tiene otra marca");
                    }
                    b = b.getSiguiente();
                }
                bloquesArchivos += a.bloques();
                nodo = nodo.getSiguiente();
            }
            int ocupados = disco.getBloquesArchivos() - disco.contarLibres(false);
            if (ocupados != bloquesArchivos) {
                return integridadRota("hay " + ocupados + " bloques de archivo ocupados y los archivos suman " + bloquesArchivos);
            }
            return true;
        } finally {
            mutex.release();
        }
    }

    // ---------------------------------------------------------------- rutas

    /** Ruta absoluta sin "/" final ni tramos vacíos ("/a/b"), "/" para la raíz, o null si no es válida. */
    public static String normalizar(String ruta) {
        if (ruta == null) {
            return null;
        }
        String r = ruta.trim();
        if (!r.startsWith(RAIZ)) {
            return null;
        }
        if (r.equals(RAIZ)) {
            return RAIZ;
        }
        if (r.endsWith(RAIZ)) {
            r = r.substring(0, r.length() - 1);
        }
        String[] partes = r.substring(1).split(RAIZ, -1);
        for (String parte : partes) {
            if (motivoNombre(parte) != null) {
                return null;
            }
        }
        return r;
    }

    /** Motivo por el que el nombre no es válido, o null si sirve. */
    public static String motivoNombre(String nombreNuevo) {
        if (nombreNuevo == null || nombreNuevo.isEmpty()) {
            return "el nombre está vacío";
        }
        if (!nombreNuevo.equals(nombreNuevo.trim())) {
            return "el nombre no puede empezar ni terminar con espacios";
        }
        if (nombreNuevo.equals(".") || nombreNuevo.equals("..")) {
            return "'" + nombreNuevo + "' está reservado";
        }
        if (nombreNuevo.length() > MAX_NOMBRE) {
            return "el nombre tiene más de " + MAX_NOMBRE + " caracteres";
        }
        for (int i = 0; i < nombreNuevo.length(); i++) {
            char c = nombreNuevo.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                return "el nombre no puede contener '" + (Character.isISOControl(c) ? "control" : String.valueOf(c)) + "'";
            }
        }
        return null;
    }

    /** Directorio padre de una ruta normalizada ("/" para los de primer nivel). */
    public static String padreDe(String ruta) {
        int corte = ruta.lastIndexOf('/');
        return corte <= 0 ? RAIZ : ruta.substring(0, corte);
    }

    /** Último tramo de una ruta normalizada. */
    public static String nombreDe(String ruta) {
        return ruta.substring(ruta.lastIndexOf('/') + 1);
    }

    /** Marca que llevan en el disco los bloques del archivo con ese id. */
    public static String marca(int id) {
        return PREFIJO_BLOQUE + id;
    }

    // ---------------------------------------------------------------- internos

    /** Acciones sobre algo que ya existe. */
    private enum Accion {
        LEER("leer"),
        ESCRIBIR("modificar"),
        RENOMBRAR("cambiar el nombre de"),
        ELIMINAR("eliminar");

        private final String verbo;

        Accion(String verbo) {
            this.verbo = verbo;
        }
    }

    /** Comprueba permisos y aplica la acción con el mutex tomado. */
    private ResultadoFS operarSobreExistente(Usuario usuario, String ruta, Accion accion, String nuevoNombre) {
        mutex.acquireUninterruptibly();
        try {
            String motivo = motivoSobreExistente(usuario, ruta, accion);
            if (motivo != null) {
                return rechazo(accion.verbo, motivo);
            }
            NodoArbol<EntradaFS> nodo = buscarNodo(normalizar(ruta));
            EntradaFS entrada = nodo.getDato();
            switch (accion) {
                case LEER -> {
                    entrada.ultimaOperacion = "Leer";
                    return ResultadoFS.hecho(info(nodo), entrada.bloques);
                }
                case ESCRIBIR -> {
                    entrada.ultimaOperacion = "Actualizar";
                    Debug.ok(nombre, usuario.nombre() + " actualiza " + ruta(nodo));
                    return ResultadoFS.hecho(info(nodo), entrada.bloques);
                }
                case RENOMBRAR -> {
                    String problema = motivoNombre(nuevoNombre);
                    if (problema == null && buscarHijo(nodo.getPadre(), nuevoNombre) != null) {
                        problema = "ya existe '" + nuevoNombre + "' en " + ruta(nodo.getPadre());
                    }
                    if (problema != null) {
                        return rechazo("cambiar el nombre", problema);
                    }
                    String anterior = ruta(nodo);
                    entrada.nombre = nuevoNombre;
                    entrada.ultimaOperacion = "Actualizar (renombrar)";
                    Debug.ok(nombre, "renombrado: " + anterior + " -> " + ruta(nodo));
                    return ResultadoFS.hecho(info(nodo), entrada.bloques);
                }
                default -> {
                    InfoEntrada borrada = info(nodo);
                    int liberados = liberarSubarbol(nodo);
                    nodo.getPadre().quitarHijo(nodo);
                    Debug.ok(nombre, "eliminado " + borrada.ruta() + ": " + liberados + " bloques liberados");
                    return ResultadoFS.hecho(borrada, liberados);
                }
            }
        } finally {
            mutex.release();
        }
    }

    /** Reglas de permisos sobre una entrada existente (llamar con el mutex tomado). */
    private String motivoSobreExistente(Usuario usuario, String ruta, Accion accion) {
        if (usuario == null) {
            Debug.error(nombre, "operación sin usuario sobre " + ruta);
            return "no se indicó el usuario";
        }
        String normal = normalizar(ruta);
        if (normal == null) {
            return "la ruta '" + ruta + "' no es válida";
        }
        NodoArbol<EntradaFS> nodo = buscarNodo(normal);
        if (nodo == null) {
            return normal + " no existe";
        }
        EntradaFS e = nodo.getDato();
        if (nodo == raiz && (accion == Accion.RENOMBRAR || accion == Accion.ELIMINAR)) {
            return "no se puede " + accion.verbo + " la raíz";
        }
        if (accion == Accion.ESCRIBIR && e.esDirectorio()) {
            return normal + " es un directorio: se renombra o se elimina, no se escribe";
        }
        if (usuario.esAdmin()) {
            return null;
        }
        String quien = "el usuario " + usuario.nombre();
        return switch (accion) {
            case LEER -> e.esDirectorio() || e.dueno.equals(usuario.nombre()) || e.permiso == Permiso.PUBLICO ? null
                    : quien + " no puede leer " + normal + ": es privado de " + e.dueno;
            case RENOMBRAR -> "solo el administrador puede cambiar nombres";
            case ESCRIBIR -> e.dueno.equals(usuario.nombre()) ? null
                    : quien + " solo puede modificar sus propios archivos (" + normal + " es de " + e.dueno + ")";
            case ELIMINAR -> e.esDirectorio() ? "solo el administrador puede eliminar directorios"
                    : e.dueno.equals(usuario.nombre()) ? null
                    : quien + " solo puede eliminar sus propios archivos (" + normal + " es de " + e.dueno + ")";
        };
    }

    /** Reglas de permisos para crear (llamar con el mutex tomado). */
    private String motivoCrearInterno(Usuario usuario, String rutaPadre, TipoEntrada tipo) {
        if (usuario == null || tipo == null) {
            Debug.error(nombre, "creación sin usuario o sin tipo en " + rutaPadre);
            return "faltan datos para crear";
        }
        String normal = normalizar(rutaPadre);
        NodoArbol<EntradaFS> padre = normal == null ? null : buscarNodo(normal);
        if (padre == null) {
            return "el directorio " + rutaPadre + " no existe";
        }
        if (!padre.getDato().esDirectorio()) {
            return normal + " es un archivo, no un directorio";
        }
        if (usuario.esAdmin()) {
            return null;
        }
        if (tipo == TipoEntrada.DIRECTORIO) {
            return "solo el administrador puede crear directorios";
        }
        return padre.getDato().dueno.equals(usuario.nombre()) ? null
                : "el usuario " + usuario.nombre() + " solo puede crear archivos en sus propios directorios (" + normal
                + " es de " + padre.getDato().dueno + ")";
    }

    /** Validaciones de un elemento nuevo: nombre, que no exista, dueño y permiso (llamar con el mutex tomado). */
    private String motivoNuevo(String rutaPadre, String nombreNuevo, String dueno, Permiso permiso) {
        String problema = motivoNombre(nombreNuevo);
        if (problema != null) {
            return problema;
        }
        if (buscarHijo(buscarNodo(normalizar(rutaPadre)), nombreNuevo) != null) {
            return "ya existe '" + nombreNuevo + "' en " + normalizar(rutaPadre);
        }
        if (dueno == null || dueno.isBlank()) {
            return "falta el dueño";
        }
        if (permiso == null) {
            return "falta el permiso (público o privado)";
        }
        return null;
    }

    /** Pide los bloques al asignador (cadena exacta, desde un bloque o los primeros libres) y cuelga el archivo. */
    private ResultadoFS agregarArchivo(String rutaPadre, String nombreNuevo, String dueno, Permiso permiso, int bloques,
            int primerBloque, int[] cadena, String creador) {
        int id = siguienteId;
        String marca = marca(id);
        int primero = cadena != null ? asignador.asignarExacta(marca, cadena)
                : primerBloque == Bloque.FIN ? asignador.asignar(marca, bloques) : asignador.asignarDesde(marca, bloques, primerBloque);
        if (primero == Bloque.FIN) {
            DiscoSD disco = asignador.getDisco();
            String motivo = cadena != null ? "algún bloque de su cadena guardada ya está ocupado o no es válido"
                    : primerBloque != Bloque.FIN && disco.esPosicionValida(primerBloque) && !disco.esSwap(primerBloque)
                    && disco.leer(primerBloque).isOcupado()
                    ? "el bloque " + primerBloque + " ya está ocupado"
                    : "no hay bloques libres suficientes (pide " + bloques + ", libres " + disco.contarLibres(false) + ")";
            return rechazo("crear archivo", motivo);
        }
        siguienteId++;
        EntradaFS entrada = new EntradaFS(id, TipoEntrada.ARCHIVO, nombreNuevo, dueno.trim(), permiso, primero, bloques,
                creador == null || creador.isBlank() ? "desconocido" : creador);
        NodoArbol<EntradaFS> nodo = new NodoArbol<>(entrada);
        buscarNodo(rutaPadre).agregarHijo(nodo);
        Debug.ok(nombre, "archivo creado: " + ruta(nodo) + " (" + bloques + " bloques desde el " + primero + ", dueño " + entrada.dueno + ")");
        return ResultadoFS.hecho(info(nodo), bloques);
    }

    /** Aplica una entrada del JSON; si falla anota el problema y devuelve false. */
    private boolean cargarUna(DatosEntrada d, ListaEnlazada<String> problemas) {
        String ruta = normalizar(d.ruta());
        if (ruta == null || ruta.equals(RAIZ)) {
            problemas.agregarFinal("entrada " + d.indice() + " (" + d.ruta() + "): la ruta no es válida");
            return false;
        }
        ResultadoFS resultado;
        mutex.acquireUninterruptibly();
        try {
            String padre = padreDe(ruta);
            NodoArbol<EntradaFS> nodoPadre = buscarNodo(padre);
            String motivo = nodoPadre == null ? "el directorio padre " + padre + " no existe"
                    : !nodoPadre.getDato().esDirectorio() ? padre + " es un archivo, no un directorio"
                    : motivoNuevo(padre, nombreDe(ruta), d.dueno(), d.permiso());
            if (motivo != null) {
                resultado = ResultadoFS.rechazo(motivo);
            } else if (d.tipo() == TipoEntrada.DIRECTORIO) {
                EntradaFS entrada = new EntradaFS(siguienteId++, TipoEntrada.DIRECTORIO, nombreDe(ruta), d.dueno().trim(), d.permiso(),
                        Bloque.FIN, 0, d.creador() == null ? CREADOR_CARGA : d.creador());
                nodoPadre.agregarHijo(new NodoArbol<>(entrada));
                resultado = ResultadoFS.hecho(null, 0);
            } else {
                resultado = agregarArchivo(padre, nombreDe(ruta), d.dueno(), d.permiso(), d.bloques(), d.primerBloque(),
                        d.cadena(), d.creador() == null ? CREADOR_CARGA : d.creador());
            }
        } finally {
            mutex.release();
        }
        if (!resultado.exito()) {
            problemas.agregarFinal("entrada " + d.indice() + " (" + d.ruta() + "): " + resultado.motivo());
        }
        return resultado.exito();
    }

    /** Libera los bloques de todos los archivos del subárbol (llamar con el mutex tomado). */
    private int liberarSubarbol(NodoArbol<EntradaFS> nodo) {
        if (!nodo.getDato().esDirectorio()) {
            return asignador.liberarCadena(nodo.getDato().primerBloque);
        }
        int liberados = 0;
        Nodo<NodoArbol<EntradaFS>> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            liberados += liberarSubarbol(hijo.getDato());
            hijo = hijo.getSiguiente();
        }
        return liberados;
    }

    /** Nodo de una ruta normalizada, o null (llamar con el mutex tomado). */
    private NodoArbol<EntradaFS> buscarNodo(String rutaNormal) {
        if (rutaNormal == null) {
            return null;
        }
        if (rutaNormal.equals(RAIZ)) {
            return raiz;
        }
        NodoArbol<EntradaFS> actual = raiz;
        for (String parte : rutaNormal.substring(1).split(RAIZ)) {
            actual = buscarHijo(actual, parte);
            if (actual == null) {
                return null;
            }
        }
        return actual;
    }

    /** Hijo directo con ese nombre, o null. */
    private static NodoArbol<EntradaFS> buscarHijo(NodoArbol<EntradaFS> padre, String nombreHijo) {
        if (padre == null) {
            return null;
        }
        Nodo<NodoArbol<EntradaFS>> hijo = padre.getHijos().getCabeza();
        while (hijo != null) {
            if (hijo.getDato().getDato().nombre.equals(nombreHijo)) {
                return hijo.getDato();
            }
            hijo = hijo.getSiguiente();
        }
        return null;
    }

    /** Busca en el subárbol el archivo con la marca de bloques indicada. */
    private static NodoArbol<EntradaFS> buscarPorMarca(NodoArbol<EntradaFS> nodo, String marcaBloque) {
        if (!nodo.getDato().esDirectorio() && marca(nodo.getDato().id).equals(marcaBloque)) {
            return nodo;
        }
        Nodo<NodoArbol<EntradaFS>> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            NodoArbol<EntradaFS> encontrado = buscarPorMarca(hijo.getDato(), marcaBloque);
            if (encontrado != null) {
                return encontrado;
            }
            hijo = hijo.getSiguiente();
        }
        return null;
    }

    /** Recorre en preorden agregando fotos; con soloArchivos omite los directorios. */
    private void preorden(NodoArbol<EntradaFS> nodo, ListaEnlazada<InfoEntrada> lista, boolean soloArchivos) {
        if (!soloArchivos || !nodo.getDato().esDirectorio()) {
            lista.agregarFinal(info(nodo));
        }
        Nodo<NodoArbol<EntradaFS>> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            preorden(hijo.getDato(), lista, soloArchivos);
            hijo = hijo.getSiguiente();
        }
    }

    /** Ruta absoluta de un nodo, armada subiendo hasta la raíz. */
    private String ruta(NodoArbol<EntradaFS> nodo) {
        if (nodo == raiz) {
            return RAIZ;
        }
        StringBuilder sb = new StringBuilder();
        NodoArbol<EntradaFS> actual = nodo;
        while (actual != null && actual != raiz) {
            sb.insert(0, "/" + actual.getDato().nombre);
            actual = actual.getPadre();
        }
        return sb.toString();
    }

    /** Foto inmutable del nodo. */
    private InfoEntrada info(NodoArbol<EntradaFS> nodo) {
        EntradaFS e = nodo.getDato();
        return new InfoEntrada(e.id, ruta(nodo), nodo == raiz ? RAIZ : e.nombre, e.tipo, e.dueno, e.permiso, e.primerBloque,
                e.bloques, e.creador, e.ultimaOperacion, nodo.profundidad());
    }

    /** Cantidad de tramos de una ruta ("/a/b" = 2); 0 si no es válida. */
    private static int profundidadDe(String ruta) {
        String normal = normalizar(ruta);
        if (normal == null || normal.equals(RAIZ)) {
            return 0;
        }
        return normal.substring(1).split(RAIZ).length;
    }

    /** Registra el rechazo (situación normal, INFO) y lo devuelve con su motivo. */
    private ResultadoFS rechazo(String accion, String motivo) {
        Debug.info(nombre, "no se puede " + accion + ": " + motivo);
        return ResultadoFS.rechazo(motivo);
    }

    /** Reporta la integridad rota y devuelve false. */
    private boolean integridadRota(String motivo) {
        Debug.error(nombre, "integridad del sistema de archivos rota: " + motivo);
        return false;
    }
}
