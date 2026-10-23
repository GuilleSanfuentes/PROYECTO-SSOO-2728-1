package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.EstadoProceso;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.PCB;
import avilaos.sync.DatosBuffer;
import avilaos.vista.ConsolaTerminal;
import avilaos.vista.ReporteTerminal;

/** Pruebas de terminal de la etapa 4: kernel, planificador con las 4 políticas, admisión, deadlines y clúster. */
public final class PruebasPaso4 {

    private static final String ORIGEN = "Pruebas";
    private static final int AUTO = DatosProceso.ASIGNACION_AUTOMATICA;
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso4() {
    }

    /** Ejecuta todas las pruebas de la etapa 4 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(4, "Etapa 4: kernel, planificador y computador");
        probarFCFS();
        probarRoundRobin();
        probarPrioridadesApropiativas();
        probarEDF();
        probarDeadline();
        probarDeadlineBloqueado();
        probarEnvejecimiento();
        probarCambioDePolitica();
        probarEntradaSalida();
        probarAdmisionYAsignacion();
        probarMaxCiclosConHilo();
        probarConsola();
        probarConfiguracionReal();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** FCFS: A y luego B, sin expulsión; cada despacho cuesta 1 ciclo en modo SO. */
    private static void probarFCFS() {
        r.grupo("FCFS (orden de llegada, cambio de contexto de 1 ciclo)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        PCB a = crear(c, cpu("A-fcfs", 5, 3, 100, 0));
        PCB b = crear(c, cpu("B-fcfs", 5, 2, 100, 0));
        c.getReloj().ejecutarCiclos(10);
        CPU cpu = c.getComputador(0).getCpu();
        r.verificar("A termina en el ciclo 4 y B en el 7", a.getCicloFin() == 4 && b.getCicloFin() == 7,
                a.getCicloFin() + ", " + b.getCicloFin());
        r.verificar("tiempo de respuesta de B = 5", b.getTiempoRespuesta() == 5, String.valueOf(b.getTiempoRespuesta()));
        r.verificar("CPU: 5 usuario, 2 SO, 3 ociosa", cpu.getCiclosUsuario() == 5 && cpu.getCiclosSO() == 2 && cpu.getCiclosOciosos() == 3,
                cpu.getCiclosUsuario() + "/" + cpu.getCiclosSO() + "/" + cpu.getCiclosOciosos());
        r.seccion(" FCFS        " + ReporteTerminal.lineaCPU(cpu).trim());
        c.apagar();
    }

    /** Round Robin q=2: A y B se alternan cada 2 instrucciones. */
    private static void probarRoundRobin() {
        r.grupo("Round Robin con quantum 2 (expulsión por quantum)");
        Cluster c = cluster(PoliticaTipo.ROUND_ROBIN, 2, 1024, 0, 0, 0);
        PCB a = crear(c, cpu("A-rr", 5, 4, 100, 0));
        PCB b = crear(c, cpu("B-rr", 5, 3, 100, 0));
        c.getReloj().ejecutarCiclos(12);
        CPU cpu = c.getComputador(0).getCpu();
        r.verificar("A termina en el ciclo 9 y B en el 11", a.getCicloFin() == 9 && b.getCicloFin() == 11,
                a.getCicloFin() + ", " + b.getCicloFin());
        r.verificar("4 cambios de contexto (ciclos 1, 4, 7 y 10)", cpu.getCiclosSO() == 4, String.valueOf(cpu.getCiclosSO()));
        r.seccion(" RR q=2      " + ReporteTerminal.lineaCPU(cpu).trim());
        c.apagar();
    }

    /** Prioridades: B (prioridad 1) llega en el ciclo 3 y expulsa a A (prioridad 5). */
    private static void probarPrioridadesApropiativas() {
        r.grupo("Prioridades apropiativas (expulsión al llegar uno más importante)");
        Cluster c = cluster(PoliticaTipo.PRIORIDADES, 3, 1024, 0, 0, 0);
        PCB a = crear(c, cpu("A-prio", 5, 6, 100, 0));
        c.getReloj().ejecutarCiclos(3);
        PCB b = crear(c, cpu("B-prio", 1, 2, 100, 0));
        c.getReloj().ejecutarCiclos(8);
        r.verificar("B expulsa a A y empieza en el ciclo 4 (respuesta 1)", b.getCicloPrimeraEjecucion() == 4 && b.getTiempoRespuesta() == 1,
                b.getCicloPrimeraEjecucion() + ", " + b.getTiempoRespuesta());
        r.verificar("B termina en el 6 y A retoma y termina en el 11", b.getCicloFin() == 6 && a.getCicloFin() == 11,
                b.getCicloFin() + ", " + a.getCicloFin());
        c.apagar();
    }

    /** EDF: B tiene deadline más cercano y se ejecuta antes aunque llegó después. */
    private static void probarEDF() {
        r.grupo("EDF (primero el deadline más cercano)");
        Cluster c = cluster(PoliticaTipo.EDF, 3, 1024, 0, 0, 0);
        PCB a = crear(c, cpu("A-edf", 5, 3, 50, 0));
        PCB b = crear(c, cpu("B-edf", 5, 3, 10, 0));
        c.getReloj().ejecutarCiclos(8);
        r.verificar("B (deadline 10) termina en el 4 y A (deadline 50) en el 8", b.getCicloFin() == 4 && a.getCicloFin() == 8,
                b.getCicloFin() + ", " + a.getCicloFin());
        c.apagar();
    }

    /** Un proceso en CPU que llega a deadline 0 se termina, libera la CPU y la RAM. */
    private static void probarDeadline() {
        r.grupo("Deadline vencido en ejecución");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        PCB a = crear(c, cpu("A-dl", 5, 20, 5, 0));
        c.getReloj().ejecutarCiclos(6);
        Computador pc = c.getComputador(0);
        r.verificar("termina por deadline en el ciclo 5", a.getEstado() == EstadoProceso.TERMINADO && a.isDeadlineVencido()
                && a.getCicloFin() == 5, a.toString());
        r.verificar("libera la CPU y la RAM", pc.getCpu().getIdProceso() == CPU.SIN_PROCESO && pc.getRam().getUsada() == 0
                && pc.getRam().verificarIntegridad(), "RAM " + pc.getRam().getUsada());
        c.apagar();
    }

    /** Un proceso bloqueado en E/S que llega a deadline 0 se termina y su E/S se cancela en el DMA. */
    private static void probarDeadlineBloqueado() {
        r.grupo("Deadline vencido mientras espera E/S");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        PCB a = crear(c, io("A-dlio", 5, 10, 2, 10, 6, 0));
        c.getReloj().ejecutarCiclos(7);
        Computador pc = c.getComputador(0);
        r.verificar("termina por deadline en el ciclo 6 estando bloqueado", a.isDeadlineVencido() && a.getCicloFin() == 6, a.toString());
        r.verificar("el DMA cancela su E/S y la RAM queda libre", pc.getDma().cantidadPendientes() == 0 && pc.getRam().getUsada() == 0,
                pc.getDma().describirPendientes());
        c.apagar();
    }

    /** Envejecimiento: B (prioridad 6) espera; cada 5 ciclos mejora hasta superar a A (prioridad 3) y la expulsa. */
    private static void probarEnvejecimiento() {
        r.grupo("Envejecimiento contra la inanición");
        Cluster c = cluster(PoliticaTipo.PRIORIDADES, 3, 1024, 5, 0, 0);
        PCB a = crear(c, cpu("A-env", 3, 40, 1000, 0));
        PCB b = crear(c, cpu("B-env", 6, 3, 1000, 0));
        c.getReloj().ejecutarCiclos(20);
        r.verificar("tras 20 ciclos en Listos B pasa de prioridad 6 a 2", b.getPrioridadEfectiva() == 2 && b.getEstado() == EstadoProceso.LISTO,
                b.toString());
        c.getReloj().ejecutarCiclos(2);
        r.verificar("en el ciclo 21 B supera a A y entra a la CPU", b.getCicloPrimeraEjecucion() == 21, String.valueOf(b.getCicloPrimeraEjecucion()));
        r.verificar("en CPU conserva la prioridad 2, así A (3) no la expulsa enseguida", b.getPrioridadEfectiva() == 2
                && b.getEstado() == EstadoProceso.EJECUCION && a.getEstado() == EstadoProceso.LISTO, b + " | " + a);
        c.getReloj().ejecutarCiclos(3);
        r.verificar("B completa sus 3 instrucciones (ciclo 24) y A retoma en el 25", b.getCicloFin() == 24
                && a.getEstado() == EstadoProceso.EJECUCION, b.getCicloFin() + " | " + a);
        c.apagar();
    }

    /** La política se cambia en tiempo de ejecución y se aplica en el ciclo siguiente. */
    private static void probarCambioDePolitica() {
        r.grupo("Cambio de política en tiempo de ejecución");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        crear(c, cpu("A-cambio", 5, 10, 100, 0));
        c.getReloj().ejecutarCiclos(1);
        PCB b = crear(c, cpu("B-cambio", 1, 2, 100, 0));
        c.getReloj().ejecutarCiclos(2);
        r.verificar("con FCFS, B espera aunque sea más importante", b.getEstado() == EstadoProceso.LISTO, b.toString());
        c.cambiarPolitica(0, PoliticaTipo.PRIORIDADES);
        c.getReloj().ejecutarCiclos(1);
        r.verificar("tras cambiar a Prioridades, B entra en el ciclo 4", b.getCicloPrimeraEjecucion() == 4, String.valueOf(b.getCicloPrimeraEjecucion()));
        r.verificar("el computador 1 sigue con su propia política", c.getComputador(1).getKernel().getPolitica() == PoliticaTipo.FCFS, "");
        c.apagar();
    }

    /** E/S por DMA a través del kernel, incluida una E/S como última instrucción. */
    private static void probarEntradaSalida() {
        r.grupo("E/S con DMA atendida por el kernel");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        PCB a = crear(c, io("A-io", 5, 7, 3, 4, 100, 0));
        c.getReloj().ejecutarCiclos(17);
        r.verificar("2 E/S de 4 ciclos y termina en el ciclo 16", a.getCicloFin() == 16 && c.getComputador(0).getDma().getCompletadas() == 2,
                a.getCicloFin() + ", " + c.getComputador(0).getDma().getCompletadas());
        c.apagar();

        Cluster c2 = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        PCB b = crear(c2, io("B-io-fin", 5, 3, 3, 4, 100, 0));
        c2.getReloj().ejecutarCiclos(9);
        r.verificar("si la última instrucción es E/S, termina al volver del DMA (ciclo 8)", b.getCicloFin() == 8 && !b.isDeadlineVencido(),
                b.toString());
        c2.apagar();
    }

    /** Rechazo de procesos más grandes que la RAM y balanceo de carga en la asignación automática. */
    private static void probarAdmisionYAsignacion() {
        r.grupo("Admisión por RAM y asignación automática");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 100, 0, 0, 0);
        Debug.info(ORIGEN, "la línea ERROR siguiente es provocada a propósito");
        r.verificar("rechaza un proceso más grande que la RAM total de su computador",
                c.crearProceso(cpu("Grande", 5, 5, 100, 0, 150).datos()) == null, "");
        PCB auto = crear(c, cpu("Grande-auto", 5, 5, 100, AUTO, 150));
        r.verificar("en automático va al computador donde sí cabe (1)", auto != null && auto.getComputador() == 1,
                auto == null ? "null" : String.valueOf(auto.getComputador()));
        c.apagar();

        Cluster c2 = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        PCB p1 = crear(c2, cpu("auto-1", 5, 5, 100, AUTO));
        PCB p2 = crear(c2, cpu("auto-2", 5, 5, 100, AUTO));
        PCB p3 = crear(c2, cpu("auto-3", 5, 5, 100, AUTO));
        r.verificar("balanceo: 0, 1, 0 (menor carga; en empate más RAM libre y menor número)",
                p1.getComputador() == 0 && p2.getComputador() == 1 && p3.getComputador() == 0,
                p1.getComputador() + ", " + p2.getComputador() + ", " + p3.getComputador());
        Cluster c3 = cluster(PoliticaTipo.FCFS, 3, 150, 0, 0, 0);
        PCB x = crear(c3, cpu("X-ram", 5, 3, 100, 0, 100));
        PCB y = crear(c3, cpu("Y-ram", 1, 3, 100, 0, 100));
        PCB z = crear(c3, cpu("Z-ram", 3, 3, 100, 0, 100));
        c3.getReloj().ejecutarCiclos(1);
        r.verificar("con RAM para uno solo, entra primero el más prioritario (Y)", y.getEstado() == EstadoProceso.EJECUCION
                && x.getEstado() == EstadoProceso.NUEVO && z.getEstado() == EstadoProceso.NUEVO, y + " | " + x + " | " + z);
        c3.getReloj().ejecutarCiclos(4);
        r.verificar("al terminar Y se libera RAM y entra Z (prioridad 3) antes que X (5)", z.getEstado() != EstadoProceso.NUEVO
                && x.getEstado() == EstadoProceso.NUEVO, z + " | " + x);
        c2.apagar();
        c3.apagar();
    }

