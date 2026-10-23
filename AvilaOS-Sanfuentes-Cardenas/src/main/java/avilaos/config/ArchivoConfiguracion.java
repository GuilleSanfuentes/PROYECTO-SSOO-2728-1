package avilaos.config;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Reloj;
import avilaos.log.Debug;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.sync.DatosBuffer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Semaphore;

/** Lectura, validación y escritura del archivo JSON de configuración del sistema. */
public final class ArchivoConfiguracion {

    public static final String RUTA_POR_DEFECTO = "config/avilaosconfigs.json";

    public static final String CLAVE_DEBUGGER = "debugger";
    public static final String CLAVE_SIMULACION = "simulacion";
    public static final String CLAVE_DURACION = "duracionCicloMs";
    public static final String CLAVE_MAX_CICLOS = "maxCiclos";
    public static final String CLAVE_LIMITE_ACTIVO = "limiteTiempoActivo";
    public static final String CLAVE_LIMITE_SEGUNDOS = "limiteTiempoSegundos";
    public static final String CLAVE_INACTIVIDAD_ACTIVA = "apagadoInactividadActivo";
    public static final String CLAVE_INACTIVIDAD_SEGUNDOS = "apagadoInactividadSegundos";
    public static final String CLAVE_LATENCIA = "latenciaRed";
    public static final String CLAVE_ENVEJECIMIENTO = "envejecimientoCadaCiclos";
    public static final String CLAVE_COMPUTADORES = "computadores";
    public static final String CLAVE_BUFFERS = "buffers";
    public static final String CLAVE_PROCESOS = "procesos";

    private static final String[] CLAVES_RAIZ = {CLAVE_DEBUGGER, CLAVE_SIMULACION, CLAVE_COMPUTADORES, CLAVE_BUFFERS, CLAVE_PROCESOS};
    private static final String[] CLAVES_SIMULACION = {CLAVE_DURACION, CLAVE_MAX_CICLOS, CLAVE_LIMITE_ACTIVO,
        CLAVE_LIMITE_SEGUNDOS, CLAVE_INACTIVIDAD_ACTIVA, CLAVE_INACTIVIDAD_SEGUNDOS, CLAVE_LATENCIA, CLAVE_ENVEJECIMIENTO};
    private static final String[] CLAVES_COMPUTADOR = {"ram", "politica", "quantum"};
    private static final String[] CLAVES_BUFFER = {"nombre", "capacidad", "computador"};
    private static final String[] CLAVES_PROCESO = {"nombre", "tipo", "prioridad", "memoria", "deadline", "computador",
        "instrucciones", "cadaCiclosES", "duracionES", "buffer", "ciclosPorElemento", "elementos"};

    private static final String ORIGEN = "Config";
    private static final Semaphore acceso = new Semaphore(1);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ArchivoConfiguracion() {
    }

    /** Lee la clave debugger del archivo y activa o desactiva Debug. Si falla, lo deja activo. */
    public static void aplicarDebugger(String ruta) {
        JsonObject config;
        String fallo = null;
        try {
            config = leerSinMensajes(ruta);
        } catch (IOException | JsonParseException e) {
            config = new JsonObject();
            fallo = "no se pudo leer " + ruta + ": " + e.getMessage();
        }
        Boolean valor = interpretarBooleano(config.get(CLAVE_DEBUGGER));
        if (valor == null && fallo == null) {
            fallo = "la clave '" + CLAVE_DEBUGGER + "' falta o no es TRUE/FALSE";
        }
        Debug.setActivo(valor == null || valor);
        if (fallo != null) {
            Debug.error(ORIGEN, fallo + "; debugger activado por defecto");
        } else {
            Debug.ok(ORIGEN, "debugger=" + (valor ? "TRUE" : "FALSE") + " leído de " + ruta);
        }
    }

