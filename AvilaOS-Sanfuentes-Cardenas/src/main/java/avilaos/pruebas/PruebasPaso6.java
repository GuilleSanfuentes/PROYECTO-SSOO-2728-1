package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.PCB;
import avilaos.sync.Buffer;
import avilaos.sync.DatosBuffer;
import avilaos.sync.EstadoBuffer;
import avilaos.vista.ReporteTerminal;

/** Pruebas de terminal de la etapa 6: acceso remoto a buffers con latencia de red y varios productores/consumidores. */
public final class PruebasPaso6 {

    private static final int DEADLINE_LARGO = 100000;
    private static final int LATENCIA = 3;
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso6() {
    }

    /** Lo observado mientras un proceso espera por la red: ciclos, elementos que veía el buffer y el detalle del bloqueo. */
    private record Espera(int ciclos, int elementos, String detalle) {
    }

    /** Ejecuta todas las pruebas de la etapa 6 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(6, "Etapa 6: acceso remoto a buffers y varios productores/consumidores");
        probarProductorRemoto();
        probarConsumidorRemoto();
        probarLocalNoUsaLaRed();
        probarLatenciaCeroEsLocal();
        probarProductorMuereEnLaRed();
        probarConsumidorMuereEnLaRed();
        probarDespertarEntreComputadores();
        probarProductoresYConsumidoresMezclados();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El productor remoto se bloquea 'latencia' ciclos por red y el elemento aparece solo al terminar la espera. */
    private static void probarProductorRemoto() {
        r.grupo("Productor remoto (PC1 -> buffer de PC0, latencia " + LATENCIA + ")");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, LATENCIA, PruebasPaso5.buffer("B1", 3, 0));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor-remoto", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 1)));
        Espera espera = observarEspera(c, productor, 40);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("espera exactamente " + LATENCIA + " ciclos bloqueado con motivo Red", espera.ciclos() == LATENCIA,
                espera.ciclos() + " ciclos");
        r.verificar("el detalle del bloqueo dice 'acceso remoto a B1'", espera.detalle().contains("acceso remoto a B1"), espera.detalle());
        r.verificar("mientras espera el buffer sigue sin el elemento (se aplica al terminar)", espera.elementos() == 0,
                "suma de elementos vistos " + espera.elementos());
        r.verificar("al terminar la espera el elemento está y el productor termina bien", PruebasPaso5.terminoBien(productor)
                && e.elementos() == 1 && e.producidos() == 1 && PruebasPaso5.enReposo(e, 3), PruebasPaso5.describir(e));
        r.verificar("la tarjeta de red del PC1 atendió 1 acceso y la del PC0 ninguno", c.getComputador(1).getRed().getCompletadas() == 1
                && c.getComputador(0).getRed().getCompletadas() == 0, "PC1 " + c.getComputador(1).getRed().getCompletadas());
        c.apagar();
    }

    /** El consumidor remoto espera por red y el elemento sale del buffer solo al terminar la espera. */
    private static void probarConsumidorRemoto() {
        r.grupo("Consumidor remoto (PC1 <- buffer de PC0, latencia " + LATENCIA + ")");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, LATENCIA, PruebasPaso5.buffer("B1", 3, 0));
        Buffer b1 = c.getBuffers().buscar("B1");
        PruebasPaso5.agregar(b1, PruebasPaso5.proceso("pre-llenado"));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Monitor-remoto", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 1)));
        Espera espera = observarEspera(c, consumidor, 40);
        EstadoBuffer e = b1.estado();
        r.verificar("espera exactamente " + LATENCIA + " ciclos bloqueado con motivo Red", espera.ciclos() == LATENCIA,
                espera.ciclos() + " ciclos");
        r.verificar("mientras espera el elemento sigue en el buffer (1 elemento por ciclo de espera)",
                espera.elementos() == LATENCIA, "suma de elementos vistos " + espera.elementos());
        r.verificar("al terminar la espera lo extrajo y el consumidor termina bien", PruebasPaso5.terminoBien(consumidor)
                && e.elementos() == 0 && e.consumidos() == 1 && PruebasPaso5.enReposo(e, 3), PruebasPaso5.describir(e));
        c.apagar();
    }

    /** Un proceso en el computador anfitrión no usa la red aunque la latencia sea mayor que 0. */
    private static void probarLocalNoUsaLaRed() {
        r.grupo("Acceso local: no usa la tarjeta de red");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, LATENCIA, PruebasPaso5.buffer("B1", 3, 0));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor-local", 3, 10, DEADLINE_LARGO, "B1", 1, 2, 0)));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Monitor-local", 3, 10, DEADLINE_LARGO, "B1", 1, 2, 0)));
        PruebasPaso5.correrEnSilencio(c, 200);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("ambos terminan y ninguna tarjeta de red atendió accesos", PruebasPaso5.terminoBien(productor)
                && PruebasPaso5.terminoBien(consumidor) && c.getComputador(0).getRed().getCompletadas() == 0
                && c.getComputador(1).getRed().getCompletadas() == 0, productor + " | " + consumidor);
        r.verificar("producidos = consumidos = 2 y semáforos en reposo", e.producidos() == 2 && e.consumidos() == 2
                && PruebasPaso5.enReposo(e, 3), PruebasPaso5.describir(e));
        c.apagar();
    }

    /** Con latencia 0 el acceso remoto se comporta como local: no se bloquea por red. */
    private static void probarLatenciaCeroEsLocal() {
        r.grupo("Latencia de red 0: el acceso remoto se comporta como local");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 0, PruebasPaso5.buffer("B1", 3, 0));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor-lat0", 3, 10, DEADLINE_LARGO, "B1", 1, 2, 1)));
        Espera espera = observarEspera(c, productor, 100);
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("nunca se bloquea por red y la tarjeta no atiende accesos", espera.ciclos() == 0
                && c.getComputador(1).getRed().getCompletadas() == 0, espera.ciclos() + " ciclos en red");
        r.verificar("termina bien y deja sus 2 elementos en el buffer", PruebasPaso5.terminoBien(productor) && e.elementos() == 2
                && PruebasPaso5.enReposo(e, 3), PruebasPaso5.describir(e));
        c.apagar();
    }

    /** Un productor remoto que vence su deadline durante el acceso no deja fichas retenidas. */
    private static void probarProductorMuereEnLaRed() {
        r.grupo("Productor remoto que muere por deadline (semáforos deben quedar en reposo)");
        int[] deadlines = {5, 6, 7, 8};
        String[] puntos = {"al iniciar el acceso remoto", "a mitad de la latencia", "en el último ciclo de la latencia",
            "justo al volver (con el elemento ya insertado)"};
        int[] insertados = {0, 0, 0, 1};
        for (int i = 0; i < deadlines.length; i++) {
            Cluster c = cluster(PoliticaTipo.FCFS, 3, LATENCIA, PruebasPaso5.buffer("B1", 3, 0));
            PCB p = r.registrar(c.crearProceso(DatosProceso.productor("prod-red" + deadlines[i], 3, 10, deadlines[i], "B1", 1, 1, 1)));
            PruebasPaso5.correrEnSilencio(c, 20);
            EstadoBuffer e = c.getBuffers().buscar("B1").estado();
            r.verificar("muere " + puntos[i] + " (ciclo " + deadlines[i] + "): " + insertados[i] + " elemento(s), red libre y semáforos en reposo",
                    p.isDeadlineVencido() && p.getCicloFin() == deadlines[i] && e.elementos() == insertados[i]
                    && PruebasPaso5.enReposo(e, 3) && c.getComputador(1).getRed().cantidadPendientes() == 0,
                    p.getCicloFin() + " | " + PruebasPaso5.describir(e));
            c.apagar();
        }
    }

    /** Un consumidor remoto que vence su deadline durante el acceso no deja fichas retenidas. */
    private static void probarConsumidorMuereEnLaRed() {
        r.grupo("Consumidor remoto que muere por deadline (semáforos deben quedar en reposo)");
        int[] deadlines = {4, 5, 6, 7};
        String[] puntos = {"al iniciar el acceso remoto", "a mitad de la latencia", "en el último ciclo de la latencia",
            "justo al volver (con el elemento ya extraído)"};
        int[] quedan = {1, 1, 1, 0};
        for (int i = 0; i < deadlines.length; i++) {
            Cluster c = cluster(PoliticaTipo.FCFS, 3, LATENCIA, PruebasPaso5.buffer("B1", 3, 0));
            Buffer b1 = c.getBuffers().buscar("B1");
            PruebasPaso5.agregar(b1, PruebasPaso5.proceso("pre-llenado"));
            PCB p = r.registrar(c.crearProceso(DatosProceso.consumidor("cons-red" + deadlines[i], 3, 10, deadlines[i], "B1", 1, 1, 1)));
            PruebasPaso5.correrEnSilencio(c, 20);
            EstadoBuffer e = b1.estado();
            r.verificar("muere " + puntos[i] + " (ciclo " + deadlines[i] + "): quedan " + quedan[i] + " elemento(s), red libre y semáforos en reposo",
                    p.isDeadlineVencido() && p.getCicloFin() == deadlines[i] && e.elementos() == quedan[i]
                    && PruebasPaso5.enReposo(e, 3) && c.getComputador(1).getRed().cantidadPendientes() == 0,
                    p.getCicloFin() + " | " + PruebasPaso5.describir(e));
            c.apagar();
        }
    }

    /** El semSignal de un computador despierta, por interrupción, a un proceso bloqueado en otro computador. */
    private static void probarDespertarEntreComputadores() {
        r.grupo("Despertar entre computadores (productor en PC0, consumidor en PC1)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, 1, PruebasPaso5.buffer("B1", 3, 0));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Monitor-PC1", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 1)));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor-PC0", 3, 10, DEADLINE_LARGO, "B1", 1, 1, 0)));
        boolean bloqueadoEnSemaforo = false;
        for (int i = 0; i < 80; i++) {
            PruebasPaso5.correrEnSilencio(c, 1);
            bloqueadoEnSemaforo |= consumidor.getEstado() == EstadoProceso.BLOQUEADO
                    && consumidor.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO;
        }
        EstadoBuffer e = c.getBuffers().buscar("B1").estado();
        r.verificar("el consumidor de PC1 se bloquea primero en el semáforo 'lleno'", bloqueadoEnSemaforo
                && consumidor.getCiclosBloqueadoSemaforo() > 0, "ciclos bloqueado " + consumidor.getCiclosBloqueadoSemaforo());
        r.verificar("el productor de PC0 lo despierta y ambos terminan bien", PruebasPaso5.terminoBien(productor)
                && PruebasPaso5.terminoBien(consumidor) && productor.getComputador() == 0 && consumidor.getComputador() == 1,
                productor + " | " + consumidor);
        r.verificar("producido 1, consumido 1 y semáforos en reposo", e.producidos() == 1 && e.consumidos() == 1
                && PruebasPaso5.enReposo(e, 3), PruebasPaso5.describir(e));
        c.apagar();
    }

    /** Dos productores y dos consumidores, uno local y otro remoto de cada tipo, con Round Robin en el anfitrión. */
    private static void probarProductoresYConsumidoresMezclados() {
        r.grupo("Varios productores y consumidores, locales y remotos (Round Robin q=2, latencia 2)");
        Cluster c = cluster(PoliticaTipo.ROUND_ROBIN, 2, 2, PruebasPaso5.buffer("B1", 3, 0));
        PCB p1 = r.registrar(c.crearProceso(DatosProceso.productor("P-local", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PCB p2 = r.registrar(c.crearProceso(DatosProceso.productor("P-remoto", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 1)));
        PCB c1 = r.registrar(c.crearProceso(DatosProceso.consumidor("C-local", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PCB c2 = r.registrar(c.crearProceso(DatosProceso.consumidor("C-remoto", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 1)));
        PruebasPaso5.correrEnSilencio(c, 1000);
        Buffer b1 = c.getBuffers().buscar("B1");
        EstadoBuffer e = b1.estado();
        r.verificar("los 4 procesos terminan (sin interbloqueo)", PruebasPaso5.terminoBien(p1) && PruebasPaso5.terminoBien(p2)
                && PruebasPaso5.terminoBien(c1) && PruebasPaso5.terminoBien(c2), p1 + " | " + p2 + " | " + c1 + " | " + c2);
        r.verificar("producidos = consumidos = 8 y semáforos en reposo", e.producidos() == 8 && e.consumidos() == 8
                && PruebasPaso5.enReposo(e, 3) && b1.verificarInvariante(), PruebasPaso5.describir(e));
        r.verificar("la red del PC1 atendió los 8 accesos remotos (4 inserciones + 4 extracciones) y la del PC0 ninguno",
                c.getComputador(1).getRed().getCompletadas() == 8 && c.getComputador(0).getRed().getCompletadas() == 0,
                "PC1 " + c.getComputador(1).getRed().getCompletadas() + ", PC0 " + c.getComputador(0).getRed().getCompletadas());
        r.seccion(" Mezcla de locales y remotos, buffer final:\n" + ReporteTerminal.lineasBuffers(c).stripTrailing());
        c.apagar();
    }

    /** Avanza ciclo a ciclo hasta que el proceso termina (o se agota el límite) y mide su espera por red. */
    private static Espera observarEspera(Cluster c, PCB pcb, int limiteCiclos) {
        Buffer b1 = c.getBuffers().buscar("B1");
        int ciclos = 0;
        int elementos = 0;
        String detalle = "";
        for (int i = 0; i < limiteCiclos && pcb.getEstado() != EstadoProceso.TERMINADO; i++) {
            PruebasPaso5.correrEnSilencio(c, 1);
            if (pcb.getEstado() == EstadoProceso.BLOQUEADO && pcb.getMotivoBloqueo() == MotivoBloqueo.RED) {
                ciclos++;
                elementos += b1.estado().elementos();
                detalle = pcb.getDetalleBloqueo();
            }
        }
        return new Espera(ciclos, elementos, detalle);
    }

    /** Clúster de 2 computadores: el 0 con la política indicada, el 1 FCFS con 1024; sin envejecimiento. */
    private static Cluster cluster(PoliticaTipo politica, int quantum, int latencia, DatosBuffer... buffers) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(1024, politica, quantum));
        computadores.agregarFinal(new ConfigComputador(1024, PoliticaTipo.FCFS, 3));
        ListaEnlazada<DatosBuffer> lista = new ListaEnlazada<>();
        for (DatosBuffer datos : buffers) {
            lista.agregarFinal(datos);
        }
        CondicionesParada parada = new CondicionesParada(0, false, 1, false, CondicionesParada.DEF_INACTIVIDAD_SEGUNDOS);
        ConfiguracionSistema config = new ConfiguracionSistema(true, 0, parada, latencia, 0, computadores, lista,
                new ListaEnlazada<DatosProceso>(), 0);
        return new Cluster(config, log);
    }
}
