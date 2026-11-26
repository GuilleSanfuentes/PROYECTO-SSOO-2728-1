package avilaos.so.disco;

import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.ControladorDisco;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.metricas.CambioPolitica;
import avilaos.metricas.MetricasDisco;
import avilaos.metricas.PuntoDesplazamiento;
import avilaos.metricas.SerieDesplazamiento;
import avilaos.so.disco.politicas.FabricaPoliticasDisco;
import avilaos.so.disco.politicas.Parada;
import avilaos.so.disco.politicas.PoliticaDisco;
import java.util.concurrent.Semaphore;

/** Planificador del disco: la única cola de E/S, la política activa y la orden de cada ciclo al disco. */
public class PlanificadorDisco implements ControladorDisco {

    /** Aviso de que el disco terminó una solicitud (el Kernel despierta al proceso). */
    public interface AlTerminar {

        /** Se llama fuera de las secciones críticas, en el hilo del disco. */
        void solicitudAtendida(SolicitudDisco solicitud, long ciclo);
    }

    public static final int HISTORIAL_MAXIMO = 2000;
    private static final int GIROS_POR_CICLO = 3;

    private final String nombre;
    private final DiscoSD disco;
    private final LogEventos log;
    private final Semaphore mutex = new Semaphore(1);
    private final ListaEnlazada<SolicitudDisco> cola = new ListaEnlazada<>();
    private final ListaEnlazada<SolicitudDisco> historial = new ListaEnlazada<>();
    private final long[] atendidasPorPolitica = new long[PoliticaDiscoTipo.values().length];
    private PoliticaDisco politica;
    private SolicitudDisco actual;
    private PoliticaDiscoTipo politicaDeActual;
    private int indiceBloque;
    private int siguienteId = 1;
    private long atendidas;
    private long esperaTotal;
    private long esperaAnticipadaTotal;
    private long ciclosAnticipados;
    private long ultimoCicloDisco;
    private final SerieDesplazamiento serie = new SerieDesplazamiento();
    private final ListaEnlazada<CambioPolitica> cambios = new ListaEnlazada<>();
    private final int anticipacion;
    private boolean colaEstrenable = true;
    private int ciclosPorEsperar;
    private volatile AlTerminar alTerminar;

    /** Crea el planificador sin espera anticipatoria y lo conecta como controlador del disco. */
    public PlanificadorDisco(String nombre, DiscoSD disco, PoliticaDiscoTipo inicial, LogEventos log) {
        this(nombre, disco, inicial, log, 0);
    }

    /** Crea el planificador que espera hasta anticipacion ciclos antes de elegir cuando llega trabajo a un disco libre. */
    public PlanificadorDisco(String nombre, DiscoSD disco, PoliticaDiscoTipo inicial, LogEventos log, int anticipacion) {
        this.nombre = nombre;
        this.disco = disco;
        this.log = log;
        if (anticipacion < 0) {
            Debug.error(nombre, "espera anticipatoria inválida " + anticipacion + ", se desactiva");
            anticipacion = 0;
        }
        this.anticipacion = anticipacion;
        if (inicial == null) {
            Debug.error(nombre, "política de disco inicial nula, se usa FIFO");
        }
        this.politica = FabricaPoliticasDisco.crear(inicial);
        disco.setControlador(this);
        Debug.ok(nombre, "planificador de disco listo con " + politica.getTipo().getNombre());
    }

    /** Agrega una solicitud a la cola del disco. Devuelve la solicitud, o null con ERROR si los datos no son válidos. */
    public SolicitudDisco encolar(int idProceso, String etiqueta, TipoOperacionES operacion, String ruta, int[] bloques, long ciclo) {
        String problema = validar(etiqueta, operacion, bloques);
        if (problema != null) {
            Debug.error(nombre, "solicitud rechazada de " + etiqueta + ": " + problema);
            return null;
        }
        SolicitudDisco solicitud;
        mutex.acquireUninterruptibly();
        try {
            solicitud = new SolicitudDisco(siguienteId++, idProceso, etiqueta, operacion, ruta, bloques.clone(), ciclo,
                    ciclosAnticipados);
            cola.agregarFinal(solicitud);
        } finally {
            mutex.release();
        }
        Debug.ok(nombre, "encola #" + solicitud.id() + ": " + solicitud.describir());
        registrar(ciclo, etiqueta + " solicita al disco: " + operacion.getNombre() + " en el bloque " + solicitud.destino());
        return solicitud;
    }