    /** Lee y valida toda la configuración. Lo inválido se informa y se reemplaza u omite; nunca detiene el programa. */
    public static ConfiguracionSistema cargar(String ruta) {
        JsonObject raiz;
        try {
            raiz = leerSinMensajes(ruta);
        } catch (IOException | JsonParseException e) {
            Debug.error(ORIGEN, "no se pudo leer " + ruta + ": " + e.getMessage() + "; se usa la configuración por defecto");
            return ConfiguracionSistema.porDefecto();
        }
        ConfiguracionSistema config = new Lector(ruta).interpretar(raiz);
        if (config.getErroresCarga() == 0) {
            Debug.ok(ORIGEN, ruta + " cargado sin errores");
        } else {
            Debug.error(ORIGEN, ruta + " cargado con " + config.getErroresCarga() + " valores inválidos (reemplazados u omitidos)");
        }
        return config;
    }

    /** Guarda la configuración en el archivo de forma atómica. */
    public static boolean guardarConfiguracion(ConfiguracionSistema config, String ruta) {
        acceso.acquireUninterruptibly();
        try {
            escribirAtomico(Paths.get(ruta), aJson(config));
            Debug.ok(ORIGEN, "configuración guardada en " + ruta);
            return true;
        } catch (IOException e) {
            Debug.error(ORIGEN, "no se pudo guardar " + ruta + ": " + e.getMessage());
            return false;
        } finally {
            acceso.release();
        }
    }

    /** Convierte la configuración al formato JSON del archivo; cada proceso solo con los campos de su tipo. */
    public static JsonObject aJson(ConfiguracionSistema config) {
        JsonObject raiz = new JsonObject();
        raiz.addProperty(CLAVE_DEBUGGER, config.isDebugger());
        JsonObject simulacion = new JsonObject();
        simulacion.addProperty(CLAVE_DURACION, config.getDuracionCicloMs());
        CondicionesParada parada = config.getParada();
        simulacion.addProperty(CLAVE_MAX_CICLOS, parada.maxCiclos());
        simulacion.addProperty(CLAVE_LIMITE_ACTIVO, parada.limiteTiempoActivo());
        simulacion.addProperty(CLAVE_LIMITE_SEGUNDOS, parada.limiteTiempoSegundos());
        simulacion.addProperty(CLAVE_INACTIVIDAD_ACTIVA, parada.inactividadActiva());
        simulacion.addProperty(CLAVE_INACTIVIDAD_SEGUNDOS, parada.inactividadSegundos());
        simulacion.addProperty(CLAVE_LATENCIA, config.getLatenciaRed());
        simulacion.addProperty(CLAVE_ENVEJECIMIENTO, config.getEnvejecimientoCadaCiclos());
        raiz.add(CLAVE_SIMULACION, simulacion);

        JsonArray computadores = new JsonArray();
        Nodo<ConfigComputador> c = config.getComputadores().getCabeza();
        while (c != null) {
            JsonObject obj = new JsonObject();
            obj.addProperty("ram", c.getDato().ram());
            obj.addProperty("politica", c.getDato().politica().name());
            obj.addProperty("quantum", c.getDato().quantum());
            computadores.add(obj);
            c = c.getSiguiente();
        }
        raiz.add(CLAVE_COMPUTADORES, computadores);

        JsonArray buffers = new JsonArray();
        Nodo<DatosBuffer> b = config.getBuffers().getCabeza();
        while (b != null) {
            JsonObject obj = new JsonObject();
            obj.addProperty("nombre", b.getDato().nombre());
            obj.addProperty("capacidad", b.getDato().capacidad());
            obj.addProperty("computador", b.getDato().computador());
            buffers.add(obj);
            b = b.getSiguiente();
        }
        raiz.add(CLAVE_BUFFERS, buffers);

        JsonArray procesos = new JsonArray();
        Nodo<DatosProceso> p = config.getProcesos().getCabeza();
        while (p != null) {
            procesos.add(procesoAJson(p.getDato()));
            p = p.getSiguiente();
        }
        raiz.add(CLAVE_PROCESOS, procesos);
        return raiz;
    }

