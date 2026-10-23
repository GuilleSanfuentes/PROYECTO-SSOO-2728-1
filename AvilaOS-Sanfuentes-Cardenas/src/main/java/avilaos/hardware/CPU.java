package avilaos.hardware;

import avilaos.enums.ModoCPU;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;

/** Procesador: registros PC y MAR, modo de ejecución y contadores de uso. Ejecuta lo que el SO le indica. */
public class CPU implements ComponenteReloj {

    public static final int SIN_PROCESO = -1;

    private final String nombre;
    private final LineaInterrupciones interrupciones;
    private volatile SistemaOperativo sistemaOperativo;

    private ModoCPU modo = ModoCPU.SISTEMA_OPERATIVO;
    private boolean ociosa = true;
    private int idProceso = SIN_PROCESO;
    private String etiquetaProceso = "";
    private int pc;
    private int mar;
    private String ultimaAccion = "ninguna";
    private int accionesEnCiclo;
    private long ciclosUsuario;
    private long ciclosSO;
    private long ciclosOciosos;

    /** Crea la CPU conectada a la línea de interrupciones de su computador. */
    public CPU(String nombre, LineaInterrupciones interrupciones) {
        this.nombre = nombre;
        this.interrupciones = interrupciones;
    }

    /** Conecta el sistema operativo que la CPU ejecutará en cada ciclo. */
    public void conectarSistemaOperativo(SistemaOperativo so) {
        this.sistemaOperativo = so;
        Debug.ok(nombre, "sistema operativo conectado");
    }

    /** Un ciclo: recoge las interrupciones, ejecuta el SO y comprueba que hubo exactamente una acción. */
    @Override
    public void tick(long ciclo) {
        accionesEnCiclo = 0;
        ListaEnlazada<Interrupcion> pendientes = interrupciones.tomarTodas();
        SistemaOperativo so = sistemaOperativo;
        if (so == null) {
            quedarOciosa();
        } else {
            so.ejecutarCiclo(this, pendientes, ciclo);
        }
        if (accionesEnCiclo == 0) {
            Debug.error(nombre, "el SO no indicó ninguna acción en el ciclo " + ciclo + ", se cuenta como ociosa");
            quedarOciosa();
        } else if (accionesEnCiclo > 1) {
            Debug.error(nombre, accionesEnCiclo + " acciones en el ciclo " + ciclo + "; solo se permite una");
        }
    }

    /** Carga los registros de un proceso (parte del despacho). Falla si ya hay uno cargado. */
    public boolean cargarContexto(int id, String etiqueta, int pcProceso, int marProceso) {
        if (idProceso != SIN_PROCESO) {
            Debug.error(nombre, "cargar " + etiqueta + " sin guardar antes el contexto de " + etiquetaProceso);
            return false;
        }
        idProceso = id;
        etiquetaProceso = etiqueta;
        pc = pcProceso;
        mar = marProceso;
        Debug.ok(nombre, "contexto cargado: " + etiqueta + " PC=" + pc + " MAR=" + mar);
        return true;
    }

    /** Devuelve los registros del proceso cargado y deja la CPU sin proceso. Null si no había ninguno. */
    public ContextoCPU guardarContexto() {
        if (idProceso == SIN_PROCESO) {
            Debug.error(nombre, "guardar contexto sin proceso cargado");
            return null;
        }
        ContextoCPU contexto = new ContextoCPU(idProceso, etiquetaProceso, pc, mar);
        Debug.ok(nombre, "contexto guardado: " + etiquetaProceso + " PC=" + pc + " MAR=" + mar);
        idProceso = SIN_PROCESO;
        etiquetaProceso = "";
        return contexto;
    }

    /** Ejecuta la instrucción del proceso cargado en el modo indicado: PC y MAR avanzan una posición. */
    public boolean ejecutarInstruccion(ModoCPU modoInstruccion, String descripcion) {
        if (idProceso == SIN_PROCESO) {
            Debug.error(nombre, "ejecutar '" + descripcion + "' sin proceso cargado");
            return false;
        }
        registrarAccion(modoInstruccion, false);
        ultimaAccion = etiquetaProceso + " " + descripcion + " (PC " + pc + ")";
        pc++;
        mar++;
        Debug.ok(nombre, "[" + modo.getNombre() + "] " + ultimaAccion);
        return true;
    }

    /** Ocupa el ciclo con código del SO que no es instrucción de un proceso (ej. despacho). */
    public void ejecutarCodigoSO(String descripcion) {
        registrarAccion(ModoCPU.SISTEMA_OPERATIVO, false);
        ultimaAccion = "SO: " + descripcion;
        Debug.ok(nombre, "[" + modo.getNombre() + "] " + descripcion);
    }

    /** Deja la CPU sin trabajo en este ciclo. */
    public void quedarOciosa() {
        registrarAccion(modo, true);
        ultimaAccion = "ociosa";
        Debug.info(nombre, "ociosa");
    }

    /** Cuenta la acción del ciclo y actualiza el modo y los contadores de uso. */
    private void registrarAccion(ModoCPU nuevoModo, boolean sinTrabajo) {
        accionesEnCiclo++;
        modo = nuevoModo;
        ociosa = sinTrabajo;
        if (sinTrabajo) {
            ciclosOciosos++;
        } else if (nuevoModo == ModoCPU.USUARIO) {
            ciclosUsuario++;
        } else {
            ciclosSO++;
        }
    }

    /** Porcentaje de ciclos ocupados (usuario + SO) sobre el total. */
    public double getUtilizacion() {
        long total = ciclosUsuario + ciclosSO + ciclosOciosos;
        return total == 0 ? 0 : 100.0 * (ciclosUsuario + ciclosSO) / total;
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    public ModoCPU getModo() {
        return modo;
    }

    public boolean isOciosa() {
        return ociosa;
    }

    public int getIdProceso() {
        return idProceso;
    }

    public String getEtiquetaProceso() {
        return etiquetaProceso;
    }

    public int getPc() {
        return pc;
    }

    public int getMar() {
        return mar;
    }

    public String getUltimaAccion() {
        return ultimaAccion;
    }

    public long getCiclosUsuario() {
        return ciclosUsuario;
    }

    public long getCiclosSO() {
        return ciclosSO;
    }

    public long getCiclosOciosos() {
        return ciclosOciosos;
    }
}
