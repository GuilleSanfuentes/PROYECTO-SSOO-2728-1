package avilaos.config;

import avilaos.enums.Permiso;
import avilaos.enums.TipoEntrada;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Bloque;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.so.archivos.DatosEntrada;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
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

/** JSON del sistema de archivos con el formato del enunciado: lee entrada por entrada, aparta las malas y las reporta. */
public final class ArchivoSistemaArchivos {

    public static final int MIN_BLOQUES = 10;
    public static final int MAX_BLOQUES = 5000;
    public static final String CLAVE_DISCO = "diskConfig";
    public static final String CLAVE_ENTRADAS = "fileSystem";

    private static final String ORIGEN = "ConfigFS";
    private static final String[] CLAVES_DISCO = {"totalBlocks", "initialHead"};
    private static final String[] CLAVES_ENTRADA = {"path", "type", "owner", "permissions", "startBlock", "blocks", "createdBy", "chain"};
    private static final Semaphore acceso = new Semaphore(1);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ArchivoSistemaArchivos() {
    }

    /** Lee el archivo; nunca lanza excepciones: lo que no sirve queda en la lista de problemas. */
    public static DatosSistemaArchivos leer(String ruta) {
        ListaEnlazada<String> problemas = new ListaEnlazada<>();
        JsonObject raiz;
        acceso.acquireUninterruptibly();
        try (Reader lector = Files.newBufferedReader(Paths.get(ruta), StandardCharsets.UTF_8)) {
            JsonElement leido = JsonParser.parseReader(lector);
            if (leido == null || !leido.isJsonObject()) {
                return fatal("la raíz del archivo no es un objeto JSON", problemas);
            }
            raiz = leido.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return fatal("no se pudo leer " + ruta + ": " + e.getMessage(), problemas);
        } finally {
            acceso.release();
        }
        return interpretar(raiz, problemas);
    }

    /** Interpreta un JSON ya leído (también lo usan las pruebas con texto armado). */
    public static DatosSistemaArchivos interpretar(String texto) {
        ListaEnlazada<String> problemas = new ListaEnlazada<>();
        try {
            JsonElement leido = JsonParser.parseString(texto);
            if (leido == null || !leido.isJsonObject()) {
                return fatal("la raíz del archivo no es un objeto JSON", problemas);
            }
            return interpretar(leido.getAsJsonObject(), problemas);
        } catch (JsonParseException e) {
            return fatal("el texto no es JSON válido: " + e.getMessage(), problemas);
        }
    }

    /** Guarda el estado actual (disco y árbol) con el mismo formato, de forma atómica. Devuelve false si falla. */
    public static boolean guardar(SistemaArchivos fs, DiscoSD disco, String ruta) {
        JsonObject raiz = new JsonObject();
        JsonObject discoJson = new JsonObject();
        discoJson.addProperty("totalBlocks", disco.getBloquesArchivos());
        discoJson.addProperty("initialHead", Math.min(disco.getCabezal(), disco.getBloquesArchivos() - 1));
        raiz.add(CLAVE_DISCO, discoJson);
        JsonArray entradas = new JsonArray();
        Nodo<InfoEntrada> nodo = fs.listarTodo().getCabeza();
        while (nodo != null) {
            InfoEntrada e = nodo.getDato();
            if (!e.ruta().equals(SistemaArchivos.RAIZ)) {
                JsonObject o = new JsonObject();
                o.addProperty("path", e.ruta());
                o.addProperty("type", e.tipo().getJson());
                if (!e.esDirectorio()) {
                    o.addProperty("startBlock", e.primerBloque());
                    o.addProperty("blocks", e.bloques());
                }
                o.addProperty("owner", e.dueno());
                o.addProperty("permissions", e.permiso().getJson());
                o.addProperty("createdBy", e.creador());
                if (!e.esDirectorio()) {
                    JsonArray cadena = new JsonArray();
                    for (int b : fs.bloquesDe(e.ruta())) {
                        cadena.add(b);
                    }
                    o.add("chain", cadena);
                }
                entradas.add(o);
            }
            nodo = nodo.getSiguiente();
        }
        raiz.add(CLAVE_ENTRADAS, entradas);
        Path archivo = Paths.get(ruta);
        acceso.acquireUninterruptibly();
        try {
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
            Debug.ok(ORIGEN, "sistema de archivos guardado en " + ruta + " (" + entradas.size() + " entradas)");
            return true;
        } catch (IOException | RuntimeException e) {
            Debug.error(ORIGEN, "no se pudo guardar " + ruta + ": " + e.getMessage());
            return false;
        } finally {
            acceso.release();
        }
    }

