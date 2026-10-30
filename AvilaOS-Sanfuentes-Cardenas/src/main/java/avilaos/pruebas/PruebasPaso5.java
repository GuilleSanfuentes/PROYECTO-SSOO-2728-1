package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.RolSemaforo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.so.programas.Instruccion;
import avilaos.so.programas.ProgramaBuffer;
import avilaos.so.programas.ProgramaConsumidor;
import avilaos.so.programas.ProgramaProductor;
import avilaos.sync.Buffer;
import avilaos.sync.DatosBuffer;
import avilaos.sync.EstadoBuffer;
import avilaos.sync.SemaforoSO;
import avilaos.vista.ReporteTerminal;

/** Pruebas de terminal de la etapa 5: semáforos de ÁvilaOS, buffers acotados y productor-consumidor local. */
public final class PruebasPaso5 {

    private static final String ORIGEN = "Pruebas";
    private static final int DEADLINE_LARGO = 100000;
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso5() {
    }

    /** Ejecuta todas las pruebas de la etapa 5 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(5, "Etapa 5: semáforos, buffers y productor-consumidor local");
        probarSemaforo();
        probarBuffer();
        probarCreacionDeBuffers();
        probarReglasDeAdmision();
        probarConsumidorSeBloquea();
        probarProductorSeBloquea();
        probarDespertarPorInterrupcion();
        probarProductorConsumidorCompleto();
        probarVariosProductoresYConsumidores();
        probarDeadlineEnSemaforo();
        probarProductorMuereConFichas();
        probarConsumidorMuereConFichas();
        probarMuerteConEntregaDirecta();
        probarOtrosNoQuedanAtrapados();
        probarTablaDeFichas();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** Semáforo de conteo: wait, signal y cola FIFO; semáforo binario: nunca pasa de 1. */
    private static void probarSemaforo() {
        r.grupo("Semáforo de ÁvilaOS (conteo, FIFO y binario)");
        SemaforoSO conteo = new SemaforoSO("T", RolSemaforo.LLENO, 1);
        PCB a = proceso("sem-A");
        PCB b = proceso("sem-B");
        PCB c = proceso("sem-C");
        r.verificar("con valor 1, semWait pasa y lo deja en 0", conteo.esperar(a) && conteo.getValor() == 0, "valor " + conteo.getValor());
        r.verificar("con valor 0, semWait bloquea (B y C quedan en la cola)", !conteo.esperar(b) && !conteo.esperar(c)
                && conteo.cantidadBloqueados() == 2, conteo.cantidadBloqueados() + " bloqueados");
        r.verificar("semSignal despierta primero a B y luego a C (FIFO) sin subir el valor", conteo.senalar() == b
                && conteo.senalar() == c && conteo.getValor() == 0, "valor " + conteo.getValor());
        r.verificar("sin bloqueados, semSignal sube el valor a 1", conteo.senalar() == null && conteo.getValor() == 1,
                "valor " + conteo.getValor());
        SemaforoSO bloqueo = new SemaforoSO("T", RolSemaforo.VACIO, 0);
        bloqueo.esperar(a);
        r.verificar("quitar saca a un proceso de la cola (deadline)", bloqueo.quitar(a) && bloqueo.cantidadBloqueados() == 0, "");
        SemaforoSO binario = new SemaforoSO("T", RolSemaforo.MUTEX, 1);
        Debug.info(ORIGEN, "la línea ERROR siguiente es provocada a propósito");
        r.verificar("semSignal sobre un binario que vale 1 se rechaza y no lo sube", binario.senalar() == null
                && binario.getValor() == 1, "valor " + binario.getValor());
    }

