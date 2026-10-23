package avilaos.pruebas;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.RolSemaforo;
import avilaos.enums.TipoInstruccion;
import avilaos.estructuras.Cola;
import avilaos.log.Debug;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.GeneradorIds;
import avilaos.so.PCB;
import avilaos.so.politicas.FabricaPoliticas;
import avilaos.so.politicas.PoliticaPlanificacion;
import avilaos.so.programas.Instruccion;
import avilaos.so.programas.Programa;

/** Pruebas de terminal de la etapa 2: PCB, programas, IDs y políticas. */
public final class PruebasPaso2 {

    private static final String ORIGEN = "Pruebas";
    private static final int AUTO = DatosProceso.ASIGNACION_AUTOMATICA;
    private static Resultados r;

    private PruebasPaso2() {
    }

    /** Ejecuta todas las pruebas de la etapa 2 e imprime su resumen. */
    public static void ejecutar() {
        GeneradorIds.reiniciar();
        r = new Resultados(2, "Etapa 2: PCB, programas, IDs y políticas");
        probarCreacionYValidacion();
        probarProgramas();
        probarTransiciones();
        probarDeadlineYEnvejecimiento();
        probarPoliticas();
        probarIdsConcurrentes();
        r.cerrar();
    }

    /** Datos válidos crean el PCB; datos inválidos se rechazan sin detener el programa. */
    private static void probarCreacionYValidacion() {
        r.grupo("Creación y validación de procesos");
        PCB pcb = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("calc", 3, 100, 50, 20, 0), 0));
        r.verificar("crear CPU bound", pcb != null && pcb.getEstado() == EstadoProceso.NUEVO
                && pcb.getEtiqueta().equals("P1-H1") && pcb.getTiempoRestante() == 20, String.valueOf(pcb));
        Debug.info(ORIGEN, "las 2 líneas ERROR siguientes son provocadas a propósito");
        PCB invalido = FabricaProcesos.crear(DatosProceso.cpuBound("", 11, 0, -1, 0, -5), 0);
        r.verificar("rechaza datos inválidos", invalido == null, String.valueOf(invalido));
        PCB sinBuffer = FabricaProcesos.crear(DatosProceso.productor("prod", 2, 50, 100, " ", 0, 3, AUTO), 0);
        r.verificar("rechaza productor sin buffer", sinBuffer == null, String.valueOf(sinBuffer));
        String errores = FabricaProcesos.validar(DatosProceso.cpuBound("", 11, 0, -1, 0, -5));
        r.verificar("informa todos los errores", errores.split(";").length == 6, errores);
    }

    /** Cada tipo de programa genera la secuencia de instrucciones esperada. */
    private static void probarProgramas() {
        r.grupo("Programas por tipo (secuencia de instrucciones según el Contador)");
        PCB io = r.registrar(FabricaProcesos.crear(DatosProceso.ioBound("io", 5, 50, 100, 7, 3, 4, AUTO), 0));
        r.verificar("I/O bound pide E/S cada 3", secuencia(io.getPrograma()).equals("C C E C C E C"), secuencia(io.getPrograma()));

        PCB prod = r.registrar(FabricaProcesos.crear(DatosProceso.productor("prod", 2, 50, 100, "B1", 2, 2, AUTO), 0));
        r.verificar("productor: 2 de cálculo + Stallings, 2 veces",
                secuencia(prod.getPrograma()).equals("C C W(vacío) W(mutex) I S(mutex) S(lleno) C C W(vacío) W(mutex) I S(mutex) S(lleno)"),
                secuencia(prod.getPrograma()));

        PCB cons = r.registrar(FabricaProcesos.crear(DatosProceso.consumidor("cons", 2, 50, 100, "B1", 1, 1, AUTO), 0));
        r.verificar("consumidor: Stallings + 1 de cálculo",
                secuencia(cons.getPrograma()).equals("W(lleno) W(mutex) X S(mutex) S(vacío) C"),
                secuencia(cons.getPrograma()));

        Instruccion espera = prod.getPrograma().instruccionEn(2);
        r.verificar("semWait se ejecuta en modo SO", espera.tipo() == TipoInstruccion.SEM_WAIT
                && espera.semaforo() == RolSemaforo.VACIO && espera.modo().name().equals("SISTEMA_OPERATIVO"), String.valueOf(espera));
    }

    /** Transiciones válidas se aplican; las inválidas se rechazan; el Contador avanza y se detiene al final. */
    private static void probarTransiciones() {
        r.grupo("Transiciones de estado y avance del Contador");
        PCB pcb = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("trans", 4, 10, 100, 2, 0), 5));
        r.verificar("Nuevo -> Listo", pcb.cambiarEstado(EstadoProceso.LISTO), pcb.toString());
        r.verificar("Listo -> Ejecución (despachar)", pcb.despachar(8) && pcb.getTiempoRespuesta() == 3, pcb.toString());
        r.verificar("Ejecución -> Bloqueado (semáforo)", pcb.bloquear(MotivoBloqueo.SEMAFORO, "semáforo 'vacío' de B1", 10)
                && pcb.toString().contains("vacío"), pcb.toString());
        r.verificar("Bloqueado -> Listo acumula tiempo en semáforo", pcb.desbloquear(14) && pcb.getCiclosBloqueadoSemaforo() == 4,
                String.valueOf(pcb.getCiclosBloqueadoSemaforo()));
        Debug.info(ORIGEN, "las 2 líneas ERROR siguientes son provocadas a propósito");
        r.verificar("rechaza Listo -> Bloqueado", !pcb.bloquear(MotivoBloqueo.ENTRADA_SALIDA, "", 15)
                && pcb.getEstado() == EstadoProceso.LISTO, pcb.toString());
        pcb.despachar(16);
        r.verificar("avanzar Contador", pcb.avanzarContador() && pcb.avanzarContador() && pcb.programaTerminado() && pcb.getMar() == 2, pcb.toString());
        r.verificar("no avanza Contador tras el final", !pcb.avanzarContador() && pcb.getContador() == 2, pcb.toString());
        r.verificar("Ejecución -> Terminado", pcb.terminar(17, false) && pcb.getCicloFin() == 17 && !pcb.isDeadlineVencido(), pcb.toString());

        PCB bloqueado = r.registrar(FabricaProcesos.crear(DatosProceso.ioBound("espera", 6, 20, 60, 9, 3, 5, 1), 20));
        bloqueado.cambiarEstado(EstadoProceso.LISTO);
        bloqueado.despachar(22);
        bloqueado.avanzarContador();
        bloqueado.avanzarContador();
        bloqueado.avanzarContador();
        r.verificar("I/O bound se bloquea por E/S", bloqueado.bloquear(MotivoBloqueo.ENTRADA_SALIDA, "5 ciclos en DMA", 25),
                bloqueado.toString());
    }

    /** El deadline llega a 0 y el envejecimiento mejora la prioridad sin pasar de 1. */
    private static void probarDeadlineYEnvejecimiento() {
        r.grupo("Deadline y envejecimiento");
        PCB pcb = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("dl", 3, 10, 2, 50, 0), 0));
        r.verificar("deadline baja y llega a 0", !pcb.descontarDeadline() && pcb.descontarDeadline() && pcb.getDeadlineRestante() == 0,
                pcb.toString());
        pcb.cambiarEstado(EstadoProceso.LISTO);
        int mejoras = 0;
        for (int i = 0; i < 100; i++) {
            if (pcb.envejecer(10)) {
                mejoras++;
            }
        }
        r.verificar("envejecimiento: 3 -> 1 y se detiene", mejoras == 2 && pcb.getPrioridadEfectiva() == 1, pcb.toString());
        pcb.despachar(100);
        r.verificar("en CPU conserva la prioridad envejecida", pcb.getPrioridadEfectiva() == 1, pcb.toString());
        pcb.restaurarPrioridad();
        r.verificar("al salir de la CPU recupera su prioridad base", pcb.getPrioridadEfectiva() == 3, pcb.toString());
        r.verificar("termina por deadline vencido", pcb.terminar(100, true) && pcb.isDeadlineVencido(), pcb.toString());

        PCB envejecido = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("viejo", 8, 10, 300, 30, 0), 0));
        envejecido.cambiarEstado(EstadoProceso.LISTO);
        for (int i = 0; i < 30; i++) {
            envejecido.envejecer(10);
        }
        r.verificar("en Listos con prioridad envejecida 8 -> 5", envejecido.getPrioridadEfectiva() == 5, envejecido.toString());
    }

    /** Cada política ordena la cola de listos y decide la expulsión según su criterio. */
    private static void probarPoliticas() {
        r.grupo("Políticas de planificación (orden de la cola y expulsión)");
        PCB a = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("A", 5, 10, 40, 10, 0), 0));
        PCB b = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("B", 2, 10, 90, 10, 0), 0));
        PCB c = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("C", 5, 10, 10, 10, 0), 0));
        PCB d = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("D", 2, 10, 40, 10, 0), 0));
        r.verificar("FCFS mantiene llegada", ordenCon(PoliticaTipo.FCFS, a, b, c, d).equals("A B C D"), ordenCon(PoliticaTipo.FCFS, a, b, c, d));
        r.verificar("Round Robin mantiene llegada", ordenCon(PoliticaTipo.ROUND_ROBIN, a, b, c, d).equals("A B C D"),
                ordenCon(PoliticaTipo.ROUND_ROBIN, a, b, c, d));
        r.verificar("EDF por deadline, empate por llegada", ordenCon(PoliticaTipo.EDF, a, b, c, d).equals("C A D B"),
                ordenCon(PoliticaTipo.EDF, a, b, c, d));
        r.verificar("Prioridades, empate por llegada", ordenCon(PoliticaTipo.PRIORIDADES, a, b, c, d).equals("B D A C"),
                ordenCon(PoliticaTipo.PRIORIDADES, a, b, c, d));

        r.verificar("FCFS nunca expulsa", !FabricaPoliticas.crear(PoliticaTipo.FCFS).debeExpulsar(a, b, 100, 3), "");
        PoliticaPlanificacion rr = FabricaPoliticas.crear(PoliticaTipo.ROUND_ROBIN);
        r.verificar("RR expulsa al agotar quantum", !rr.debeExpulsar(a, b, 2, 3) && rr.debeExpulsar(a, b, 3, 3), "");
        r.verificar("RR no expulsa si no hay otro listo", !rr.debeExpulsar(a, null, 5, 3), "");
        r.verificar("EDF expulsa por deadline menor", FabricaPoliticas.crear(PoliticaTipo.EDF).debeExpulsar(a, c, 0, 0), "");
        PoliticaPlanificacion prio = FabricaPoliticas.crear(PoliticaTipo.PRIORIDADES);
        r.verificar("Prioridades expulsa solo si es estrictamente mejor", prio.debeExpulsar(a, b, 0, 0) && !prio.debeExpulsar(b, d, 0, 0), "");
    }

    /** Cuatro hilos piden IDs a la vez y no se repite ninguno. */
    private static void probarIdsConcurrentes() {
        r.grupo("IDs únicos con 4 hilos concurrentes (Semaphore de Java)");
        final int hilos = 4;
        final int porHilo = 250;
        final int[][] obtenidos = new int[hilos][porHilo];
        Thread[] trabajadores = new Thread[hilos];
        int base = GeneradorIds.siguienteId();
        for (int h = 0; h < hilos; h++) {
            final int fila = h;
            trabajadores[h] = new Thread(() -> {
                for (int i = 0; i < porHilo; i++) {
                    obtenidos[fila][i] = GeneradorIds.siguienteId();
                }
            });
            trabajadores[h].start();
        }
        for (Thread t : trabajadores) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        boolean[] visto = new boolean[hilos * porHilo];
        boolean repetido = false;
        for (int[] fila : obtenidos) {
            for (int id : fila) {
                int posicion = id - base - 1;
                if (posicion < 0 || posicion >= visto.length || visto[posicion]) {
                    repetido = true;
                } else {
                    visto[posicion] = true;
                }
            }
        }
        r.verificar("1000 IDs sin repetir", !repetido, "hay IDs repetidos o fuera de rango");
    }

    /** Ordena una copia de la cola con la política y devuelve los nombres en orden. */
    private static String ordenCon(PoliticaTipo tipo, PCB... procesos) {
        Cola<PCB> cola = new Cola<>();
        for (PCB p : procesos) {
            cola.encolar(p);
        }
        cola.ordenar(FabricaPoliticas.crear(tipo).getComparador());
        StringBuilder sb = new StringBuilder();
        while (!cola.estaVacia()) {
            sb.append(sb.length() > 0 ? " " : "").append(cola.desencolar().getNombre());
        }
        return sb.toString();
    }

    /** Abrevia el programa: C cálculo, E E/S, W semWait, S semSignal, I insertar, X extraer. */
    private static String secuencia(Programa programa) {
        StringBuilder sb = new StringBuilder();
        for (int contador = 0; contador < programa.totalInstrucciones(); contador++) {
            Instruccion ins = programa.instruccionEn(contador);
            String abreviada = switch (ins.tipo()) {
                case CALCULO -> "C";
                case SOLICITAR_ES -> "E";
                case SEM_WAIT -> "W(" + ins.semaforo().getNombre() + ")";
                case SEM_SIGNAL -> "S(" + ins.semaforo().getNombre() + ")";
                case INSERTAR -> "I";
                case EXTRAER -> "X";
            };
            sb.append(sb.length() > 0 ? " " : "").append(abreviada);
        }
        return sb.toString();
    }
}