    /** Un ciclo del disco: sigue con la solicitud en curso o pide a la política la próxima parada. */
    @Override
    public void ejecutarCicloDisco(DiscoSD d, long ciclo) {
        SolicitudDisco terminada = null;
        String evento = null;
        mutex.acquireUninterruptibly();
        try {
            if (actual == null) {
                if (anticipar()) {
                    evento = ciclosPorEsperar == anticipacion - 1 ? "El disco espera " + anticipacion
                            + " ciclos anticipando solicitudes (" + politica.getTipo().getNombre() + ")" : null;
                    disco.ocioso();
                } else {
                    evento = decidir(ciclo);
                }
            }
            if (actual != null) {
                int bloque = actual.bloques()[indiceBloque];
                if (disco.getCabezal() != bloque) {
                    disco.moverHacia(bloque);
                } else {
                    disco.transferir(bloque, actual.operacion().isEscritura() ? "escribe" : "lee");
                    indiceBloque++;
                    if (indiceBloque == actual.cantidadBloques()) {
                        terminada = actual;
                        cerrarActual();
                    }
                }
            }
            ultimoCicloDisco = ciclo;
            serie.registrar(ciclo, disco.getDesplazamientoTotal());
        } finally {
            mutex.release();
        }
        if (evento != null) {
            registrar(ciclo, evento);
        }
        if (terminada != null) {
            Debug.ok(nombre, "termina #" + terminada.id() + ": " + terminada.describir());
            registrar(ciclo, "El disco termina la solicitud de " + terminada.etiqueta() + " (" + terminada.operacion().getNombre()
                    + ", " + terminada.cantidadBloques() + " bloques)");
            AlTerminar aviso = alTerminar;
            if (aviso != null) {
                aviso.solicitudAtendida(terminada, ciclo);
            }
        }
    }

    /** Indica si este ciclo se gasta esperando más solicitudes; la ventana empieza con la primera y no se reinicia. */
    private boolean anticipar() {
        if (cola.estaVacia()) {
            return false;
        }
        if (colaEstrenable) {
            colaEstrenable = false;
            ciclosPorEsperar = anticipacion;
        }
        if (ciclosPorEsperar == 0) {
            return false;
        }
        ciclosPorEsperar--;
        ciclosAnticipados++;
        return true;
    }

    /** Deja lista una nueva ventana de espera si no queda nada en la cola (con el mutex tomado). */
    private void rearmarEspera() {
        if (cola.estaVacia()) {
            colaEstrenable = true;
            ciclosPorEsperar = 0;
        }
    }

    /** Quita de la cola la primera solicitud del proceso. Devuelve null si no tiene ninguna en cola. */
    public SolicitudDisco cancelar(int idProceso, long ciclo) {
        return quitarDeCola(idProceso, true, ciclo);
    }

    /** Quita de la cola la solicitud con ese número. Devuelve null si no está en cola (no existe o ya se atiende). */
    public SolicitudDisco cancelarSolicitud(int idSolicitud, long ciclo) {
        return quitarDeCola(idSolicitud, false, ciclo);
    }

    /** Busca en la cola por proceso o por número de solicitud y la retira. */
    private SolicitudDisco quitarDeCola(int id, boolean porProceso, long ciclo) {
        SolicitudDisco cancelada = null;
        mutex.acquireUninterruptibly();
        try {
            Nodo<SolicitudDisco> nodo = cola.getCabeza();
            while (nodo != null && cancelada == null) {
                if ((porProceso ? nodo.getDato().idProceso() : nodo.getDato().id()) == id) {
                    cancelada = nodo.getDato();
                }
                nodo = nodo.getSiguiente();
            }
            if (cancelada != null) {
                cola.eliminar(cancelada);
                if (actual == null) {
                    rearmarEspera();
                }
            }
        } finally {
            mutex.release();
        }
        if (cancelada != null) {
            Debug.ok(nombre, "cancela #" + cancelada.id() + ": " + cancelada.describir());
            registrar(ciclo, "El disco cancela la solicitud en cola de " + cancelada.etiqueta() + " (bloque "
                    + cancelada.destino() + ")");
        }
        return cancelada;
    }

    /** Ciclos de la espera anticipatoria configurada (0 = desactivada). */
    public int getAnticipacion() {
        return anticipacion;
    }