    /** Un proceso con solo los campos que usa su tipo. */
    private static JsonObject procesoAJson(DatosProceso d) {
        JsonObject obj = new JsonObject();
        obj.addProperty("nombre", d.nombre());
        obj.addProperty("tipo", d.tipo().name());
        obj.addProperty("prioridad", d.prioridad());
        obj.addProperty("memoria", d.memoria());
        obj.addProperty("deadline", d.deadline());
        obj.addProperty("computador", d.computador());
        switch (d.tipo()) {
            case CPU_BOUND -> obj.addProperty("instrucciones", d.instrucciones());
            case IO_BOUND -> {
                obj.addProperty("instrucciones", d.instrucciones());
                obj.addProperty("cadaCiclosES", d.cadaCiclosES());
                obj.addProperty("duracionES", d.duracionES());
            }
            case PRODUCTOR, CONSUMIDOR -> {
                obj.addProperty("buffer", d.buffer());
                obj.addProperty("ciclosPorElemento", d.ciclosPorElemento());
                obj.addProperty("elementos", d.elementos());
            }
        }
        return obj;
    }

    /** Acepta true/false o los textos "TRUE"/"FALSE" sin distinguir mayúsculas; si no, devuelve null. */
    private static Boolean interpretarBooleano(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitivo = elemento.getAsJsonPrimitive();
        if (primitivo.isBoolean()) {
            return primitivo.getAsBoolean();
        }
        if (primitivo.isString()) {
            String texto = primitivo.getAsString().trim();
            if (texto.equalsIgnoreCase("TRUE")) {
                return true;
            }
            if (texto.equalsIgnoreCase("FALSE")) {
                return false;
            }
        }
        return null;
    }

