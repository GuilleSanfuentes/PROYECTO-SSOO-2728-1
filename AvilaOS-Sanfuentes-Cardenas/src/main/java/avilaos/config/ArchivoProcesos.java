package avilaos.config;

import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.DiscoSD;
import avilaos.hardware.Reloj;
import avilaos.log.Debug;
import avilaos.so.DatosProcesoArchivo;
import avilaos.so.FabricaProcesos;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.sync.DatosBuffer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Semaphore;

/** JSON de procesos y configuración con el formato del enunciado: aparta las entradas malas y las reporta. */
public final class ArchivoProcesos {

    public static final String CLAVE_GLOBAL = "globalConfig";
    public static final String CLAVE_COMPUTADOR = "computer";
    public static final String CLAVE_PROCESOS = "processes";
    public static final String CLAVE_BUFFERS = "buffers";
    public static final int MAX_SWAP = ArchivoSistemaArchivos.MAX_BLOQUES;
    public static final int VELOCIDAD_POR_DEFECTO = 1;

    private static final String ORIGEN = "ConfigProcesos";
    private static final String[] CLAVES_RAIZ = {CLAVE_GLOBAL, CLAVE_COMPUTADOR, CLAVE_PROCESOS, CLAVE_BUFFERS};
    private static final String[] CLAVES_GLOBAL = {"cycleDurationMs", "swapSizeBlocks", "initialDiskPolicy", "headSpeed", "debugger"};
    private static final String[] CLAVES_PC = {"ramCapacity", "policy", "quantum"};
    private static final String[] CLAVES_BUFFER = {"name", "capacity"};
    private static final String[] CLAVES_PROCESO = {"name", "memory", "deadline", "instructions", "targetPath", "operation",
        "fileSizeBlocks", "priority", "newName"};
    private static final Semaphore acceso = new Semaphore(1);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ArchivoProcesos() {
    }

