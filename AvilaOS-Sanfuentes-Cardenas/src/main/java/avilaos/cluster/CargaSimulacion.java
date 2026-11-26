package avilaos.cluster;

import avilaos.config.ArchivoProcesos;
import avilaos.config.ArchivoSistemaArchivos;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfigDisco;
import avilaos.config.ConfiguracionSistema;
import avilaos.config.DatosProcesos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import java.util.concurrent.Semaphore;

/** Guarda los dos JSON obligatorios del Proyecto 2 y solo arma el sistema cuando ambos están cargados. */
public class CargaSimulacion {

    /** Resultado de armar: el sistema o el motivo del rechazo, y las observaciones de ambas cargas. */
    public record Resultado(Cluster cluster, String motivo, ListaEnlazada<String> problemas) {

        /** Indica si el sistema se pudo armar. */
        public boolean armado() {
            return cluster != null;
        }
    }

    private static final String ORIGEN = "Carga";

    private final Semaphore mutex = new Semaphore(1);
    private DatosSistemaArchivos sistema;
    private DatosProcesos procesos;

    /** Lee el JSON del sistema de archivos; solo reemplaza al anterior si se puede cargar. */
    public DatosSistemaArchivos cargarSistemaArchivos(String ruta) {
        DatosSistemaArchivos datos = ArchivoSistemaArchivos.leer(ruta);
        usarSistemaArchivos(datos);
        return datos;
    }

    /** Lee el JSON de procesos; solo reemplaza al anterior si se puede cargar. */
    public DatosProcesos cargarProcesos(String ruta) {
        DatosProcesos datos = ArchivoProcesos.leer(ruta);
        usarProcesos(datos);
        return datos;
    }

    /** Acepta un sistema de archivos ya leído. Devuelve false si no se puede cargar. */
    public boolean usarSistemaArchivos(DatosSistemaArchivos datos) {
        if (datos == null || !datos.cargable()) {
            Debug.info(ORIGEN, "el JSON del sistema de archivos no se puede usar; se conserva el anterior");
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            sistema = datos;
        } finally {
            mutex.release();
        }
        Debug.ok(ORIGEN, "sistema de archivos listo para armar");
        return true;
    }

    /** Acepta unos procesos ya leídos. Devuelve false si no se pueden cargar. */
    public boolean usarProcesos(DatosProcesos datos) {
        if (datos == null || !datos.cargable()) {
            Debug.info(ORIGEN, "el JSON de procesos no se puede usar; se conserva el anterior");
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            procesos = datos;
        } finally {
            mutex.release();
        }
        Debug.ok(ORIGEN, "procesos listos para armar");
        return true;
    }

    /** Motivo legible por el que todavía no se puede armar el sistema, o null si ya están los dos JSON. */
    public String motivoNoArmar() {
        mutex.acquireUninterruptibly();
        try {
            return motivo(sistema, procesos);
        } finally {
            mutex.release();
        }
    }

    public DatosSistemaArchivos getSistemaArchivos() {
        mutex.acquireUninterruptibly();
        try {
            return sistema;
        } finally {
            mutex.release();
        }
    }

    public DatosProcesos getProcesos() {
        mutex.acquireUninterruptibly();
        try {
            return procesos;
        } finally {
            mutex.release();
        }
    }

    /** Arma un sistema nuevo con un solo computador, su disco y los archivos cargados; no inicia el reloj. */
    public Resultado armar(LogEventos log) {
        DatosSistemaArchivos fs;
        DatosProcesos datos;
        mutex.acquireUninterruptibly();
        try {
            fs = sistema;
            datos = procesos;
        } finally {
            mutex.release();
        }
        String motivo = motivo(fs, datos);
        if (motivo != null) {
            Debug.info(ORIGEN, "no se arma el sistema: " + motivo);
            return new Resultado(null, motivo, new ListaEnlazada<>());
        }
        if (datos.debugger() != null) {
            Debug.setActivo(datos.debugger());
        }
        ConfigDisco disco = new ConfigDisco(fs.totalBloques(), datos.bloquesSwap(), fs.cabezalInicial(),
                datos.velocidadCabezal(), datos.politicaDisco(), datos.ciclosAnticipacion());
        Cluster cluster = new Cluster(configuracion(datos), log, disco);
        ListaEnlazada<String> problemas = new ListaEnlazada<>();
        copiar(fs.problemas(), problemas);
        copiar(datos.problemas(), problemas);
        cluster.getComputador(0).getSistemaArchivos().cargar(fs.entradas(), problemas);
        Debug.ok(ORIGEN, "sistema armado con " + problemas.tamano() + " observaciones de carga");
        return new Resultado(cluster, null, problemas);
    }

    /** Configuración de ÁvilaOS para el Proyecto 2: siempre un computador y sin procesos del Proyecto 1. */
    private static ConfiguracionSistema configuracion(DatosProcesos datos) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(datos.ram(), datos.politica(), datos.quantumInterno()));
        return new ConfiguracionSistema(Debug.isActivo(), datos.duracionCicloMs(), CondicionesParada.porDefecto(),
                ConfiguracionSistema.DEF_LATENCIA, ConfiguracionSistema.DEF_ENVEJECIMIENTO, computadores,
                datos.buffers(), new ListaEnlazada<>(), 0);
    }

    /** Texto que dice cuál de los dos JSON falta, o null si no falta ninguno. */
    private static String motivo(DatosSistemaArchivos fs, DatosProcesos datos) {
        if (fs == null && datos == null) {
            return "Falta cargar el JSON del sistema de archivos y el JSON de procesos";
        }
        if (fs == null) {
            return "Falta cargar el JSON del sistema de archivos";
        }
        return datos == null ? "Falta cargar el JSON de procesos" : null;
    }

    /** Copia los textos de una lista al final de otra. */
    private static void copiar(ListaEnlazada<String> origen, ListaEnlazada<String> destino) {
        Nodo<String> nodo = origen.getCabeza();
        while (nodo != null) {
            destino.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
    }
}