    /** Pide paradas a la política hasta tomar una solicitud o gastar el ciclo moviendo o saltando (con el mutex tomado). */
    private String decidir(long ciclo) {
        for (int giro = 0; giro < GIROS_POR_CICLO; giro++) {
            Parada parada = politica.siguiente(cola, disco.getCabezal(), disco.getDireccion(), disco.getTotalPosiciones() - 1);
            if (parada == null) {
                disco.ocioso();
                return null;
            }
            if (parada.esAtencion()) {
                actual = parada.solicitud();
                politicaDeActual = politica.getTipo();
                cola.eliminar(actual);
                indiceBloque = 0;
                esperaTotal += ciclo - actual.cicloLlegada();
                esperaAnticipadaTotal += ciclosAnticipados - actual.anticipadosAlLlegar();
                return "El disco atiende la solicitud de " + actual.etiqueta() + " en el bloque " + actual.destino()
                        + " (" + politica.getTipo().getNombre() + ")";
            }
            if (parada.destino() == disco.getCabezal()) {
                disco.setDireccion(disco.getDireccion().opuesta());
                continue;
            }
            if (parada.salto()) {
                disco.saltarA(parada.destino());
                return "El cabezal salta al bloque " + parada.destino() + " (" + politica.getTipo().getNombre() + ")";
            }
            disco.moverHacia(parada.destino());
            return null;
        }
        Debug.error(nombre, "la política " + politica.getTipo().getNombre() + " no decidió una parada en el ciclo " + ciclo);
        disco.ocioso();
        return null;
    }

    /** Cuenta la solicitud terminada y la pasa al historial (con el mutex tomado). */
    private void cerrarActual() {
        atendidas++;
        atendidasPorPolitica[politicaDeActual.ordinal()]++;
        historial.agregarFinal(actual);
        if (historial.tamano() > HISTORIAL_MAXIMO) {
            historial.eliminarEn(0);
        }
        actual = null;
        indiceBloque = 0;
        rearmarEspera();
    }

    /** Cambia la política en plena ejecución; la solicitud en curso termina normalmente. */
    public boolean cambiarPolitica(PoliticaDiscoTipo tipo, long ciclo) {
        if (tipo == null) {
            Debug.error(nombre, "política de disco nula");
            return false;
        }
        PoliticaDiscoTipo anterior;
        mutex.acquireUninterruptibly();
        try {
            anterior = politica.getTipo();
            politica = FabricaPoliticasDisco.crear(tipo);
            if (anterior != tipo) {
                cambios.agregarFinal(new CambioPolitica(ultimoCicloDisco + 1, anterior, tipo));
            }
        } finally {
            mutex.release();
        }
        Debug.ok(nombre, "política de disco: " + anterior.getNombre() + " -> " + tipo.getNombre());
        registrar(ciclo, "El disco cambia de política: " + anterior.getNombre() + " -> " + tipo.getNombre());
        return true;
    }