    /** Con el reloj en su hilo, la simulación se detiene sola al llegar al máximo de ciclos. */
    private static void probarMaxCiclosConHilo() {
        r.grupo("Parada automática por máximo de ciclos (reloj en su hilo)");
        Cluster c = cluster(PoliticaTipo.ROUND_ROBIN, 2, 1024, 0, 15, 1);
        crear(c, cpu("largo", 5, 100, 1000, 0));
        boolean dl = Debug.isActivo();
        Debug.setActivo(false);
        c.iniciar();
        boolean termino = c.esperarFin(5000);
        Debug.setActivo(dl);
        r.verificar("se detiene exactamente en el ciclo 15", termino && c.getReloj().getCiclo() == 15 && !c.getReloj().isEjecutando(),
                "ciclo " + c.getReloj().getCiclo());
        c.apagar();
    }

    /** Los comandos de la terminal cambian el sistema y los inválidos se rechazan sin detenerlo. */
    private static void probarConsola() {
        r.grupo("Comandos de la terminal (equivalen a los botones de la GUI)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, 0, 0, 0);
        ConsolaTerminal consola = new ConsolaTerminal(c);
        r.verificar("'politica 0 EDF' cambia la política", consola.ejecutarComando("politica 0 EDF")
                && c.getComputador(0).getKernel().getPolitica() == PoliticaTipo.EDF, "");
        r.verificar("'quantum 1 5' cambia el quantum", consola.ejecutarComando("quantum 1 5")
                && c.getComputador(1).getKernel().getQuantum() == 5, "");
        r.verificar("'velocidad 200' cambia la duración del ciclo", consola.ejecutarComando("velocidad 200")
                && c.getReloj().getDuracionCicloMs() == 200, "");
        Debug.info(ORIGEN, "las 4 líneas ERROR siguientes son provocadas a propósito");
        r.verificar("rechaza quantum 0", !consola.ejecutarComando("quantum 0 0"), "");
        r.verificar("rechaza texto donde va un número", !consola.ejecutarComando("velocidad rapido"), "");
        r.verificar("rechaza una política inexistente", !consola.ejecutarComando("politica 0 SJF"), "");
        r.verificar("rechaza un comando desconocido", !consola.ejecutarComando("volar"), "");
        c.apagar();
    }

    /** El clúster se arma desde avilaosconfigs.json y crea su carga inicial (sin productores/consumidores aún). */
    private static void probarConfiguracionReal() {
        r.grupo("Clúster armado desde avilaosconfigs.json");
        boolean dl = Debug.isActivo();
        ConfiguracionSistema config = ArchivoConfiguracion.cargar(ArchivoConfiguracion.RUTA_POR_DEFECTO);
        Cluster c = new Cluster(config, log);
        int esperados = 0;
        Nodo<DatosProceso> nodo = config.getProcesos().getCabeza();
        while (nodo != null) {
            esperados += nodo.getDato().tipo().usaBuffer() ? 0 : 1;
            nodo = nodo.getSiguiente();
        }
        int creados = c.cargarProcesosIniciales();
        r.verificar("crea un computador por cada uno configurado", c.cantidadComputadores() == config.getComputadores().tamano(), "");
        r.verificar("crea los " + esperados + " procesos iniciales que no usan buffer", creados == esperados, creados + " creados");
        Debug.setActivo(false);
        c.getReloj().ejecutarCiclos(10);
        Debug.setActivo(dl);
        r.verificar("corre 10 ciclos sin errores de integridad en la RAM", ramIntegra(c), "");
        ListaEnlazada<PCB> todos = c.todosLosProcesos();
        Nodo<PCB> p = todos.getCabeza();
        while (p != null) {
            r.registrar(p.getDato());
            p = p.getSiguiente();
        }
        r.seccion(" Clúster de avilaosconfigs.json tras 10 ciclos:");
        for (int i = 0; i < c.cantidadComputadores(); i++) {
            r.seccion("   " + c.getComputador(i).getNombre() + " (" + c.getComputador(i).getKernel().describirPlanificador() + ")");
            r.seccion(" " + ReporteTerminal.lineaCPU(c.getComputador(i).getCpu()));
            r.seccion(" " + ReporteTerminal.lineaRAM(c.getComputador(i).getRam()));
        }
        c.apagar();
    }

    private static boolean ramIntegra(Cluster c) {
        boolean bien = true;
        for (int i = 0; i < c.cantidadComputadores(); i++) {
            bien &= c.getComputador(i).getRam().verificarIntegridad();
        }
        return bien;
    }

    /** Clúster de 2 computadores: el 0 con los parámetros indicados, el 1 FCFS con 1024 de RAM. */
    private static Cluster cluster(PoliticaTipo politica, int quantum, int ram0, int envejecimiento, int maxCiclos, int duracionMs) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(ram0, politica, quantum));
        computadores.agregarFinal(new ConfigComputador(1024, PoliticaTipo.FCFS, 3));
        ConfiguracionSistema config = new ConfiguracionSistema(true, duracionMs, maxCiclos, 2, envejecimiento,
                computadores, new ListaEnlazada<DatosBuffer>(), new ListaEnlazada<DatosProceso>(), 0);
        return new Cluster(config, log);
    }

    private record Pedido(DatosProceso datos) {
    }

    private static Pedido cpu(String nombre, int prioridad, int instrucciones, int deadline, int computador) {
        return cpu(nombre, prioridad, instrucciones, deadline, computador, 10);
    }

    private static Pedido cpu(String nombre, int prioridad, int instrucciones, int deadline, int computador, int memoria) {
        return new Pedido(DatosProceso.cpuBound(nombre, prioridad, memoria, deadline, instrucciones, computador));
    }

    private static Pedido io(String nombre, int prioridad, int instrucciones, int cada, int duracion, int deadline, int computador) {
        return new Pedido(DatosProceso.ioBound(nombre, prioridad, 10, deadline, instrucciones, cada, duracion, computador));
    }

    private static PCB crear(Cluster c, Pedido pedido) {
        return r.registrar(c.crearProceso(pedido.datos()));
    }
}