    /** Lee el archivo; nunca lanza excepciones: lo que no sirve queda en la lista de problemas. */
    public static DatosProcesos leer(String ruta) {
        String texto;
        acceso.acquireUninterruptibly();
        try {
            texto = Files.readString(Paths.get(ruta), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return fatal("no se pudo leer " + ruta + ": " + e.getMessage(), new ListaEnlazada<>());
        } finally {
            acceso.release();
        }
        return interpretar(texto);
    }

    /** Interpreta el texto de un JSON de procesos (lo usan leer y las pruebas). */
    public static DatosProcesos interpretar(String texto) {
        ListaEnlazada<String> problemas = new ListaEnlazada<>();
        JsonElement leido;
        try {
            leido = JsonParser.parseString(texto);
        } catch (RuntimeException e) {
            return fatal("el texto no es JSON válido: " + e.getMessage(), problemas);
        }
        if (leido == null || !leido.isJsonObject()) {
            return fatal("la raíz del archivo no es un objeto JSON", problemas);
        }
        return interpretar(leido.getAsJsonObject(), problemas);
    }

    /** Guarda los datos con el mismo formato, de forma atómica. Devuelve false si falla. */
    public static boolean guardar(DatosProcesos datos, String ruta) {
        if (datos == null || !datos.cargable() || ruta == null || ruta.isBlank()) {
            Debug.error(ORIGEN, "guardar: no hay datos cargables o la ruta está vacía");
            return false;
        }
        JsonObject raiz = aJson(datos);
        acceso.acquireUninterruptibly();
        try {
            Path archivo = Paths.get(ruta);
            Path carpeta = archivo.toAbsolutePath().getParent();
            if (carpeta != null) {
                Files.createDirectories(carpeta);
            }
            Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
            try (Writer escritor = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
                GSON.toJson(raiz, escritor);
            }
            try {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
            Debug.ok(ORIGEN, "procesos guardados en " + ruta + " (" + datos.procesos().tamano() + " procesos)");
            return true;
        } catch (IOException | RuntimeException e) {
            Debug.error(ORIGEN, "no se pudo guardar " + ruta + ": " + e.getMessage());
            return false;
        } finally {
            acceso.release();
        }
    }

    /** Convierte los datos al formato del enunciado; las claves opcionales solo van si aportan algo. */
    private static JsonObject aJson(DatosProcesos datos) {
        JsonObject raiz = new JsonObject();
        JsonObject global = new JsonObject();
        global.addProperty("cycleDurationMs", datos.duracionCicloMs());
        global.addProperty("swapSizeBlocks", datos.bloquesSwap());
        global.addProperty("initialDiskPolicy", datos.politicaDisco().getNombre());
        if (datos.velocidadCabezal() != VELOCIDAD_POR_DEFECTO) {
            global.addProperty("headSpeed", datos.velocidadCabezal());
        }
        if (datos.debugger() != null) {
            global.addProperty("debugger", datos.debugger());
        }
        raiz.add(CLAVE_GLOBAL, global);
        JsonObject computador = new JsonObject();
        computador.addProperty("ramCapacity", datos.ram());
        computador.addProperty("policy", datos.politica().name());
        computador.addProperty("quantum", datos.quantum());
        raiz.add(CLAVE_COMPUTADOR, computador);
        if (!datos.buffers().estaVacia()) {
            JsonArray buffers = new JsonArray();
            Nodo<DatosBuffer> b = datos.buffers().getCabeza();
            while (b != null) {
                JsonObject o = new JsonObject();
                o.addProperty("name", b.getDato().nombre());
                o.addProperty("capacity", b.getDato().capacidad());
                buffers.add(o);
                b = b.getSiguiente();
            }
            raiz.add(CLAVE_BUFFERS, buffers);
        }
        JsonArray procesos = new JsonArray();
        Nodo<DatosProcesoArchivo> nodo = datos.procesos().getCabeza();
        while (nodo != null) {
            DatosProcesoArchivo p = nodo.getDato();
            JsonObject o = new JsonObject();
            o.addProperty("name", p.nombre());
            o.addProperty("memory", p.memoria());
            o.addProperty("deadline", p.deadline());
            o.addProperty("instructions", p.instrucciones());
            o.addProperty("targetPath", p.ruta());
            o.addProperty("operation", p.operacion().getJson());
            o.addProperty("fileSizeBlocks", p.bloquesArchivo());
            if (p.prioridad() != DatosProcesoArchivo.PRIORIDAD_POR_DEFECTO) {
                o.addProperty("priority", p.prioridad());
            }
            if (p.nuevoNombre() != null) {
                o.addProperty("newName", p.nuevoNombre());
            }
            procesos.add(o);
            nodo = nodo.getSiguiente();
        }
        raiz.add(CLAVE_PROCESOS, procesos);
        return raiz;
    }

    /** Interpreta cada sección; si falta una de las obligatorias el archivo es incargable. */
    private static DatosProcesos interpretar(JsonObject raiz, ListaEnlazada<String> problemas) {
        ArchivoSistemaArchivos.avisarClaves(raiz, CLAVES_RAIZ, "raíz", problemas);
        if (!raiz.has(CLAVE_GLOBAL) || !raiz.get(CLAVE_GLOBAL).isJsonObject()) {
            return fatal("falta la sección '" + CLAVE_GLOBAL + "' (objeto)", problemas);
        }
        if (!raiz.has(CLAVE_COMPUTADOR) || !raiz.get(CLAVE_COMPUTADOR).isJsonObject()) {
            return fatal("falta la sección '" + CLAVE_COMPUTADOR + "' (un solo objeto, no una lista)", problemas);
        }
        if (!raiz.has(CLAVE_PROCESOS) || !raiz.get(CLAVE_PROCESOS).isJsonArray()) {
            return fatal("falta la sección '" + CLAVE_PROCESOS + "' (lista de procesos)", problemas);
        }

        JsonObject global = raiz.getAsJsonObject(CLAVE_GLOBAL);
        ArchivoSistemaArchivos.avisarClaves(global, CLAVES_GLOBAL, CLAVE_GLOBAL, problemas);
        int duracion = numero(global, "cycleDurationMs", 0, Reloj.DURACION_MAXIMA_MS,
                ConfiguracionSistema.DEF_DURACION_MS, CLAVE_GLOBAL, problemas);
        int swap = numero(global, "swapSizeBlocks", 0, MAX_SWAP, DiscoSD.SWAP_POR_DEFECTO, CLAVE_GLOBAL, problemas);
        PoliticaDiscoTipo politicaDisco = PoliticaDiscoTipo.desdeTexto(ArchivoSistemaArchivos.texto(global.get("initialDiskPolicy")));
        if (politicaDisco == null) {
            problemas.agregarFinal(CLAVE_GLOBAL + ".initialDiskPolicy inválida (" + global.get("initialDiskPolicy")
                    + "): debe ser FIFO, SSTF, SCAN, C-SCAN, LOOK o C-LOOK; se usa FIFO");
            politicaDisco = PoliticaDiscoTipo.FIFO;
        }
        int velocidad = global.has("headSpeed")
                ? numero(global, "headSpeed", 1, DiscoSD.MAX_VELOCIDAD, VELOCIDAD_POR_DEFECTO, CLAVE_GLOBAL, problemas)
                : VELOCIDAD_POR_DEFECTO;
        Boolean debugger = null;
        if (global.has("debugger")) {
            debugger = ArchivoConfiguracion.interpretarBooleano(global.get("debugger"));
            if (debugger == null) {
                problemas.agregarFinal(CLAVE_GLOBAL + ".debugger inválido (" + global.get("debugger")
                        + "): debe ser true o false; se ignora");
            }
        }

        JsonObject computador = raiz.getAsJsonObject(CLAVE_COMPUTADOR);
        ArchivoSistemaArchivos.avisarClaves(computador, CLAVES_PC, CLAVE_COMPUTADOR, problemas);
        int ram = numero(computador, "ramCapacity", 1, ConfiguracionSistema.MAX_RAM, ConfiguracionSistema.DEF_RAM,
                CLAVE_COMPUTADOR, problemas);
        PoliticaTipo politica = politicaCPU(computador.get("policy"), problemas);
        int minimoQuantum = politica == PoliticaTipo.ROUND_ROBIN ? 1 : 0;
        int quantum = numero(computador, "quantum", minimoQuantum, ConfiguracionSistema.MAX_QUANTUM,
                ConfiguracionSistema.DEF_QUANTUM, CLAVE_COMPUTADOR, problemas);

        ListaEnlazada<DatosBuffer> buffers = new ListaEnlazada<>();
        int ramLibre = raiz.has(CLAVE_BUFFERS) ? leerBuffers(raiz.get(CLAVE_BUFFERS), ram, buffers, problemas) : ram;

        ListaEnlazada<DatosProcesoArchivo> procesos = new ListaEnlazada<>();
        JsonArray lista = raiz.getAsJsonArray(CLAVE_PROCESOS);
        for (int i = 0; i < lista.size(); i++) {
            DatosProcesoArchivo p = proceso(lista.get(i), i + 1, ramLibre, problemas);
            if (p != null) {
                procesos.agregarFinal(p);
            }
        }
        if (problemas.estaVacia()) {
            Debug.ok(ORIGEN, "procesos leídos: " + procesos.tamano() + " válidos");
        } else {
            Debug.info(ORIGEN, "procesos leídos: " + procesos.tamano() + " de " + lista.size() + " válidos, "
                    + problemas.tamano() + " observaciones");
        }
        return new DatosProcesos(true, duracion, swap, politicaDisco, velocidad, debugger, ram, politica, quantum,
                buffers, procesos, problemas);
    }

    /** Política de CPU por su nombre (admite espacios o guiones en vez de _); si no existe se usa la de por defecto. */
    private static PoliticaTipo politicaCPU(JsonElement elemento, ListaEnlazada<String> problemas) {
        String valor = ArchivoSistemaArchivos.texto(elemento);
        if (valor != null) {
            String limpio = valor.trim().replace('-', '_').replace(' ', '_');
            for (PoliticaTipo tipo : PoliticaTipo.values()) {
                if (tipo.name().equalsIgnoreCase(limpio)) {
                    return tipo;
                }
            }
        }
        problemas.agregarFinal(CLAVE_COMPUTADOR + ".policy inválida (" + elemento
                + "): debe ser FCFS, EDF, ROUND_ROBIN o PRIORIDADES; se usa " + ConfiguracionSistema.DEF_POLITICA.name());
        return ConfiguracionSistema.DEF_POLITICA;
    }

    /** Lee los buffers opcionales que quepan en la RAM y devuelve la memoria que queda para los procesos. */
    private static int leerBuffers(JsonElement elemento, int ram, ListaEnlazada<DatosBuffer> buffers,
            ListaEnlazada<String> problemas) {
        if (!elemento.isJsonArray()) {
            problemas.agregarFinal("'" + CLAVE_BUFFERS + "' debe ser una lista; se ignora");
            return ram;
        }
        int libre = ram;
        JsonArray lista = elemento.getAsJsonArray();
        for (int i = 0; i < lista.size(); i++) {
            String etiqueta = "buffer " + (i + 1);
            if (!lista.get(i).isJsonObject()) {
                problemas.agregarFinal(etiqueta + ": no es un objeto JSON (se aparta)");
                continue;
            }
            JsonObject o = lista.get(i).getAsJsonObject();
            ArchivoSistemaArchivos.avisarClaves(o, CLAVES_BUFFER, etiqueta, problemas);
            String nombre = ArchivoSistemaArchivos.texto(o.get("name"));
            Integer capacidad = ArchivoSistemaArchivos.entero(o.get("capacity"));
            if (nombre == null || nombre.isBlank() || existeBuffer(buffers, nombre.trim())) {
                problemas.agregarFinal(etiqueta + ": 'name' falta, está vacío o repetido (se aparta)");
            } else if (capacidad == null || capacidad < 1 || capacidad > ConfiguracionSistema.MAX_CAPACIDAD_BUFFER) {
                problemas.agregarFinal(etiqueta + " (" + nombre + "): 'capacity' debe ser un entero de 1 a "
                        + ConfiguracionSistema.MAX_CAPACIDAD_BUFFER + " (se aparta)");
            } else if (capacidad > libre) {
                problemas.agregarFinal(etiqueta + " (" + nombre + "): pide " + capacidad + " de memoria y quedan " + libre
                        + " libres (se aparta)");
            } else {
                libre -= capacidad;
                buffers.agregarFinal(new DatosBuffer(nombre.trim(), capacidad, 0));
            }
        }
        return libre;
    }

    /** Indica si ya hay un buffer con ese nombre. */
    private static boolean existeBuffer(ListaEnlazada<DatosBuffer> buffers, String nombre) {
        Nodo<DatosBuffer> nodo = buffers.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().nombre().equals(nombre)) {
                return true;
            }
            nodo = nodo.getSiguiente();
        }
        return false;
    }

