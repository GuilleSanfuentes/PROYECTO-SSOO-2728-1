package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.PCB;
import avilaos.sync.Buffer;
import avilaos.sync.DatosBuffer;
import avilaos.vista.ConsolaTerminal;
import java.io.File;
import java.io.IOException;

/** Pruebas de terminal de la etapa 8: validación con motivo, creación en ejecución y guardado de la configuración. */
public final class PruebasPaso8 {

    private static final int DEADLINE_LARGO = 100000;
    private static final int RAM_CHICA = 100;
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso8() {
    }

    /** Ejecuta todas las pruebas de la etapa 8 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(8, "Etapa 8: crear buffers y procesos en ejecución y guardar la configuración");
        probarValidacionDeBuffers();
        probarCreacionDeBuffers();
        probarValidacionDeProcesos();
        probarCreacionEnEjecucion();
        probarCreacionConElRelojEnMarcha();
        probarGuardarYRecargar();
        probarComandosDeConsola();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** Cada dato inválido de un buffer devuelve su motivo; un buffer válido devuelve null. */
    private static void probarValidacionDeBuffers() {
        r.grupo("Validación de buffers (mensaje de error o null)");
        Cluster c = cluster(PruebasPaso5.buffer("B1", 60, 0));
        r.verificar("datos nulos", contiene(c.validarBuffer(null), "nulos"), String.valueOf(c.validarBuffer(null)));
        r.verificar("nombre vacío", contiene(c.validarBuffer(PruebasPaso5.buffer("  ", 3, 0)), "nombre"),
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("  ", 3, 0))));
        r.verificar("capacidad 0", contiene(c.validarBuffer(PruebasPaso5.buffer("X", 0, 0)), "capacidad 0"),
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("X", 0, 0))));
        r.verificar("capacidad sobre el máximo", contiene(c.validarBuffer(PruebasPaso5.buffer("X", 10001, 0)), "capacidad 10001"),
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("X", 10001, 0))));
        r.verificar("anfitrión inexistente", contiene(c.validarBuffer(PruebasPaso5.buffer("X", 3, 7)), "anfitrión 7"),
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("X", 3, 7))));
        r.verificar("nombre repetido", contiene(c.validarBuffer(PruebasPaso5.buffer("B1", 3, 1)), "ya existe"),
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("B1", 3, 1))));
        r.verificar("no cabe en la RAM del anfitrión (60 usados de 100, pide 50)",
                contiene(c.validarBuffer(PruebasPaso5.buffer("B3", 50, 0)), "no caben"),
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("B3", 50, 0))));
        r.verificar("datos válidos -> null", c.validarBuffer(PruebasPaso5.buffer("B2", 3, 1)) == null,
                String.valueOf(c.validarBuffer(PruebasPaso5.buffer("B2", 3, 1))));
        c.apagar();
    }

    /** crearBuffer usa la validación: lo inválido no crea nada y lo válido reserva RAM y queda registrado. */
    private static void probarCreacionDeBuffers() {
        r.grupo("Creación de buffers en ejecución");
        Cluster c = cluster(PruebasPaso5.buffer("B1", 60, 0));
        int usadaAntes = c.getComputador(0).getRam().getUsada();
        r.verificar("los rechazados devuelven null y no tocan la RAM ni el registro",
                c.crearBuffer(PruebasPaso5.buffer("B3", 50, 0)) == null && c.crearBuffer(PruebasPaso5.buffer("B1", 3, 1)) == null
                && c.crearBuffer(null) == null && c.getComputador(0).getRam().getUsada() == usadaAntes
                && c.getBuffers().cantidad() == 1, "usada " + c.getComputador(0).getRam().getUsada());
        Buffer b2 = c.crearBuffer(PruebasPaso5.buffer(" B2 ", 30, 0));
        r.verificar("un buffer válido se crea con el nombre sin espacios y ocupa 30 de RAM más",
                b2 != null && b2.getNombre().equals("B2") && c.getBuffers().buscar("B2") == b2
                && c.getComputador(0).getRam().getUsada() == usadaAntes + 30, String.valueOf(b2));
        r.verificar("la RAM del anfitrión conserva su integridad y queda llena (90 de 100)",
                c.getComputador(0).getRam().verificarIntegridad() && c.getComputador(0).getRam().getUsada() == 90,
                String.valueOf(c.getComputador(0).getRam().getUsada()));
        c.apagar();
    }

    /** Cada dato inválido de un proceso devuelve su motivo; un proceso válido devuelve null. */
    private static void probarValidacionDeProcesos() {
        r.grupo("Validación de procesos (mensaje de error o null)");
        Cluster c = cluster(PruebasPaso5.buffer("B1", 20, 0));
        String vacio = c.validarProceso(DatosProceso.cpuBound("", 5, 10, 100, 5, 0));
        r.verificar("nombre vacío", contiene(vacio, "nombre"), String.valueOf(vacio));
        String prioridad = c.validarProceso(DatosProceso.cpuBound("P", 11, 10, 100, 5, 0));
        r.verificar("prioridad fuera de 1..10", contiene(prioridad, "prioridad 11"), String.valueOf(prioridad));
        String varios = c.validarProceso(DatosProceso.cpuBound("P", 0, 0, 0, 0, 0));
        r.verificar("varios errores juntos, sin ';' sobrante al final", contiene(varios, "prioridad") && contiene(varios, "memoria")
                && contiene(varios, "deadline") && contiene(varios, "instrucciones") && !varios.endsWith(";"), String.valueOf(varios));
        String comp = c.validarProceso(DatosProceso.cpuBound("P", 5, 10, 100, 5, 5));
        r.verificar("computador inexistente", contiene(comp, "computador 5"), String.valueOf(comp));
        String grande = c.validarProceso(DatosProceso.cpuBound("P", 5, 200, 100, 5, 0));
        r.verificar("más memoria que la RAM total del computador elegido", contiene(grande, "RAM total"), String.valueOf(grande));
        String enorme = c.validarProceso(DatosProceso.cpuBound("P", 5, 5000, 100, 5, DatosProceso.ASIGNACION_AUTOMATICA));
        r.verificar("automático con más memoria que la mayor RAM del clúster", contiene(enorme, "mayor RAM"), String.valueOf(enorme));
        String sinBuffer = c.validarProceso(DatosProceso.productor("S", 5, 10, 100, "NoExiste", 1, 3, 0));
        r.verificar("productor con un buffer que no existe", contiene(sinBuffer, "no existe"), String.valueOf(sinBuffer));
        r.verificar("proceso válido -> null",
                c.validarProceso(DatosProceso.cpuBound("P", 5, 10, 100, 5, 0)) == null
                && c.validarProceso(DatosProceso.productor("S", 5, 10, 100, "B1", 1, 3, 1)) == null, "");
        r.verificar("crearProceso rechaza lo mismo que la validación y no deja procesos",
                c.crearProceso(DatosProceso.cpuBound("", 5, 10, 100, 5, 0)) == null
                && c.crearProceso(DatosProceso.productor("S", 5, 10, 100, "NoExiste", 1, 3, 0)) == null
                && c.todosLosProcesos().tamano() == 0, "procesos " + c.todosLosProcesos().tamano());
        c.apagar();
    }

    /** Con la simulación ya avanzada se crean un buffer remoto y sus procesos, y todo termina bien. */
    private static void probarCreacionEnEjecucion() {
        r.grupo("Buffer y procesos creados con la simulación avanzada");
        Cluster c = cluster();
        PruebasPaso5.correrEnSilencio(c, 5);
        Buffer b2 = c.crearBuffer(PruebasPaso5.buffer("B2", 3, 1));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor", 3, 10, DEADLINE_LARGO, "B2", 1, 4, 0)));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Monitor", 3, 10, DEADLINE_LARGO, "B2", 1, 4, 1)));
        PruebasPaso5.correrEnSilencio(c, 600);
        r.verificar("el buffer, el productor (remoto) y el consumidor (local) se crearon", b2 != null && productor != null
                && consumidor != null, "");
        if (b2 == null || productor == null || consumidor == null) {
            c.apagar();
            return;
        }
        r.verificar("ambos procesos terminan bien", PruebasPaso5.terminoBien(productor) && PruebasPaso5.terminoBien(consumidor),
                productor + " | " + consumidor);
        r.verificar("se produjeron y consumieron 4 elementos y el buffer cumple su invariante",
                b2.estado().producidos() == 4 && b2.estado().consumidos() == 4 && b2.verificarInvariante(),
                PruebasPaso5.describir(b2.estado()));
        c.apagar();
    }

    /** Se crean buffer y procesos desde el hilo principal mientras el reloj corre en su propio hilo. */
    private static void probarCreacionConElRelojEnMarcha() {
        r.grupo("Creación mientras el reloj corre en su hilo");
        Cluster c = cluster();
        c.cambiarDuracionCiclo(1);
        c.cambiarMaxCiclos(800);
        boolean iniciado = c.iniciar();
        Buffer b2 = c.crearBuffer(PruebasPaso5.buffer("B2", 2, 0));
        PCB productor = c.crearProceso(DatosProceso.productor("Sensor", 3, 10, DEADLINE_LARGO, "B2", 1, 5, 1));
        PCB consumidor = c.crearProceso(DatosProceso.consumidor("Monitor", 3, 10, DEADLINE_LARGO, "B2", 1, 5, 0));
        PCB cpu = c.crearProceso(DatosProceso.cpuBound("Calc", 5, 10, DEADLINE_LARGO, 10, DatosProceso.ASIGNACION_AUTOMATICA));
        boolean paro = c.esperarFin(20000);
        r.registrar(productor);
        r.registrar(consumidor);
        r.registrar(cpu);
        r.verificar("el reloj arrancó y llegó solo al máximo de ciclos", iniciado && paro, "iniciado " + iniciado + ", fin " + paro);
        r.verificar("todo lo creado en caliente existe", b2 != null && productor != null && consumidor != null && cpu != null, "");
        if (b2 != null && productor != null && consumidor != null && cpu != null) {
            r.verificar("los tres procesos terminan bien", PruebasPaso5.terminoBien(productor)
                    && PruebasPaso5.terminoBien(consumidor) && PruebasPaso5.terminoBien(cpu), productor + " | " + consumidor + " | " + cpu);
            r.verificar("el buffer quedó en reposo con 5 producidos y 5 consumidos",
                    b2.estado().producidos() == 5 && b2.estado().consumidos() == 5 && b2.verificarInvariante(),
                    PruebasPaso5.describir(b2.estado()));
        }
        c.apagar();
    }

    /** La configuración guardada refleja los cambios en ejecución y se vuelve a cargar sin errores. */
    private static void probarGuardarYRecargar() {
        r.grupo("Guardar la configuración y volver a cargarla");
        Cluster c = cluster(PruebasPaso5.buffer("B1", 20, 0));
        c.crearProceso(DatosProceso.productor("Sensor", 3, 10, DEADLINE_LARGO, "B1", 1, 3, 0));
        c.cambiarPolitica(0, PoliticaTipo.EDF);
        c.cambiarQuantum(1, 5);
        c.cambiarDuracionCiclo(200);
        c.cambiarMaxCiclos(77);
        c.crearBuffer(PruebasPaso5.buffer("B2", 4, 1));
        c.crearProceso(DatosProceso.consumidor("Monitor", 3, 10, DEADLINE_LARGO, "B1", 1, 3, 1));
        c.crearProceso(DatosProceso.cpuBound("Calc", 4, 10, 300, 12, DatosProceso.ASIGNACION_AUTOMATICA));
        c.crearBuffer(PruebasPaso5.buffer("B1", 3, 1));
        c.crearProceso(DatosProceso.cpuBound("Mala", 99, 10, 300, 12, 0));
        r.verificar("la configuración actual ya trae lo creado (2 buffers, 3 procesos) y no lo rechazado",
                c.configuracionActual().getBuffers().tamano() == 2 && c.configuracionActual().getProcesos().tamano() == 3,
                c.configuracionActual().getBuffers().tamano() + " buffers, " + c.configuracionActual().getProcesos().tamano() + " procesos");
        r.verificar("una ruta vacía no se guarda", !c.guardarConfiguracion("  ") && !c.guardarConfiguracion(null), "");
        File archivo = null;
        try {
            archivo = File.createTempFile("avilaos_config_prueba", ".json");
            boolean guardado = c.guardarConfiguracion(archivo.getPath());
            ConfiguracionSistema leida = ArchivoConfiguracion.cargar(archivo.getPath());
            r.verificar("se guarda y se recarga sin valores inválidos", guardado && leida.getErroresCarga() == 0,
                    "guardado " + guardado + ", errores " + leida.getErroresCarga());
            ConfigComputador pc0 = leida.getComputadores().obtener(0);
            ConfigComputador pc1 = leida.getComputadores().obtener(1);
            r.verificar("política del computador 0 = EDF, quantum del 1 = 5, RAM intacta", pc0.politica() == PoliticaTipo.EDF
                    && pc1.quantum() == 5 && pc0.ram() == RAM_CHICA && pc1.ram() == 1024, pc0 + " | " + pc1);
            r.verificar("duración del ciclo 200 ms y máximo de ciclos 77", leida.getDuracionCicloMs() == 200
                    && leida.getParada().maxCiclos() == 77, leida.getDuracionCicloMs() + " ms, " + leida.getParada().maxCiclos());
            r.verificar("2 buffers y 3 procesos recargados", leida.getBuffers().tamano() == 2 && leida.getProcesos().tamano() == 3,
                    leida.getBuffers().tamano() + " buffers, " + leida.getProcesos().tamano() + " procesos");
            r.verificar("un proceso de ejemplo conserva sus datos (Calc: prioridad 4, deadline 300, 12 instrucciones, automático)",
                    tieneCalc(leida.getProcesos()), "");
            Cluster nuevo = new Cluster(leida, log);
            r.verificar("un clúster armado con lo guardado acepta los procesos guardados",
                    nuevo.cargarProcesosIniciales() == 3 && nuevo.getBuffers().cantidad() == 2, "");
            nuevo.apagar();
        } catch (IOException e) {
            r.verificar("crear el archivo temporal", false, e.getMessage());
        } finally {
            if (archivo != null) {
                archivo.delete();
            }
        }
        c.apagar();
    }

    /** Los comandos de consola solo llaman al clúster: crean, rechazan sin interrumpir y guardan. */
    private static void probarComandosDeConsola() {
        r.grupo("Comandos de consola (buffer, proceso, guardar)");
        Cluster c = cluster();
        ConsolaTerminal consola = new ConsolaTerminal(c);
        boolean activo = Debug.isActivo();
        Debug.setActivo(false);
        boolean buffer = consola.ejecutarComando("buffer B9 4 1");
        boolean cpu = consola.ejecutarComando("proceso cpu Calc 3 50 200 -1 20");
        boolean io = consola.ejecutarComando("proceso io Imp 4 20 300 1 12 3 2");
        boolean productor = consola.ejecutarComando("proceso productor Sen 3 10 400 0 B9 1 3");
        boolean consumidor = consola.ejecutarComando("proceso consumidor Mon 3 10 400 1 B9 1 3");
        boolean sinBuffer = consola.ejecutarComando("proceso productor Raro 3 10 400 0 Nada 1 3");
        boolean tipoMalo = consola.ejecutarComando("proceso gpu X 3 10 400 0 5");
        boolean textoEnNumero = consola.ejecutarComando("proceso cpu X tres 10 400 0 5");
        boolean faltan = consola.ejecutarComando("proceso cpu X 3");
        boolean bufferDuplicado = consola.ejecutarComando("buffer B9 4 1");
        Debug.setActivo(activo);
        r.verificar("buffer y los cuatro tipos de proceso se crean por comando", buffer && cpu && io && productor && consumidor,
                buffer + " " + cpu + " " + io + " " + productor + " " + consumidor);
        r.verificar("los comandos mal escritos o inválidos devuelven false sin crear nada",
                !sinBuffer && !tipoMalo && !textoEnNumero && !faltan && !bufferDuplicado
                && c.todosLosProcesos().tamano() == 4 && c.getBuffers().cantidad() == 1,
                "procesos " + c.todosLosProcesos().tamano() + ", buffers " + c.getBuffers().cantidad());
        File archivo = null;
        try {
            archivo = File.createTempFile("avilaos_comando_guardar", ".json");
            Debug.setActivo(false);
            boolean guardado = consola.ejecutarComando("guardar " + archivo.getPath());
            Debug.setActivo(activo);
            ConfiguracionSistema leida = ArchivoConfiguracion.cargar(archivo.getPath());
            r.verificar("'guardar <ruta>' escribe un archivo con 4 procesos y 1 buffer", guardado
                    && leida.getProcesos().tamano() == 4 && leida.getBuffers().tamano() == 1,
                    leida.getProcesos().tamano() + " procesos, " + leida.getBuffers().tamano() + " buffers");
        } catch (IOException e) {
            r.verificar("crear el archivo temporal", false, e.getMessage());
        } finally {
            Debug.setActivo(activo);
            if (archivo != null) {
                archivo.delete();
            }
        }
        c.apagar();
    }

    /** Busca el proceso Calc y comprueba sus datos. */
    private static boolean tieneCalc(ListaEnlazada<DatosProceso> procesos) {
        for (int i = 0; i < procesos.tamano(); i++) {
            DatosProceso d = procesos.obtener(i);
            if (d.nombre().equals("Calc")) {
                return d.prioridad() == 4 && d.deadline() == 300 && d.instrucciones() == 12
                        && d.computador() == DatosProceso.ASIGNACION_AUTOMATICA;
            }
        }
        return false;
    }

    private static boolean contiene(String texto, String parte) {
        return texto != null && texto.contains(parte);
    }

    /** Clúster de 2 computadores: el 0 con RAM chica (100) y el 1 con 1024; latencia de red 1, sin envejecimiento. */
    private static Cluster cluster(DatosBuffer... buffers) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(RAM_CHICA, PoliticaTipo.FCFS, 3));
        computadores.agregarFinal(new ConfigComputador(1024, PoliticaTipo.FCFS, 3));
        ListaEnlazada<DatosBuffer> lista = new ListaEnlazada<>();
        for (DatosBuffer datos : buffers) {
            lista.agregarFinal(datos);
        }
        CondicionesParada parada = new CondicionesParada(0, false, 1, false, CondicionesParada.DEF_INACTIVIDAD_SEGUNDOS);
        ConfiguracionSistema config = new ConfiguracionSistema(true, 0, parada, 1, 0, computadores, lista,
                new ListaEnlazada<DatosProceso>(), 0);
        return new Cluster(config, log);
    }
}