    /** Buffer: elementos en orden FIFO, rechazo de insertar lleno y extraer vacío, invariante con los semáforos. */
    private static void probarBuffer() {
        r.grupo("Buffer acotado (capacidad 2) con sus tres semáforos");
        Buffer buffer = new Buffer("BT", 2, 0);
        PCB p = proceso("buf-P");
        r.verificar("recién creado: vacío=2, lleno=0, mutex=1", valores(buffer.estado(), 2, 0, 1), describir(buffer.estado()));
        agregar(buffer, p);
        agregar(buffer, p);
        EstadoBuffer lleno = buffer.estado();
        r.verificar("tras 2 inserciones: 2 elementos, vacío=0, lleno=2", lleno.elementos() == 2 && valores(lleno, 0, 2, 1),
                describir(lleno));
        r.verificar("con vacío=0 un tercer semWait(vacío) bloquea", !buffer.esperar(RolSemaforo.VACIO, p)
                && buffer.estado().bloqueadosVacio().tamano() == 1, "");
        Debug.info(ORIGEN, "la línea ERROR siguiente es provocada a propósito");
        r.verificar("insertar con el buffer lleno se rechaza", buffer.insertar() == -1, "");
        r.verificar("la invariante se cumple", buffer.verificarInvariante(), describir(buffer.estado()));
        buffer.esperar(RolSemaforo.LLENO, p);
        r.verificar("extraer entrega los elementos en orden: 1 y luego 2", buffer.extraer() == 1, "");
        PCB despertado = buffer.senalar(RolSemaforo.VACIO);
        r.verificar("semSignal(vacío) tras extraer despierta al productor bloqueado", despertado == p, String.valueOf(despertado));
        buffer.esperar(RolSemaforo.LLENO, p);
        buffer.extraer();
        Debug.info(ORIGEN, "la línea ERROR siguiente es provocada a propósito");
        r.verificar("extraer con el buffer vacío se rechaza", buffer.extraer() == -1, "");
    }