    /** Interpreta diskConfig y cada entrada de fileSystem; las secciones raíz faltantes hacen el archivo incargable. */
    private static DatosSistemaArchivos interpretar(JsonObject raiz, ListaEnlazada<String> problemas) {
        Nodo<String> clave = ClavesJson.de(raiz).getCabeza();
        while (clave != null) {
            if (!clave.getDato().equals(CLAVE_DISCO) && !clave.getDato().equals(CLAVE_ENTRADAS)) {
                problemas.agregarFinal("clave desconocida en la raíz: '" + clave.getDato() + "' (se ignora)");
            }
            clave = clave.getSiguiente();
        }
        if (!raiz.has(CLAVE_DISCO) || !raiz.get(CLAVE_DISCO).isJsonObject()) {
            return fatal("falta la sección '" + CLAVE_DISCO + "' (objeto con totalBlocks e initialHead)", problemas);
        }
        if (!raiz.has(CLAVE_ENTRADAS) || !raiz.get(CLAVE_ENTRADAS).isJsonArray()) {
            return fatal("falta la sección '" + CLAVE_ENTRADAS + "' (lista de directorios y archivos)", problemas);
        }
        JsonObject disco = raiz.getAsJsonObject(CLAVE_DISCO);
        avisarClaves(disco, CLAVES_DISCO, CLAVE_DISCO, problemas);
        Integer total = entero(disco.get("totalBlocks"));
        if (total == null || total < MIN_BLOQUES || total > MAX_BLOQUES) {
            problemas.agregarFinal(CLAVE_DISCO + ".totalBlocks inválido (" + disco.get("totalBlocks") + "): debe ser un entero de "
                    + MIN_BLOQUES + " a " + MAX_BLOQUES + "; se usa " + DiscoSD.BLOQUES_POR_DEFECTO);
            total = DiscoSD.BLOQUES_POR_DEFECTO;
        }
        Integer cabezal = entero(disco.get("initialHead"));
        if (cabezal == null || cabezal < 0 || cabezal >= total) {
            problemas.agregarFinal(CLAVE_DISCO + ".initialHead inválido (" + disco.get("initialHead") + "): debe estar entre 0 y "
                    + (total - 1) + "; se usa 0");
            cabezal = 0;
        }
        ListaEnlazada<DatosEntrada> entradas = new ListaEnlazada<>();
        JsonArray lista = raiz.getAsJsonArray(CLAVE_ENTRADAS);
        for (int i = 0; i < lista.size(); i++) {
            DatosEntrada d = entrada(lista.get(i), i + 1, total, problemas);
            if (d != null) {
                entradas.agregarFinal(d);
            }
        }
        reportar(problemas, entradas.tamano(), lista.size());
        return new DatosSistemaArchivos(true, total, cabezal, entradas, problemas);
    }

    /** Valida una entrada; si algo no sirve la aparta con su motivo y devuelve null. */
    private static DatosEntrada entrada(JsonElement elemento, int indice, int totalBloques, ListaEnlazada<String> problemas) {
        if (elemento == null || !elemento.isJsonObject()) {
            problemas.agregarFinal("entrada " + indice + ": no es un objeto JSON (se aparta)");
            return null;
        }
        JsonObject o = elemento.getAsJsonObject();
        String etiqueta = "entrada " + indice + (texto(o.get("path")) != null ? " (" + texto(o.get("path")) + ")" : "");
        avisarClaves(o, CLAVES_ENTRADA, etiqueta, problemas);
        String ruta = SistemaArchivos.normalizar(texto(o.get("path")));
        if (ruta == null) {
            return apartar(etiqueta, "'path' falta o no es una ruta absoluta válida como /dir/archivo", problemas);
        }
        if (ruta.equals(SistemaArchivos.RAIZ)) {
            return apartar(etiqueta, "la raíz / ya existe", problemas);
        }
        TipoEntrada tipo = TipoEntrada.desdeJson(texto(o.get("type")));
        if (tipo == null) {
            return apartar(etiqueta, "'type' debe ser DIRECTORY o FILE (vino " + o.get("type") + ")", problemas);
        }
        String dueno = texto(o.get("owner"));
        if (dueno == null || dueno.isBlank()) {
            return apartar(etiqueta, "'owner' falta o está vacío", problemas);
        }
        Permiso permiso = Permiso.desdeJson(texto(o.get("permissions")));
        if (permiso == null) {
            return apartar(etiqueta, "'permissions' debe ser public o private (vino " + o.get("permissions") + ")", problemas);
        }
        String creador = o.has("createdBy") ? texto(o.get("createdBy")) : null;
        if (tipo == TipoEntrada.DIRECTORIO) {
            if (o.has("startBlock") || o.has("blocks")) {
                problemas.agregarFinal(etiqueta + ": un directorio no ocupa bloques; se ignoran startBlock y blocks");
            }
            if (o.has("chain")) {
                problemas.agregarFinal(etiqueta + ": un directorio no tiene cadena de bloques; se ignora 'chain'");
            }
            return new DatosEntrada(indice, ruta, tipo, dueno.trim(), permiso, Bloque.FIN, 0, creador, null);
        }
        Integer bloques = entero(o.get("blocks"));
        if (bloques == null || bloques < 1) {
            return apartar(etiqueta, "'blocks' debe ser un entero de al menos 1 (vino " + o.get("blocks") + ")", problemas);
        }
        int primero = Bloque.FIN;
        if (!o.has("startBlock")) {
            problemas.agregarFinal(etiqueta + ": no indica 'startBlock'; se le asignan los primeros bloques libres");
        } else {
            Integer inicio = entero(o.get("startBlock"));
            if (inicio == null || inicio < 0 || inicio >= totalBloques) {
                return apartar(etiqueta, "'startBlock' debe ser un entero de 0 a " + (totalBloques - 1) + " (vino " + o.get("startBlock") + ")", problemas);
            }
            primero = inicio;
        }
        int[] cadena = o.has("chain") ? cadena(o.get("chain"), bloques, primero, totalBloques) : null;
        if (o.has("chain") && cadena == null) {
            problemas.agregarFinal(etiqueta + ": 'chain' no coincide con startBlock y blocks o tiene bloques inválidos; se ignora");
        }
        return new DatosEntrada(indice, ruta, tipo, dueno.trim(), permiso, primero, bloques, creador, cadena);
    }