    /** Valida un proceso; si algo no sirve lo aparta con su motivo y devuelve null. */
    private static DatosProcesoArchivo proceso(JsonElement elemento, int indice, int ramLibre, ListaEnlazada<String> problemas) {
        if (elemento == null || !elemento.isJsonObject()) {
            problemas.agregarFinal("entrada " + indice + ": no es un objeto JSON (se aparta)");
            return null;
        }
        JsonObject o = elemento.getAsJsonObject();
        String nombre = ArchivoSistemaArchivos.texto(o.get("name"));
        String destino = ArchivoSistemaArchivos.texto(o.get("targetPath"));
        String etiqueta = "entrada " + indice + " (" + (nombre == null ? "sin nombre" : nombre)
                + (destino == null ? "" : ", " + destino) + ")";
        ArchivoSistemaArchivos.avisarClaves(o, CLAVES_PROCESO, etiqueta, problemas);
        if (nombre == null || nombre.isBlank()) {
            return apartar(etiqueta, "'name' falta o está vacío", problemas);
        }
        Integer memoria = ArchivoSistemaArchivos.entero(o.get("memory"));
        if (memoria == null || memoria < 1) {
            return apartar(etiqueta, "'memory' debe ser un entero de al menos 1 (vino " + o.get("memory") + ")", problemas);
        }
        if (memoria > ramLibre) {
            return apartar(etiqueta, "'memory' pide " + memoria + " y la RAM disponible para procesos es " + ramLibre, problemas);
        }
        Integer deadline = ArchivoSistemaArchivos.entero(o.get("deadline"));
        if (deadline == null || deadline < 1) {
            return apartar(etiqueta, "'deadline' debe ser un entero de al menos 1 (vino " + o.get("deadline") + ")", problemas);
        }
        Integer instrucciones = ArchivoSistemaArchivos.entero(o.get("instructions"));
        if (instrucciones == null || instrucciones < 1) {
            return apartar(etiqueta, "'instructions' debe ser un entero de al menos 1 (vino " + o.get("instructions") + ")",
                    problemas);
        }
        String ruta = SistemaArchivos.normalizar(destino);
        if (ruta == null || ruta.equals(SistemaArchivos.RAIZ)) {
            return apartar(etiqueta, "'targetPath' falta o no es una ruta absoluta válida distinta de /", problemas);
        }
        TipoOperacionES operacion = TipoOperacionES.desdeJson(ArchivoSistemaArchivos.texto(o.get("operation")));
        if (operacion == null) {
            return apartar(etiqueta, "'operation' debe ser CREATE, READ, UPDATE o DELETE (vino " + o.get("operation") + ")",
                    problemas);
        }
        int minimoBloques = operacion == TipoOperacionES.CREAR ? 1 : 0;
        Integer bloques = ArchivoSistemaArchivos.entero(o.get("fileSizeBlocks"));
        if (!o.has("fileSizeBlocks") && minimoBloques == 0) {
            problemas.agregarFinal(etiqueta + ": no indica 'fileSizeBlocks'; se usa 0");
            bloques = 0;
        }
        if (bloques == null || bloques < minimoBloques) {
            return apartar(etiqueta, "'fileSizeBlocks' debe ser un entero de al menos " + minimoBloques
                    + " (vino " + o.get("fileSizeBlocks") + ")", problemas);
        }
        int prioridad = DatosProcesoArchivo.PRIORIDAD_POR_DEFECTO;
        if (o.has("priority")) {
            Integer valor = ArchivoSistemaArchivos.entero(o.get("priority"));
            if (valor == null || valor < FabricaProcesos.PRIORIDAD_MAXIMA || valor > FabricaProcesos.PRIORIDAD_MINIMA) {
                return apartar(etiqueta, "'priority' debe ser un entero de " + FabricaProcesos.PRIORIDAD_MAXIMA + " a "
                        + FabricaProcesos.PRIORIDAD_MINIMA + " (vino " + o.get("priority") + ")", problemas);
            }
            prioridad = valor;
        }
        String nuevoNombre = null;
        if (o.has("newName")) {
            if (operacion != TipoOperacionES.ACTUALIZAR) {
                problemas.agregarFinal(etiqueta + ": 'newName' solo se usa con UPDATE; se ignora");
            } else {
                nuevoNombre = ArchivoSistemaArchivos.texto(o.get("newName"));
                String motivo = nuevoNombre == null ? "debe ser un texto" : SistemaArchivos.motivoNombre(nuevoNombre);
                if (motivo != null) {
                    return apartar(etiqueta, "'newName' inválido: " + motivo, problemas);
                }
            }
        }
        return new DatosProcesoArchivo(nombre.trim(), memoria, deadline, instrucciones, ruta, operacion, bloques,
                prioridad, nuevoNombre);
    }