    /** Motivo por el que la solicitud no es válida, o null. */
    private String validar(String etiqueta, TipoOperacionES operacion, int[] bloques) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return "sin proceso";
        }
        if (operacion == null) {
            return "sin operación";
        }
        if (bloques == null || bloques.length == 0) {
            return "sin bloques";
        }
        for (int b : bloques) {
            if (!disco.esPosicionValida(b)) {
                return "el bloque " + b + " no existe";
            }
            if (disco.esSwap(b) != operacion.isIntercambio()) {
                return operacion.getNombre() + " no puede usar el bloque " + b
                        + (operacion.isIntercambio() ? " (fuera del swap)" : " (es del swap)");
            }
        }
        return null;
    }

    /** Anota el evento en el log, si hay uno asociado. */
    private void registrar(long ciclo, String texto) {
        if (log != null) {
            log.registrar(ciclo, texto);
        }
    }

    /** Define quién recibe el aviso de cada solicitud terminada. */
    public void setAlTerminar(AlTerminar aviso) {
        alTerminar = aviso;
    }

    /** Política activa. */
    public PoliticaDiscoTipo getPolitica() {
        mutex.acquireUninterruptibly();
        try {
            return politica.getTipo();
        } finally {
            mutex.release();
        }
    }

    /** Solicitudes esperando en la cola (sin contar la que se atiende). */
    public int cantidadEnCola() {
        mutex.acquireUninterruptibly();
        try {
            return cola.tamano();
        } finally {
            mutex.release();
        }
    }

    /** Copia de la cola en orden de llegada. */
    public ListaEnlazada<SolicitudDisco> copiarCola() {
        return copiar(cola);
    }

    /** Copia de las solicitudes atendidas, en el orden en que se atendieron. */
    public ListaEnlazada<SolicitudDisco> copiarHistorial() {
        return copiar(historial);
    }

    /** Solicitud que el disco atiende ahora, o null. */
    public SolicitudDisco getActual() {
        mutex.acquireUninterruptibly();
        try {
            return actual;
        } finally {
            mutex.release();
        }
    }

    /** Indica si no hay nada en curso ni en cola. */
    public boolean estaInactivo() {
        mutex.acquireUninterruptibly();
        try {
            return actual == null && cola.estaVacia();
        } finally {
            mutex.release();
        }
    }

    /** Solicitudes terminadas desde el inicio. */
    public long getAtendidas() {
        mutex.acquireUninterruptibly();
        try {
            return atendidas;
        } finally {
            mutex.release();
        }
    }

    /** Solicitudes terminadas que eligió la política indicada, aunque hayan acabado después de cambiarla. */
    public long getAtendidasPor(PoliticaDiscoTipo tipo) {
        mutex.acquireUninterruptibly();
        try {
            return tipo == null ? 0 : atendidasPorPolitica[tipo.ordinal()];
        } finally {
            mutex.release();
        }
    }

    /** Espera total: ciclos promedio desde que cada solicitud entró en la cola hasta que el disco la eligió. */
    public double getEsperaPromedio() {
        mutex.acquireUninterruptibly();
        try {
            return promedio(esperaTotal);
        } finally {
            mutex.release();
        }
    }

    /** La espera promedio sin contar los ciclos de espera anticipatoria que cada solicitud pasó en la cola. */
    public double getEsperaPromedioSinAnticipacion() {
        mutex.acquireUninterruptibly();
        try {
            return promedio(esperaTotal - esperaAnticipadaTotal);
        } finally {
            mutex.release();
        }
    }

    /** Desplazamiento acumulado por ciclo desde el inicio. */
    public PuntoDesplazamiento[] copiarSerie() {
        mutex.acquireUninterruptibly();
        try {
            return serie.copiar();
        } finally {
            mutex.release();
        }
    }

    /** Cambios de política en orden. */
    public ListaEnlazada<CambioPolitica> copiarCambios() {
        ListaEnlazada<CambioPolitica> copia = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            Nodo<CambioPolitica> nodo = cambios.getCabeza();
            while (nodo != null) {
                copia.agregarFinal(nodo.getDato());
                nodo = nodo.getSiguiente();
            }
        } finally {
            mutex.release();
        }
        return copia;
    }

    /** Foto de todas las métricas bajo un solo mutex; los contadores de suspensión los aporta quien la pide. */
    public MetricasDisco fotografiar(long suspensiones, long reactivaciones) {
        mutex.acquireUninterruptibly();
        try {
            CambioPolitica[] copiaCambios = new CambioPolitica[cambios.tamano()];
            Nodo<CambioPolitica> nodo = cambios.getCabeza();
            for (int i = 0; i < copiaCambios.length; i++) {
                copiaCambios[i] = nodo.getDato();
                nodo = nodo.getSiguiente();
            }
            return new MetricasDisco(ultimoCicloDisco, politica.getTipo(), disco.getDesplazamientoTotal(), atendidas,
                    atendidasPorPolitica, atendidas + (actual != null ? 1 : 0), promedio(esperaTotal),
                    promedio(esperaTotal - esperaAnticipadaTotal), suspensiones, reactivaciones, serie.copiar(), copiaCambios);
        } finally {
            mutex.release();
        }
    }

    /** La última solicitud terminada, o null si todavía no hay ninguna. */
    public SolicitudDisco getUltimaAtendida() {
        mutex.acquireUninterruptibly();
        try {
            return historial.ultimo();
        } finally {
            mutex.release();
        }
    }

    /** Reparte una suma entre las solicitudes que ya empezaron a atenderse (con el mutex tomado). */
    private double promedio(long suma) {
        long empezadas = atendidas + (actual != null ? 1 : 0);
        return empezadas == 0 ? 0 : (double) suma / empezadas;
    }

    public DiscoSD getDisco() {
        return disco;
    }

    /** Copia una lista bajo el mutex. */
    private ListaEnlazada<SolicitudDisco> copiar(ListaEnlazada<SolicitudDisco> origen) {
        ListaEnlazada<SolicitudDisco> copia = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            Nodo<SolicitudDisco> nodo = origen.getCabeza();
            while (nodo != null) {
                copia.agregarFinal(nodo.getDato());
                nodo = nodo.getSiguiente();
            }
        } finally {
            mutex.release();
        }
        return copia;
    }
}