    /** Cadena guardada (opcional): enteros distintos dentro del disco, tantos como blocks y empezando en startBlock. */
    private static int[] cadena(JsonElement e, int bloques, int primero, int totalBloques) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != bloques) {
            return null;
        }
        JsonArray lista = e.getAsJsonArray();
        int[] cadena = new int[bloques];
        for (int i = 0; i < bloques; i++) {
            Integer b = entero(lista.get(i));
            if (b == null || b < 0 || b >= totalBloques) {
                return null;
            }
            for (int j = 0; j < i; j++) {
                if (cadena[j] == b) {
                    return null;
                }
            }
            cadena[i] = b;
        }
        return primero == Bloque.FIN || cadena[0] == primero ? cadena : null;
    }

    /** Anota las claves que no son del formato (no aparta la entrada). */
    private static void avisarClaves(JsonObject o, String[] permitidas, String donde, ListaEnlazada<String> problemas) {
        Nodo<String> clave = ClavesJson.de(o).getCabeza();
        while (clave != null) {
            boolean conocida = false;
            for (String permitida : permitidas) {
                conocida |= permitida.equals(clave.getDato());
            }
            if (!conocida) {
                problemas.agregarFinal(donde + ": clave desconocida '" + clave.getDato() + "' (se ignora)");
            }
            clave = clave.getSiguiente();
        }
    }

    /** Entero exacto del elemento, o null si falta o no es un número entero. */
    static Integer entero(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) {
            return null;
        }
        try {
            double valor = e.getAsDouble();
            if (valor != Math.rint(valor) || valor < Integer.MIN_VALUE || valor > Integer.MAX_VALUE) {
                return null;
            }
            return (int) valor;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Texto del elemento, o null si falta o no es un texto. */
    static String texto(JsonElement e) {
        return e != null && e.isJsonPrimitive() && ((JsonPrimitive) e).isString() ? e.getAsString() : null;
    }

    /** Anota el motivo y devuelve null (entrada apartada). */
    private static DatosEntrada apartar(String etiqueta, String motivo, ListaEnlazada<String> problemas) {
        problemas.agregarFinal(etiqueta + ": " + motivo + " (se aparta)");
        return null;
    }

    /** Archivo que no se puede usar: sin disco ni entradas. */
    private static DatosSistemaArchivos fatal(String motivo, ListaEnlazada<String> problemas) {
        problemas.agregarFinal(motivo);
        Debug.error(ORIGEN, "no se puede cargar el sistema de archivos: " + motivo);
        return new DatosSistemaArchivos(false, DiscoSD.BLOQUES_POR_DEFECTO, 0, new ListaEnlazada<>(), problemas);
    }

    /** Resume la lectura en el debugger. */
    private static void reportar(ListaEnlazada<String> problemas, int validas, int total) {
        if (problemas.estaVacia()) {
            Debug.ok(ORIGEN, "sistema de archivos leído: " + validas + " entradas válidas");
        } else {
            Debug.info(ORIGEN, "sistema de archivos leído: " + validas + " de " + total + " entradas válidas, "
                    + problemas.tamano() + " observaciones");
        }
    }
}