    /** Entero obligatorio de una sección; si falta o está fuera de rango se anota y se usa el valor por defecto. */
    private static int numero(JsonObject o, String clave, int min, int max, int defecto, String seccion,
            ListaEnlazada<String> problemas) {
        Integer valor = ArchivoSistemaArchivos.entero(o.get(clave));
        if (valor == null || valor < min || valor > max) {
            problemas.agregarFinal(seccion + "." + clave + " inválido (" + o.get(clave) + "): debe ser un entero de "
                    + min + " a " + max + "; se usa " + defecto);
            return defecto;
        }
        return valor;
    }

    /** Anota el motivo y devuelve null (entrada apartada). */
    private static DatosProcesoArchivo apartar(String etiqueta, String motivo, ListaEnlazada<String> problemas) {
        problemas.agregarFinal(etiqueta + ": " + motivo + " (se aparta)");
        return null;
    }

    /** Archivo que no se puede usar: sin procesos. */
    private static DatosProcesos fatal(String motivo, ListaEnlazada<String> problemas) {
        problemas.agregarFinal(motivo);
        Debug.error(ORIGEN, "no se pueden cargar los procesos: " + motivo);
        return new DatosProcesos(false, ConfiguracionSistema.DEF_DURACION_MS, DiscoSD.SWAP_POR_DEFECTO, PoliticaDiscoTipo.FIFO,
                VELOCIDAD_POR_DEFECTO, null, ConfiguracionSistema.DEF_RAM, ConfiguracionSistema.DEF_POLITICA,
                ConfiguracionSistema.DEF_QUANTUM, new ListaEnlazada<>(), new ListaEnlazada<>(), problemas);
    }
}