    /** Lee el JSON en exclusión mutua, sin imprimir; crea el archivo por defecto si no existe. */
    private static JsonObject leerSinMensajes(String ruta) throws IOException {
        acceso.acquireUninterruptibly();
        try {
            Path archivo = Paths.get(ruta);
            if (!Files.exists(archivo)) {
                JsonObject porDefecto = aJson(ConfiguracionSistema.porDefecto());
                escribirAtomico(archivo, porDefecto);
                return porDefecto;
            }
            try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                JsonElement raiz = JsonParser.parseReader(lector);
                if (raiz == null || !raiz.isJsonObject()) {
                    throw new JsonParseException("la raíz del archivo no es un objeto JSON");
                }
                return raiz.getAsJsonObject();
            }
        } finally {
            acceso.release();
        }
    }

    /** Escribe en archivo.tmp y lo mueve sobre el definitivo para no dejarlo a medias. */
    private static void escribirAtomico(Path archivo, JsonObject config) throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        try (Writer escritor = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
            GSON.toJson(config, escritor);
        }
        try {
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Interpreta el JSON campo por campo, contando los valores inválidos. */
    private static final class Lector {

        private final String ruta;
        private int errores;

        Lector(String ruta) {
            this.ruta = ruta;
        }

        ConfiguracionSistema interpretar(JsonObject raiz) {
            claves(raiz, CLAVES_RAIZ, "raíz");
            Boolean debugger = interpretarBooleano(raiz.get(CLAVE_DEBUGGER));
            if (debugger == null) {
                error(CLAVE_DEBUGGER + " falta o no es TRUE/FALSE, se usa TRUE");
                debugger = true;
            }
            JsonObject simulacion = objeto(raiz, CLAVE_SIMULACION);
            claves(simulacion, CLAVES_SIMULACION, CLAVE_SIMULACION);
            int duracion = entero(simulacion, CLAVE_DURACION, 0, Reloj.DURACION_MAXIMA_MS,
                    ConfiguracionSistema.DEF_DURACION_MS, CLAVE_SIMULACION);
            CondicionesParada parada = new CondicionesParada(
                    entero(simulacion, CLAVE_MAX_CICLOS, 0, Integer.MAX_VALUE, CondicionesParada.DEF_MAX_CICLOS, CLAVE_SIMULACION),
                    booleano(simulacion, CLAVE_LIMITE_ACTIVO, CondicionesParada.DEF_LIMITE_TIEMPO_ACTIVO, CLAVE_SIMULACION),
                    entero(simulacion, CLAVE_LIMITE_SEGUNDOS, 1, CondicionesParada.MAX_SEGUNDOS,
                            CondicionesParada.DEF_LIMITE_SEGUNDOS, CLAVE_SIMULACION),
                    booleano(simulacion, CLAVE_INACTIVIDAD_ACTIVA, CondicionesParada.DEF_INACTIVIDAD_ACTIVA, CLAVE_SIMULACION),
                    entero(simulacion, CLAVE_INACTIVIDAD_SEGUNDOS, 1, CondicionesParada.MAX_SEGUNDOS,
                            CondicionesParada.DEF_INACTIVIDAD_SEGUNDOS, CLAVE_SIMULACION));
            int latencia = entero(simulacion, CLAVE_LATENCIA, 0, ConfiguracionSistema.MAX_LATENCIA,
                    ConfiguracionSistema.DEF_LATENCIA, CLAVE_SIMULACION);
            int envejecimiento = entero(simulacion, CLAVE_ENVEJECIMIENTO, 0, ConfiguracionSistema.MAX_ENVEJECIMIENTO,
                    ConfiguracionSistema.DEF_ENVEJECIMIENTO, CLAVE_SIMULACION);

            ListaEnlazada<ConfigComputador> computadores = leerComputadores(arreglo(raiz, CLAVE_COMPUTADORES));
            ListaEnlazada<DatosBuffer> buffers = leerBuffers(arreglo(raiz, CLAVE_BUFFERS), computadores.tamano());
            ListaEnlazada<DatosProceso> procesos = leerProcesos(arreglo(raiz, CLAVE_PROCESOS), computadores.tamano(), buffers);
            return new ConfiguracionSistema(debugger, duracion, parada, latencia, envejecimiento,
                    computadores, buffers, procesos, errores);
        }

        private boolean booleano(JsonObject obj, String clave, boolean defecto, String contexto) {
            if (!obj.has(clave)) {
                Debug.info(ORIGEN, contexto + "." + clave + " no definido, se usa " + (defecto ? "TRUE" : "FALSE"));
                return defecto;
            }
            Boolean valor = interpretarBooleano(obj.get(clave));
            if (valor == null) {
                error(contexto + "." + clave + " = " + obj.get(clave) + " no es TRUE/FALSE, se usa " + (defecto ? "TRUE" : "FALSE"));
                return defecto;
            }
            return valor;
        }

        private ListaEnlazada<ConfigComputador> leerComputadores(JsonArray lista) {
            ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
            for (int i = 0; i < lista.size(); i++) {
                String contexto = CLAVE_COMPUTADORES + "[" + i + "]";
                if (i >= ConfiguracionSistema.MAX_COMPUTADORES) {
                    error(contexto + " supera el máximo de " + ConfiguracionSistema.MAX_COMPUTADORES + " computadores, se omite");
                    continue;
                }
                JsonObject obj = comoObjeto(lista.get(i), contexto);
                claves(obj, CLAVES_COMPUTADOR, contexto);
                int ram = entero(obj, "ram", 1, ConfiguracionSistema.MAX_RAM, ConfiguracionSistema.DEF_RAM, contexto);
                PoliticaTipo politica = politica(obj, contexto);
                int quantum = entero(obj, "quantum", 1, ConfiguracionSistema.MAX_QUANTUM, ConfiguracionSistema.DEF_QUANTUM, contexto);
                computadores.agregarFinal(new ConfigComputador(ram, politica, quantum));
            }
            while (computadores.tamano() < ConfiguracionSistema.MIN_COMPUTADORES) {
                error("hay " + computadores.tamano() + " computadores; el mínimo es " + ConfiguracionSistema.MIN_COMPUTADORES
                        + ", se agrega uno por defecto");
                computadores.agregarFinal(new ConfigComputador(ConfiguracionSistema.DEF_RAM,
                        ConfiguracionSistema.DEF_POLITICA, ConfiguracionSistema.DEF_QUANTUM));
            }
            return computadores;
        }

        private ListaEnlazada<DatosBuffer> leerBuffers(JsonArray lista, int cantidadComputadores) {
            ListaEnlazada<DatosBuffer> buffers = new ListaEnlazada<>();
            for (int i = 0; i < lista.size(); i++) {
                String contexto = CLAVE_BUFFERS + "[" + i + "]";
                JsonObject obj = comoObjeto(lista.get(i), contexto);
                claves(obj, CLAVES_BUFFER, contexto);
                String nombre = texto(obj, "nombre");
                String problema = "";
                if (nombre == null || nombre.isBlank()) {
                    problema += " nombre vacío;";
                } else if (buscarBuffer(buffers, nombre.trim()) != null) {
                    problema += " nombre '" + nombre + "' repetido;";
                }
                Integer capacidad = enteroEstricto(obj, "capacidad", 1, ConfiguracionSistema.MAX_CAPACIDAD_BUFFER);
                if (capacidad == null) {
                    problema += " capacidad debe ser entero 1.." + ConfiguracionSistema.MAX_CAPACIDAD_BUFFER + ";";
                }
                Integer computador = enteroEstricto(obj, "computador", 0, cantidadComputadores - 1);
                if (computador == null) {
                    problema += " computador debe ser 0.." + (cantidadComputadores - 1) + ";";
                }
                if (!problema.isEmpty()) {
                    error(contexto + " se omite:" + problema);
                    continue;
                }
                buffers.agregarFinal(new DatosBuffer(nombre.trim(), capacidad, computador));
            }
            return buffers;
        }

        private ListaEnlazada<DatosProceso> leerProcesos(JsonArray lista, int cantidadComputadores, ListaEnlazada<DatosBuffer> buffers) {
            ListaEnlazada<DatosProceso> procesos = new ListaEnlazada<>();
            for (int i = 0; i < lista.size(); i++) {
                String contexto = CLAVE_PROCESOS + "[" + i + "]";
                JsonObject obj = comoObjeto(lista.get(i), contexto);
                claves(obj, CLAVES_PROCESO, contexto);
                DatosProceso datos;
                try {
                    datos = GSON.fromJson(obj, DatosProceso.class);
                } catch (JsonParseException e) {
                    error(contexto + " se omite: algún campo tiene un tipo de dato inválido (" + e.getMessage() + ")");
                    continue;
                }
                if (datos == null || datos.tipo() == null) {
                    error(contexto + " se omite: tipo inválido (CPU_BOUND, IO_BOUND, PRODUCTOR o CONSUMIDOR)");
                    continue;
                }
                String problema = FabricaProcesos.validar(datos);
                if (datos.computador() >= cantidadComputadores) {
                    problema += " computador " + datos.computador() + " no existe (0.." + (cantidadComputadores - 1) + " o -1);";
                }
                if (datos.tipo().usaBuffer() && datos.buffer() != null && !datos.buffer().isBlank()
                        && buscarBuffer(buffers, datos.buffer().trim()) == null) {
                    problema += " buffer '" + datos.buffer() + "' no está definido en 'buffers';";
                }
                if (!problema.isEmpty()) {
                    error(contexto + " '" + datos.nombre() + "' se omite:" + problema);
                    continue;
                }
                procesos.agregarFinal(datos);
            }
            return procesos;
        }

        private DatosBuffer buscarBuffer(ListaEnlazada<DatosBuffer> buffers, String nombre) {
            Nodo<DatosBuffer> nodo = buffers.getCabeza();
            while (nodo != null) {
                if (nodo.getDato().nombre().equals(nombre)) {
                    return nodo.getDato();
                }
                nodo = nodo.getSiguiente();
            }
            return null;
        }

        private PoliticaTipo politica(JsonObject obj, String contexto) {
            String valor = texto(obj, "politica");
            if (valor == null) {
                Debug.info(ORIGEN, contexto + ".politica no definida, se usa " + ConfiguracionSistema.DEF_POLITICA.name());
                return ConfiguracionSistema.DEF_POLITICA;
            }
            for (PoliticaTipo tipo : PoliticaTipo.values()) {
                if (tipo.name().equalsIgnoreCase(valor.trim())) {
                    return tipo;
                }
            }
            error(contexto + ".politica '" + valor + "' no existe (FCFS, EDF, ROUND_ROBIN, PRIORIDADES), se usa "
                    + ConfiguracionSistema.DEF_POLITICA.name());
            return ConfiguracionSistema.DEF_POLITICA;
        }

        private int entero(JsonObject obj, String clave, int min, int max, int defecto, String contexto) {
            if (!obj.has(clave)) {
                Debug.info(ORIGEN, contexto + "." + clave + " no definido, se usa " + defecto);
                return defecto;
            }
            Integer valor = enteroEstricto(obj, clave, min, max);
            if (valor == null) {
                error(contexto + "." + clave + " = " + obj.get(clave) + " inválido (entero " + min + ".." + max + "), se usa " + defecto);
                return defecto;
            }
            return valor;
        }

        private Integer enteroEstricto(JsonObject obj, String clave, int min, int max) {
            JsonElement elemento = obj.get(clave);
            if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            double valor = elemento.getAsDouble();
            if (valor != Math.rint(valor) || valor < min || valor > max) {
                return null;
            }
            return (int) valor;
        }

        private String texto(JsonObject obj, String clave) {
            JsonElement elemento = obj.get(clave);
            if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isString()) {
                return null;
            }
            return elemento.getAsString();
        }

        private JsonObject objeto(JsonObject padre, String clave) {
            JsonElement elemento = padre.get(clave);
            if (elemento == null) {
                Debug.info(ORIGEN, "sección '" + clave + "' no definida, se usan valores por defecto");
                return new JsonObject();
            }
            return comoObjeto(elemento, clave);
        }

        private JsonArray arreglo(JsonObject padre, String clave) {
            JsonElement elemento = padre.get(clave);
            if (elemento == null) {
                Debug.info(ORIGEN, "lista '" + clave + "' no definida, queda vacía");
                return new JsonArray();
            }
            if (!elemento.isJsonArray()) {
                error("'" + clave + "' debe ser una lista [ ... ], queda vacía");
                return new JsonArray();
            }
            return elemento.getAsJsonArray();
        }

        private JsonObject comoObjeto(JsonElement elemento, String contexto) {
            if (elemento == null || !elemento.isJsonObject()) {
                error(contexto + " debe ser un objeto { ... }, se usan valores por defecto");
                return new JsonObject();
            }
            return elemento.getAsJsonObject();
        }

        private void claves(JsonObject obj, String[] permitidas, String contexto) {
            for (String clave : obj.keySet()) {
                if (clave.startsWith("_")) {
                    continue;
                }
                boolean conocida = false;
                for (String permitida : permitidas) {
                    conocida |= permitida.equals(clave);
                }
                if (!conocida) {
                    error(contexto + ": clave desconocida '" + clave + "' (se ignora; ¿error de escritura?)");
                }
            }
        }

        private void error(String mensaje) {
            errores++;
            Debug.error(ORIGEN, ruta + ": " + mensaje);
        }
    }
}