    /** El clúster crea los buffers reservando RAM y rechaza los inválidos sin detener nada. */
    private static void probarCreacionDeBuffers() {
        r.grupo("Creación de buffers en la RAM del anfitrión");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 100, buffer("B1", 30, 0));
        r.verificar("crea B1 (capacidad 30) y reserva 30 en la RAM del computador 0", c.getBuffers().cantidad() == 1
                && c.getComputador(0).getRam().getUsada() == 30 && c.getComputador(1).getRam().getUsada() == 0,
                "RAM 0 usada " + c.getComputador(0).getRam().getUsada());
        Debug.info(ORIGEN, "las 4 líneas ERROR siguientes son provocadas a propósito");
        r.verificar("rechaza un buffer que no cabe en la RAM libre", c.crearBuffer(new DatosBuffer("B2", 80, 0)) == null, "");
        r.verificar("rechaza un nombre repetido", c.crearBuffer(new DatosBuffer("B1", 5, 1)) == null, "");
        r.verificar("rechaza un anfitrión inexistente", c.crearBuffer(new DatosBuffer("B3", 5, 9)) == null, "");
        r.verificar("rechaza capacidad 0", c.crearBuffer(new DatosBuffer("B4", 0, 0)) == null, "");
        r.verificar("tras los rechazos solo existe B1 y la RAM sigue íntegra", c.getBuffers().cantidad() == 1
                && c.getComputador(0).getRam().verificarIntegridad(), "");
        c.apagar();
    }

    /** Se rechaza un buffer inexistente; el automático ya no va al anfitrión sino al computador menos cargado. */
    private static void probarReglasDeAdmision() {
        r.grupo("Admisión de productores y consumidores");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 5, 1));
        Debug.info(ORIGEN, "la línea ERROR siguiente es provocada a propósito");
        r.verificar("rechaza un consumidor de un buffer que no existe",
                c.crearProceso(DatosProceso.consumidor("fantasma", 3, 10, DEADLINE_LARGO, "NOEXISTE", 1, 3, 0)) == null, "");
        PCB remoto = r.registrar(c.crearProceso(DatosProceso.productor("remoto", 3, 10, DEADLINE_LARGO, "B1", 1, 3, 0)));
        r.verificar("acepta un productor de otro computador (acceso remoto, etapa 6)", remoto != null && remoto.getComputador() == 0,
                String.valueOf(remoto));
        PCB auto = r.registrar(c.crearProceso(DatosProceso.productor("auto", 3, 10, DEADLINE_LARGO, "B1", 1, 3,
                DatosProceso.ASIGNACION_AUTOMATICA)));
        r.verificar("en automático va al computador menos cargado (1, porque el 0 ya tiene un proceso)",
                auto != null && auto.getComputador() == 1, auto == null ? "null" : String.valueOf(auto.getComputador()));
        c.apagar();
    }

    /** Buffer vacío: el consumidor se bloquea en el semáforo 'lleno' y libera la CPU. */
    private static void probarConsumidorSeBloquea() {
        r.grupo("Consumidor con el buffer vacío");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("cons", 3, 10, DEADLINE_LARGO, "B1", 1, 3, 0)));
        c.getReloj().ejecutarCiclos(5);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("se bloquea con motivo Semáforo en 'lleno' de B1", consumidor.getEstado() == EstadoProceso.BLOQUEADO
                && consumidor.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO && consumidor.getDetalleBloqueo().contains("lleno"),
                consumidor.toString());
        r.verificar("queda en la cola del semáforo 'lleno' y la CPU se libera", e.bloqueadosLleno().tamano() == 1
                && c.getComputador(0).getCpu().isOciosa(), describir(e));
        r.seccion(" Consumidor bloqueado:\n" + ReporteTerminal.lineasBuffers(c).stripTrailing());
        c.apagar();
    }

    /** Buffer lleno: el productor se bloquea en el semáforo 'vacío' con el buffer en su capacidad. */
    private static void probarProductorSeBloquea() {
        r.grupo("Productor con el buffer lleno");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("prod", 3, 10, DEADLINE_LARGO, "B1", 1, 5, 0)));
        c.getReloj().ejecutarCiclos(20);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("produce 2 elementos y se bloquea en 'vacío'", productor.getEstado() == EstadoProceso.BLOQUEADO
                && productor.getDetalleBloqueo().contains("vacío") && e.producidos() == 2 && e.elementos() == 2,
                productor + " | " + describir(e));
        r.verificar("la invariante se cumple y vacío=0", c.getBuffers().buscar("B1").verificarInvariante() && e.valorVacio() == 0,
                describir(e));
        c.apagar();
    }

    /** El despertar llega por interrupción: el proceso sigue Bloqueado ese ciclo y pasa a Listos en el siguiente. */
    private static void probarDespertarPorInterrupcion() {
        r.grupo("Despertar por la interrupción SEMAFORO_SIGNAL (también en local)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("cons-irq", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
        c.getReloj().ejecutarCiclos(5);
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("prod-irq", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
        c.getReloj().ejecutarCiclos(7);
        Buffer b1 = c.getBuffers().buscar("B1");
        r.verificar("en el ciclo 12 el productor termina con su semSignal(lleno)", productor.getCicloFin() == 12,
                String.valueOf(productor.getCicloFin()));
        r.verificar("ese mismo ciclo el consumidor sigue Bloqueado y la interrupción espera en la línea",
                consumidor.getEstado() == EstadoProceso.BLOQUEADO && c.getComputador(0).getLinea().cantidadPendientes() == 1
                && b1.estado().bloqueadosLleno().tamano() == 0, consumidor + " | pendientes "
                + c.getComputador(0).getLinea().cantidadPendientes());
        c.getReloj().ejecutarCiclos(1);
        r.verificar("en el ciclo 13 el kernel atiende la interrupción y despacha al consumidor",
                consumidor.getEstado() == EstadoProceso.EJECUCION && c.getComputador(0).getLinea().cantidadPendientes() == 0,
                consumidor.toString());
        c.getReloj().ejecutarCiclos(6);
        r.verificar("no repite el semWait: sigue desde donde quedó y termina en el ciclo 18", consumidor.getCicloFin() == 18
                && !consumidor.isDeadlineVencido(), String.valueOf(consumidor.getCicloFin()));
        EstadoBuffer e = b1.estado();
        r.verificar("quedó producido 1, consumido 1 y los semáforos en reposo (2, 0, 1)", e.producidos() == 1
                && e.consumidos() == 1 && valores(e, 2, 0, 1), describir(e));
        c.apagar();
    }

    /** Un productor y un consumidor con buffer de 3: producen y consumen todo sin interbloqueo. */
    private static void probarProductorConsumidorCompleto() {
        r.grupo("Productor-consumidor local completo (buffer de 3, 6 elementos)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 3, 0));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor", 3, 10, DEADLINE_LARGO, "B1", 2, 6, 0)));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Monitor", 3, 10, DEADLINE_LARGO, "B1", 1, 6, 0)));
        correrEnSilencio(c, 200);
        Buffer b1 = c.getBuffers().buscar("B1");
        EstadoBuffer e = b1.estado();
        r.verificar("ambos terminan sin vencer el deadline", terminoBien(productor) && terminoBien(consumidor),
                productor + " | " + consumidor);
        r.verificar("producidos = consumidos = 6 y el buffer queda vacío", e.producidos() == 6 && e.consumidos() == 6
                && e.elementos() == 0, describir(e));
        r.verificar("con el mutex libre: lleno = elementos y vacío + lleno = capacidad", valores(e, 3, 0, 1) && b1.verificarInvariante(),
                describir(e));
        r.verificar("cada proceso contó sus 6 elementos", productor.getElementosProcesados() == 6
                && consumidor.getElementosProcesados() == 6, productor.getElementosProcesados() + ", "
                + consumidor.getElementosProcesados());
        r.verificar("la RAM solo conserva el buffer (3) e integra", c.getComputador(0).getRam().getUsada() == 3
                && c.getComputador(0).getRam().verificarIntegridad(), "usada " + c.getComputador(0).getRam().getUsada());
        r.seccion(" Productor-consumidor completo, buffer final:\n" + ReporteTerminal.lineasBuffers(c).stripTrailing());
        c.apagar();
    }

    /** Dos productores y dos consumidores con Round Robin: hay expulsiones con el mutex tomado y aun así termina todo. */
    private static void probarVariosProductoresYConsumidores() {
        r.grupo("Varios productores y consumidores (Round Robin q=2, buffer de 3)");
        Cluster c = cluster(PoliticaTipo.ROUND_ROBIN, 2, 1024, buffer("B1", 3, 0));
        PCB p1 = r.registrar(c.crearProceso(DatosProceso.productor("P-uno", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PCB p2 = r.registrar(c.crearProceso(DatosProceso.productor("P-dos", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PCB c1 = r.registrar(c.crearProceso(DatosProceso.consumidor("C-uno", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PCB c2 = r.registrar(c.crearProceso(DatosProceso.consumidor("C-dos", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        correrEnSilencio(c, 500);
        Buffer b1 = c.getBuffers().buscar("B1");
        EstadoBuffer e = b1.estado();
        r.verificar("los 4 procesos terminan (sin interbloqueo)", terminoBien(p1) && terminoBien(p2) && terminoBien(c1)
                && terminoBien(c2), p1 + " | " + p2 + " | " + c1 + " | " + c2);
        r.verificar("producidos = consumidos = 8 y semáforos en reposo", e.producidos() == 8 && e.consumidos() == 8
                && valores(e, 3, 0, 1), describir(e));
        r.verificar("ninguna cola de semáforo quedó con procesos", e.bloqueadosVacio().estaVacia() && e.bloqueadosLleno().estaVacia()
                && e.bloqueadosMutex().estaVacia(), describir(e));
        c.apagar();
    }

    /** Un proceso bloqueado en un semáforo que llega a deadline 0 se termina y sale de la cola del semáforo. */
    private static void probarDeadlineEnSemaforo() {
        r.grupo("Deadline vencido mientras espera en un semáforo");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("cons-dl", 3, 10, 6, "B1", 1, 3, 0)));
        c.getReloj().ejecutarCiclos(8);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("termina por deadline en el ciclo 6 estando bloqueado", consumidor.isDeadlineVencido()
                && consumidor.getCicloFin() == 6, consumidor.toString());
        r.verificar("sale de la cola del semáforo y libera su RAM (queda solo el buffer)", e.bloqueadosLleno().estaVacia()
                && c.getComputador(0).getRam().getUsada() == 2, describir(e) + " | RAM " + c.getComputador(0).getRam().getUsada());
        r.verificar("no deja fichas falsas: los semáforos siguen en reposo (2, 0, 1)", valores(e, 2, 0, 1), describir(e));
        c.apagar();
    }

    /** Un productor que muere por deadline en cada punto de su elemento no deja ninguna ficha retenida. */
    private static void probarProductorMuereConFichas() {
        r.grupo("Productor que muere por deadline retiene fichas (semáforos deben quedar en reposo)");
        int[] deadlines = {3, 4, 5, 6};
        String[] puntos = {"tras semWait(vacío)", "tras semWait(mutex)", "tras insertar", "tras semSignal(mutex)"};
        int[] insertados = {0, 0, 1, 1};
        for (int i = 0; i < deadlines.length; i++) {
            Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
            PCB p = r.registrar(c.crearProceso(DatosProceso.productor("prod-dl" + deadlines[i], 3, 10, deadlines[i], "B1", 1, 1, 0)));
            correrEnSilencio(c, 10);
            EstadoBuffer e = c.getBuffers().buscar("B1").estado();
            r.verificar("muere " + puntos[i] + " (ciclo " + deadlines[i] + "): " + insertados[i] + " elemento(s) y semáforos en reposo",
                    p.isDeadlineVencido() && p.getCicloFin() == deadlines[i] && e.elementos() == insertados[i]
                    && enReposo(e, 2), p.getCicloFin() + " | " + describir(e));
            c.apagar();
        }
    }

    /** Un consumidor que muere por deadline en cada punto de su elemento no deja ninguna ficha retenida. */
    private static void probarConsumidorMuereConFichas() {
        r.grupo("Consumidor que muere por deadline retiene fichas (semáforos deben quedar en reposo)");
        int[] deadlines = {9, 10, 11, 12};
        String[] puntos = {"tras semWait(lleno)", "tras semWait(mutex)", "tras extraer", "tras semSignal(mutex)"};
        int[] quedan = {1, 1, 0, 0};
        for (int i = 0; i < deadlines.length; i++) {
            Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
            r.registrar(c.crearProceso(DatosProceso.productor("prod-previo", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
            PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("cons-dl" + deadlines[i], 3, 10, deadlines[i],
                    "B1", 1, 1, 0)));
            correrEnSilencio(c, 16);
            EstadoBuffer e = c.getBuffers().buscar("B1").estado();
            r.verificar("muere " + puntos[i] + " (ciclo " + deadlines[i] + "): quedan " + quedan[i] + " elemento(s) y semáforos en reposo",
                    consumidor.isDeadlineVencido() && consumidor.getCicloFin() == deadlines[i] && e.elementos() == quedan[i]
                    && enReposo(e, 2), consumidor.getCicloFin() + " | " + describir(e));
            c.apagar();
        }
    }

    /** El consumidor recibe la ficha por entrega directa y muere antes de despertar: la ficha no se pierde. */
    private static void probarMuerteConEntregaDirecta() {
        r.grupo("Muere con la entrega directa de un semSignal pendiente");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("cons-entrega", 3, 10, 12, "B1", 1, 1, 0)));
        c.getReloj().ejecutarCiclos(5);
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("prod-entrega", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
        correrEnSilencio(c, 9);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("el productor termina en el 12 y el consumidor muere por deadline en ese mismo ciclo, ya sin esperar en la cola",
                productor.getCicloFin() == 12 && consumidor.isDeadlineVencido() && consumidor.getCicloFin() == 12
                && e.bloqueadosLleno().estaVacia(), productor.getCicloFin() + ", " + consumidor);
        r.verificar("el elemento queda visible para otros consumidores (lleno = 1, 1 elemento)", enReposo(e, 2) && e.elementos() == 1
                && e.valorLleno() == 1, describir(e));
        c.apagar();
    }

    /** Un productor muere con el mutex tomado y los demás procesos del buffer igual terminan. */
    private static void probarOtrosNoQuedanAtrapados() {
        r.grupo("Los demás procesos no quedan bloqueados por el que murió con el mutex");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1024, buffer("B1", 2, 0));
        PCB a = r.registrar(c.crearProceso(DatosProceso.productor("A-muere", 3, 10, 4, "B1", 1, 1, 0)));
        PCB b = r.registrar(c.crearProceso(DatosProceso.productor("B-sigue", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("C-sigue", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
        correrEnSilencio(c, 40);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("A muere por deadline en el ciclo 4 con el mutex tomado", a.isDeadlineVencido() && a.getCicloFin() == 4, a.toString());
        r.verificar("B y C terminan bien", terminoBien(b) && terminoBien(consumidor), b + " | " + consumidor);
        r.verificar("producido 1, consumido 1 y semáforos en reposo", e.producidos() == 1 && e.consumidos() == 1 && enReposo(e, 2),
                describir(e));
        c.apagar();
    }

    /** Para cada posición de un elemento ejecuta a mano las primeras instrucciones, libera las fichas y comprueba el reposo. */
    private static void probarTablaDeFichas() {
        r.grupo("Tabla de fichas retenidas: en toda posición el buffer vuelve al reposo");
        int capacidad = 2;
        ProgramaProductor productor = new ProgramaProductor("BT", 2, 1);
        ProgramaConsumidor consumidor = new ProgramaConsumidor("BT", 2, 1);
        StringBuilder falloProductor = new StringBuilder();
        StringBuilder falloConsumidor = new StringBuilder();
        for (int ejecutadas = 0; ejecutadas < productor.largoPorElemento(); ejecutadas++) {
            Buffer buffer = new Buffer("BT", capacidad, 0);
            PCB pcb = proceso("tabla-P");
            ejecutarAMano(buffer, productor, ejecutadas, pcb);
            soltar(buffer, productor.fichasRetenidas(ejecutadas));
            if (!enReposo(buffer.estado(), capacidad) || !buffer.verificarInvariante()) {
                falloProductor.append(' ').append(ejecutadas).append(" (").append(describir(buffer.estado())).append(')');
            }
            Buffer conElemento = new Buffer("BT", capacidad, 0);
            agregar(conElemento, pcb);
            ejecutarAMano(conElemento, consumidor, ejecutadas, pcb);
            soltar(conElemento, consumidor.fichasRetenidas(ejecutadas));
            if (!enReposo(conElemento.estado(), capacidad) || !conElemento.verificarInvariante()) {
                falloConsumidor.append(' ').append(ejecutadas).append(" (").append(describir(conElemento.estado())).append(')');
            }
        }
        r.verificar("productor: las 7 posiciones de su elemento (K=2) vuelven al reposo", falloProductor.length() == 0,
                "fallan:" + falloProductor);
        r.verificar("consumidor: las 7 posiciones de su elemento (K=2) vuelven al reposo", falloConsumidor.length() == 0,
                "fallan:" + falloConsumidor);
        r.verificar("completo (sin retener nada) no libera fichas", productor.fichasRetenidas(productor.largoPorElemento()).length == 0
                && consumidor.fichasRetenidas(consumidor.largoPorElemento()).length == 0, "");
    }

    /** Aplica sobre el buffer las primeras instrucciones del programa, sin pasar por kernel ni CPU. */
    private static void ejecutarAMano(Buffer buffer, ProgramaBuffer programa, int cantidad, PCB pcb) {
        for (int i = 0; i < cantidad; i++) {
            Instruccion instruccion = programa.instruccionEn(i);
            switch (instruccion.tipo()) {
                case SEM_WAIT -> buffer.esperar(instruccion.semaforo(), pcb);
                case SEM_SIGNAL -> buffer.senalar(instruccion.semaforo());
                case INSERTAR -> buffer.insertar();
                case EXTRAER -> buffer.extraer();
                default -> {
                }
            }
        }
    }

    /** Señala en orden los semáforos indicados. */
    private static void soltar(Buffer buffer, RolSemaforo[] roles) {
        for (RolSemaforo rol : roles) {
            buffer.senalar(rol);
        }
    }

    /** Reposo exacto: mutex libre, lleno = elementos y vacío + elementos = capacidad; sin procesos esperando. */
    static boolean enReposo(EstadoBuffer e, int capacidad) {
        return valores(e, capacidad - e.elementos(), e.elementos(), 1) && e.bloqueadosVacio().estaVacia()
                && e.bloqueadosLleno().estaVacia() && e.bloqueadosMutex().estaVacia();
    }

    /** Un productor completo sobre el buffer: semWait(vacío), semWait(mutex), insertar, semSignal(mutex), semSignal(lleno). */
    static void agregar(Buffer buffer, PCB pcb) {
        buffer.esperar(RolSemaforo.VACIO, pcb);
        buffer.esperar(RolSemaforo.MUTEX, pcb);
        buffer.insertar();
        buffer.senalar(RolSemaforo.MUTEX);
        buffer.senalar(RolSemaforo.LLENO);
    }

    /** Corre los ciclos con el debugger apagado para no llenar la terminal. */
    static void correrEnSilencio(Cluster c, int ciclos) {
        boolean activo = Debug.isActivo();
        Debug.setActivo(false);
        c.getReloj().ejecutarCiclos(ciclos);
        Debug.setActivo(activo);
    }

    static boolean terminoBien(PCB pcb) {
        return pcb.getEstado() == EstadoProceso.TERMINADO && !pcb.isDeadlineVencido();
    }

    private static boolean valores(EstadoBuffer e, int vacio, int lleno, int mutex) {
        return e.valorVacio() == vacio && e.valorLleno() == lleno && e.valorMutex() == mutex;
    }

    static String describir(EstadoBuffer e) {
        return e.nombre() + " elementos=" + e.elementos() + "/" + e.capacidad() + " vacío=" + e.valorVacio()
                + " lleno=" + e.valorLleno() + " mutex=" + e.valorMutex() + " producidos=" + e.producidos()
                + " consumidos=" + e.consumidos();
    }

    static PCB proceso(String nombre) {
        return FabricaProcesos.crear(DatosProceso.cpuBound(nombre, 5, 10, 100, 5, 0), 0);
    }

    static DatosBuffer buffer(String nombre, int capacidad, int computador) {
        return new DatosBuffer(nombre, capacidad, computador);
    }

    /** Clúster de 2 computadores: el 0 con la política y la RAM indicadas, el 1 FCFS con 1024; sin envejecimiento. */
    private static Cluster cluster(PoliticaTipo politica, int quantum, int ram0, DatosBuffer... buffers) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(ram0, politica, quantum));
        computadores.agregarFinal(new ConfigComputador(1024, PoliticaTipo.FCFS, 3));
        ListaEnlazada<DatosBuffer> lista = new ListaEnlazada<>();
        for (DatosBuffer datos : buffers) {
            lista.agregarFinal(datos);
        }
        CondicionesParada parada = new CondicionesParada(0, false, 1, false, CondicionesParada.DEF_INACTIVIDAD_SEGUNDOS);
        ConfiguracionSistema config = new ConfiguracionSistema(true, 0, parada, 2, 0, computadores, lista,
                new ListaEnlazada<DatosProceso>(), 0);
        return new Cluster(config, log);
    }
}
