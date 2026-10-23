package avilaos.so;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoProceso;
import avilaos.log.Debug;
import avilaos.so.programas.Instruccion;
import avilaos.so.programas.Programa;

/**
 * Bloque de control del proceso (asociado 1:1 con su único hilo, H1).
 * No es seguro entre hilos: lo sincroniza el kernel de su computador.
 */
public class PCB {

    private static final String ORIGEN = "PCB";

    private final int id;
    private final String nombre;
    private final TipoProceso tipo;
    private final Programa programa;
    private final int memoria;
    private final int prioridadBase;
    private final int deadlineInicial;
    private final long cicloLlegada;

    private int computador;
    private EstadoProceso estado;
    private MotivoBloqueo motivoBloqueo;
    private String detalleBloqueo;
    private int prioridadEfectiva;
    private int deadlineRestante;
    private int contador;
    private int mar;
    private int elementosProcesados;
    private int ciclosEsperandoEnListos;
    private long cicloPrimeraEjecucion;
    private long cicloFin;
    private long cicloInicioBloqueoSemaforo;
    private long ciclosBloqueadoSemaforo;
    private boolean deadlineVencido;

    /** Crea el PCB en estado Nuevo. Usar FabricaProcesos, que valida los datos. */
    PCB(int id, String nombre, TipoProceso tipo, Programa programa, int memoria, int prioridad,
            int deadline, int computador, long cicloLlegada) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.programa = programa;
        this.memoria = memoria;
        this.prioridadBase = prioridad;
        this.prioridadEfectiva = prioridad;
        this.deadlineInicial = deadline;
        this.deadlineRestante = deadline;
        this.computador = computador;
        this.cicloLlegada = cicloLlegada;
        this.estado = EstadoProceso.NUEVO;
        this.motivoBloqueo = MotivoBloqueo.NINGUNO;
        this.detalleBloqueo = "";
        this.cicloPrimeraEjecucion = -1;
        this.cicloFin = -1;
        this.cicloInicioBloqueoSemaforo = -1;
    }

    /** Etiqueta del tipo P3-H1 usada en el log. */
    public String getEtiqueta() {
        return "P" + id + "-H1";
    }

    /** Instrucción a la que apunta el Contador, o null si el programa terminó. */
    public Instruccion instruccionActual() {
        return programaTerminado() ? null : programa.instruccionEn(contador);
    }

    /** Avanza Contador y MAR una posición tras ejecutar una instrucción. */
    public boolean avanzarContador() {
        if (programaTerminado()) {
            Debug.error(ORIGEN, getEtiqueta() + " intenta avanzar el Contador más allá del programa (" + contador + ")");
            return false;
        }
        contador++;
        mar++;
        return true;
    }

    /** Indica si ya se ejecutaron todas las instrucciones. */
    public boolean programaTerminado() {
        return contador >= programa.totalInstrucciones();
    }

    /** Cambia de estado si la transición es válida; si no, informa el error y no cambia nada. */
    public boolean cambiarEstado(EstadoProceso nuevo) {
        if (!esTransicionValida(estado, nuevo)) {
            Debug.error(ORIGEN, getEtiqueta() + " transición inválida " + estado.getNombre() + " -> " + nuevo.getNombre());
            return false;
        }
        Debug.ok(ORIGEN, getEtiqueta() + " " + estado.getNombre() + " -> " + nuevo.getNombre());
        estado = nuevo;
        return true;
    }

    /** Determinador de estados. Define el estado del proceso para avanzar o no al siguiente paso. */
    public static boolean esTransicionValida(EstadoProceso desde, EstadoProceso hacia) {
        if (desde == hacia || desde == EstadoProceso.TERMINADO) {
            return false;
        }
        return switch (hacia) {
            case NUEVO -> false;
            case LISTO -> desde == EstadoProceso.NUEVO || desde == EstadoProceso.EJECUCION || desde == EstadoProceso.BLOQUEADO;
            case EJECUCION -> desde == EstadoProceso.LISTO;
            case BLOQUEADO -> desde == EstadoProceso.EJECUCION;
            case TERMINADO -> true;
        };
    }

    /** Pasa a Bloqueado con su motivo; detalle describe qué espera (ej. "semáforo 'vacío' de B1"). */
    public boolean bloquear(MotivoBloqueo motivo, String detalle, long ciclo) {
        if (motivo == MotivoBloqueo.NINGUNO) {
            Debug.error(ORIGEN, getEtiqueta() + " no se puede bloquear sin motivo");
            return false;
        }
        if (!cambiarEstado(EstadoProceso.BLOQUEADO)) {
            return false;
        }
        motivoBloqueo = motivo;
        detalleBloqueo = detalle == null ? "" : detalle;
        if (motivo == MotivoBloqueo.SEMAFORO) {
            cicloInicioBloqueoSemaforo = ciclo;
        }
        return true;
    }

    /** Sale de Bloqueado hacia Listo y acumula el tiempo bloqueado en semáforo. */
    public boolean desbloquear(long ciclo) {
        if (estado != EstadoProceso.BLOQUEADO) {
            Debug.error(ORIGEN, getEtiqueta() + " desbloquear estando en " + estado.getNombre());
            return false;
        }
        acumularBloqueoSemaforo(ciclo);
        motivoBloqueo = MotivoBloqueo.NINGUNO;
        detalleBloqueo = "";
        return cambiarEstado(EstadoProceso.LISTO);
    }

    /** Pasa a Ejecución y registra la primera ejecución. Conserva la prioridad envejecida mientras está en CPU. */
    public boolean despachar(long ciclo) {
        if (!cambiarEstado(EstadoProceso.EJECUCION)) {
            return false;
        }
        if (cicloPrimeraEjecucion < 0) {
            cicloPrimeraEjecucion = ciclo;
        }
        ciclosEsperandoEnListos = 0;
        return true;
    }

    /** Vuelve a la prioridad base al salir de la CPU sin terminar (expulsión o bloqueo). */
    public void restaurarPrioridad() {
        prioridadEfectiva = prioridadBase;
        ciclosEsperandoEnListos = 0;
    }

    /** Pasa a Terminado; porDeadline indica si fue por vencimiento del deadline. */
    public boolean terminar(long ciclo, boolean porDeadline) {
        acumularBloqueoSemaforo(ciclo);
        if (!cambiarEstado(EstadoProceso.TERMINADO)) {
            return false;
        }
        motivoBloqueo = MotivoBloqueo.NINGUNO;
        detalleBloqueo = "";
        cicloFin = ciclo;
        deadlineVencido = porDeadline;
        return true;
    }

    /** Resta un ciclo al deadline. Devuelve true si llegó a 0. */
    public boolean descontarDeadline() {
        if (deadlineRestante > 0) {
            deadlineRestante--;
        }
        return deadlineRestante == 0;
    }

    /** Suma un ciclo de espera en Listos; cada cadaCiclos mejora la prioridad en 1. Devuelve true si mejoró. */
    public boolean envejecer(int cadaCiclos) {
        if (estado != EstadoProceso.LISTO || cadaCiclos <= 0) {
            return false;
        }
        ciclosEsperandoEnListos++;
        if (ciclosEsperandoEnListos < cadaCiclos || prioridadEfectiva <= FabricaProcesos.PRIORIDAD_MAXIMA) {
            return false;
        }
        ciclosEsperandoEnListos = 0;
        prioridadEfectiva--;
        return true;
    }

    /** Suma un elemento producido o consumido. */
    public void registrarElemento() {
        elementosProcesados++;
    }

    /** Ciclos entre la llegada y la primera ejecución, o -1 si aún no se ejecutó. */
    public long getTiempoRespuesta() {
        return cicloPrimeraEjecucion < 0 ? -1 : cicloPrimeraEjecucion - cicloLlegada;
    }

    /** Instrucciones que faltan por ejecutar. */
    public int getTiempoRestante() {
        return programa.totalInstrucciones() - contador;
    }

    /** Cierra un bloqueo en semáforo abierto y suma su duración. */
    private void acumularBloqueoSemaforo(long ciclo) {
        if (cicloInicioBloqueoSemaforo >= 0) {
            ciclosBloqueadoSemaforo += ciclo - cicloInicioBloqueoSemaforo;
            cicloInicioBloqueoSemaforo = -1;
        }
    }

    /** Asigna el computador; solo se permite una vez (no hay migración). */
    public boolean asignarComputador(int computador) {
        if (this.computador != DatosProceso.ASIGNACION_AUTOMATICA) {
            Debug.error(ORIGEN, getEtiqueta() + " ya está asignado al computador " + this.computador);
            return false;
        }
        this.computador = computador;
        return true;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoProceso getTipo() {
        return tipo;
    }

    public Programa getPrograma() {
        return programa;
    }

    public int getMemoria() {
        return memoria;
    }

    public int getPrioridadBase() {
        return prioridadBase;
    }

    public int getPrioridadEfectiva() {
        return prioridadEfectiva;
    }

    public int getDeadlineInicial() {
        return deadlineInicial;
    }

    public int getDeadlineRestante() {
        return deadlineRestante;
    }

    public int getComputador() {
        return computador;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public MotivoBloqueo getMotivoBloqueo() {
        return motivoBloqueo;
    }

    public String getDetalleBloqueo() {
        return detalleBloqueo;
    }

    public int getContador() {
        return contador;
    }

    public int getMar() {
        return mar;
    }

    public int getElementosProcesados() {
        return elementosProcesados;
    }

    public long getCicloLlegada() {
        return cicloLlegada;
    }

    public long getCicloPrimeraEjecucion() {
        return cicloPrimeraEjecucion;
    }

    public long getCicloFin() {
        return cicloFin;
    }

    public long getCiclosBloqueadoSemaforo() {
        return ciclosBloqueadoSemaforo;
    }

    public boolean isDeadlineVencido() {
        return deadlineVencido;
    }

    /** Resumen del tipo P3-H1 'calc' [Listo] prio=2 dl=88 Contador=12/40. */
    @Override
    public String toString() {
        String texto = getEtiqueta() + " '" + nombre + "' [" + estado.getNombre() + "] prio=" + prioridadEfectiva
                + " dl=" + deadlineRestante + " Contador=" + contador + "/" + programa.totalInstrucciones();
        if (estado == EstadoProceso.BLOQUEADO) {
            texto += " (" + motivoBloqueo.getNombre() + (detalleBloqueo.isEmpty() ? "" : ": " + detalleBloqueo) + ")";
        }
        return texto;
    }
}
