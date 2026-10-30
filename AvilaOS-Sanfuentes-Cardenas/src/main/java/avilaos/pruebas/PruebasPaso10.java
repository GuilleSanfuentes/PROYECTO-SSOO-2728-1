package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.CondicionesParada;
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
import avilaos.sync.Buffer;
import avilaos.sync.DatosBuffer;
import avilaos.sync.EstadoBuffer;
import avilaos.vista.gui.FotoCluster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Pruebas de robustez: todas las políticas local y remoto, escenarios aleatorios, concurrencia, archivos dañados y bordes. */
public final class PruebasPaso10 {

    private static final int AUTO = DatosProceso.ASIGNACION_AUTOMATICA;
    private static final int ESCENARIOS_ALEATORIOS = 60;
    private static final int MUTACIONES_JSON = 300;
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso10() {
    }

    /** Ejecuta todas las pruebas de robustez e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(10, "Etapa 10: robustez (políticas, aleatorios, concurrencia, archivos dañados y bordes)");
        boolean dl = Debug.isActivo();
        Debug.setActivo(false);
        try {
            probarTodasLasPoliticasLocalYRemoto();
            probarEscenariosAleatorios();
            probarConcurrencia();
            probarArranquesYParadas();
            probarArchivosDanados();
            probarBordes();
            probarLlamadasInvalidas();
        } finally {
            Debug.setActivo(dl);
        }
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** 4 políticas × buffer local/remoto con CPU bound, I/O bound, 2 productores y 2 consumidores: todo termina bien. */
    private static void probarTodasLasPoliticasLocalYRemoto() {
        r.grupo("Las 4 políticas, productor-consumidor local y remoto (8 escenarios completos)");
        for (PoliticaTipo politica : PoliticaTipo.values()) {
            for (boolean remoto : new boolean[]{false, true}) {
                String nombre = politica.getNombre() + (remoto ? " remoto" : " local");
                long errores = Debug.getErrores();
                Cluster c = cluster(new int[]{1024, 1024}, politica, 2, remoto ? 3 : 0, 10);
                c.crearBuffer(new DatosBuffer("B1", 3, 0));
                int consumidores = remoto ? 1 : 0;
                crear(c, DatosProceso.cpuBound("calc", 3, 50, 5000, 20, 0));
                crear(c, DatosProceso.ioBound("io", 5, 50, 5000, 15, 4, 3, 1));
                crear(c, DatosProceso.productor("p1", 2, 20, 5000, "B1", 2, 5, 0));
                crear(c, DatosProceso.productor("p2", 4, 20, 5000, "B1", 1, 5, 0));
                crear(c, DatosProceso.consumidor("c1", 3, 20, 5000, "B1", 2, 5, consumidores));
                crear(c, DatosProceso.consumidor("c2", 6, 20, 5000, "B1", 1, 5, consumidores));
                long ciclos = correr(c, 4000);
                String problema = revisar(c, ciclos, true);
                EstadoBuffer b = c.getBuffers().buscar("B1").estado();
                r.verificar(nombre + ": todo termina sin deadlines vencidos, sin ERROR y con invariantes",
                        problema.isEmpty() && Debug.getErrores() == errores, problema + " | errores " + (Debug.getErrores() - errores));
                r.verificar(nombre + ": se produjeron y consumieron los 10 elementos", b.producidos() == 10 && b.consumidos() == 10
                        && b.elementos() == 0, b.producidos() + "/" + b.consumidos() + "/" + b.elementos());
                c.apagar();
            }
        }
    }

    /** Clústeres y cargas al azar (semillas fijas), con cambios de política y quantum en marcha: nada se atasca ni falla. */
    private static void probarEscenariosAleatorios() {
        r.grupo("Escenarios aleatorios (" + ESCENARIOS_ALEATORIOS + " clústeres y cargas al azar, cambios en marcha)");
        int malos = 0;
        String primerProblema = "";
        int procesosTotales = 0;
        long ciclosTotales = 0;
        int vencidos = 0;
        int productoresConsumidores = 0;
        for (int semilla = 1; semilla <= ESCENARIOS_ALEATORIOS; semilla++) {
            Azar azar = new Azar(semilla);
            long errores = Debug.getErrores();
            int n = azar.entre(2, 5);
            int[] rams = new int[n];
            for (int i = 0; i < n; i++) {
                rams[i] = azar.entre(64, 1024);
            }
            Cluster c = cluster(rams, PoliticaTipo.values()[azar.entre(0, 3)], azar.entre(1, 6), azar.entre(0, 4), azar.entre(0, 10));
            for (int i = 0; i < n; i++) {
                c.cambiarPolitica(i, PoliticaTipo.values()[azar.entre(0, 3)]);
            }
            int buffers = azar.entre(0, 3);
            for (int b = 0; b < buffers; b++) {
                DatosBuffer datos = new DatosBuffer("B" + b, azar.entre(1, 5), azar.entre(0, n - 1));
                if (c.validarBuffer(datos) == null) {
                    c.crearBuffer(datos);
                }
            }
            int pedidos = azar.entre(1, 12);
            int maxDeadline = 0;
            for (int p = 0; p < pedidos; p++) {
                DatosProceso datos = procesoAlAzar(azar, "x" + p, n, c);
                if (datos != null && c.validarProceso(datos) == null && c.crearProceso(datos) != null) {
                    maxDeadline = Math.max(maxDeadline, datos.deadline());
                    procesosTotales++;
                }
            }
            long limite = maxDeadline + 50L;
            while (!terminado(c) && c.getReloj().getCiclo() < limite) {
                c.getReloj().ejecutarCiclos(azar.entre(5, 40));
                if (azar.entre(0, 3) == 0) {
                    c.cambiarPolitica(azar.entre(0, n - 1), PoliticaTipo.values()[azar.entre(0, 3)]);
                    c.cambiarQuantum(azar.entre(0, n - 1), azar.entre(1, 6));
                }
                String intermedio = invariantes(c);
                if (!intermedio.isEmpty()) {
                    malos++;
                    primerProblema = primerProblema.isEmpty() ? "semilla " + semilla + ": " + intermedio : primerProblema;
                    break;
                }
            }
            String problema = revisar(c, c.getReloj().getCiclo(), false);
            ciclosTotales += c.getReloj().getCiclo();
            Nodo<PCB> proceso = c.todosLosProcesos().getCabeza();
            while (proceso != null) {
                vencidos += proceso.getDato().isDeadlineVencido() ? 1 : 0;
                productoresConsumidores += proceso.getDato().getTipo().usaBuffer() ? 1 : 0;
                proceso = proceso.getSiguiente();
            }
            if (!problema.isEmpty() || Debug.getErrores() != errores) {
                malos++;
                if (primerProblema.isEmpty()) {
                    primerProblema = "semilla " + semilla + ": " + problema + " | errores " + (Debug.getErrores() - errores);
                }
            }
            c.apagar();
        }
        r.verificar("los " + ESCENARIOS_ALEATORIOS + " escenarios (" + procesosTotales + " procesos) terminan todos, sin ERROR y con invariantes",
                malos == 0, malos + " escenarios con problemas; " + primerProblema);
        r.seccion(" Aleatorios: " + ESCENARIOS_ALEATORIOS + " clústeres, " + procesosTotales + " procesos (" + productoresConsumidores
                + " productores/consumidores), " + ciclosTotales + " ciclos simulados, " + vencidos + " terminados por deadline, "
                + malos + " con problemas");
    }

    /** Reloj en su hilo a 0 ms mientras otros hilos crean procesos, cambian políticas y leen el estado como la GUI. */
    private static void probarConcurrencia() {
        r.grupo("Concurrencia: simulación en marcha + 3 hilos creando, cambiando y leyendo a la vez");
        long errores = Debug.getErrores();
        Cluster c = cluster(new int[]{1024, 1024, 512}, PoliticaTipo.ROUND_ROBIN, 2, 2, 5);
        c.crearBuffer(new DatosBuffer("BC", 4, 1));
        crear(c, DatosProceso.cpuBound("semilla", 5, 10, 100000, 50, 0));
        AtomicInteger excepciones = new AtomicInteger();
        AtomicInteger creados = new AtomicInteger();
        c.iniciar();
        Thread creador = hilo(excepciones, () -> {
            Azar azar = new Azar(99);
            for (int i = 0; i < 40; i++) {
                DatosProceso d = switch (i % 4) {
                    case 0 -> DatosProceso.cpuBound("cc" + i, azar.entre(1, 10), 10, 100000, azar.entre(5, 30), AUTO);
                    case 1 -> DatosProceso.ioBound("ci" + i, azar.entre(1, 10), 10, 100000, azar.entre(5, 30), 3, 2, AUTO);
                    case 2 -> DatosProceso.productor("cp" + i, azar.entre(1, 10), 10, 100000, "BC", 1, 3, azar.entre(0, 2));
                    default -> DatosProceso.consumidor("cn" + i, azar.entre(1, 10), 10, 100000, "BC", 1, 3, azar.entre(0, 2));
                };
                if (c.crearProceso(d) != null) {
                    creados.incrementAndGet();
                }
                dormir(2);
            }
        });
        Thread cambiador = hilo(excepciones, () -> {
            Azar azar = new Azar(7);
            for (int i = 0; i < 200; i++) {
                c.cambiarPolitica(azar.entre(0, 2), PoliticaTipo.values()[azar.entre(0, 3)]);
                c.cambiarQuantum(azar.entre(0, 2), azar.entre(1, 5));
                if (i % 50 == 0) {
                    c.pausar();
                    dormir(3);
                    c.reanudar();
                }
                dormir(1);
            }
        });
        Thread lector = hilo(excepciones, () -> {
            for (int i = 0; i < 300; i++) {
                FotoCluster.de(c);
                c.todosLosProcesos();
                c.getBuffers().buscar("BC").estado();
                dormir(1);
            }
        });
        unir(creador);
        unir(cambiador);
        unir(lector);
        long limite = System.currentTimeMillis() + 30000;
        while (!c.todosTerminados() && System.currentTimeMillis() < limite) {
            dormir(20);
        }
        boolean todos = c.todosTerminados();
        c.detener();
        boolean paro = c.esperarFin(5000);
        String problema = revisar(c, c.getReloj().getCiclo(), true);
        r.verificar("ningún hilo lanzó excepciones", excepciones.get() == 0, excepciones.get() + " excepciones");
        r.verificar("los " + (creados.get() + 1) + " procesos terminan y el reloj se detiene", todos && paro,
                "todos=" + todos + ", paró=" + paro + ", ciclo " + c.getReloj().getCiclo());
        r.verificar("sin ERROR y con invariantes tras la carrera", problema.isEmpty() && Debug.getErrores() == errores,
                problema + " | errores " + (Debug.getErrores() - errores));
        r.seccion(" Concurrencia: " + (creados.get() + 1) + " procesos creados en marcha, " + c.getReloj().getCiclo()
                + " ciclos, " + excepciones.get() + " excepciones, " + (Debug.getErrores() - errores) + " ERROR");
        c.apagar();
    }

    /** Iniciar, pausar, reanudar, detener y apagar muchas veces seguidas no deja hilos colgados. */
    private static void probarArranquesYParadas() {
        r.grupo("Arranques y paradas repetidos (20 veces)");
        boolean bien = true;
        for (int i = 0; i < 20 && bien; i++) {
            Cluster c = cluster(new int[]{256, 256}, PoliticaTipo.values()[i % 4], 2, 1, 0);
            crear(c, DatosProceso.cpuBound("v" + i, 5, 10, 100000, 1000, 0));
            c.iniciar();
            dormir(i % 5);
            c.pausar();
            c.reanudar();
            c.pausar();
            c.detener();
            bien = c.esperarFin(3000) && !c.getReloj().isEjecutando();
            c.apagar();
            c.apagar();
            c.detener();
        }
        r.verificar("cada parada termina el reloj en menos de 3 s y repetir apagar/detener no falla", bien, "");
    }

    /** Archivos de configuración dañados al azar: cargar nunca revienta y la carga estricta solo rechaza con mensaje. */
    private static void probarArchivosDanados() {
        r.grupo("Archivos de configuración dañados (" + MUTACIONES_JSON + " mutaciones + casos extremos)");
        try {
            Path carpeta = Files.createTempDirectory("avilaos-robustez");
            String base = Files.readString(Path.of(ArchivoConfiguracion.RUTA_POR_DEFECTO), StandardCharsets.UTF_8);
            Azar azar = new Azar(2026);
            int revientaCargar = 0;
            int revientaEstricta = 0;
            String detalle = "";
            Path archivo = carpeta.resolve("danado.json");
            for (int i = 0; i < MUTACIONES_JSON + EXTREMOS.length; i++) {
                String texto = i < EXTREMOS.length ? EXTREMOS[i] : mutar(base, azar);
                Files.writeString(archivo, texto, StandardCharsets.UTF_8);
                try {
                    ConfiguracionSistema config = ArchivoConfiguracion.cargar(archivo.toString());
                    if (config == null || config.getComputadores().tamano() < ConfiguracionSistema.MIN_COMPUTADORES) {
                        revientaCargar++;
                    }
                } catch (RuntimeException e) {
                    revientaCargar++;
                    detalle = detalle.isEmpty() ? "cargar: " + e : detalle;
                }
                try {
                    ArchivoConfiguracion.cargarEstricto(archivo.toString());
                } catch (IllegalArgumentException e) {
                    if (e.getMessage() == null || e.getMessage().isBlank()) {
                        revientaEstricta++;
                    }
                } catch (RuntimeException e) {
                    revientaEstricta++;
                    detalle = detalle.isEmpty() ? "estricta: " + e : detalle;
                }
            }
            Files.deleteIfExists(archivo);
            Files.deleteIfExists(carpeta);
            r.verificar("cargar() siempre devuelve una configuración usable (mínimo 2 computadores)", revientaCargar == 0,
                    revientaCargar + " fallos; " + detalle);
            r.verificar("cargarEstricto() solo rechaza con un motivo legible, nunca con otra excepción", revientaEstricta == 0,
                    revientaEstricta + " fallos; " + detalle);
        } catch (IOException e) {
            r.verificar("archivos temporales", false, e.getMessage());
        }
    }

    private static final String[] EXTREMOS = {
        "",
        "   ",
        "null",
        "[]",
        "{",
        "\u0000\u0001\u0002",
        "{\"computadores\": 5}",
        "{\"simulacion\": {\"duracionCicloMs\": 99999999999999999999}}",
        "{\"simulacion\": {\"duracionCicloMs\": \"rápido\", \"maxCiclos\": -7, \"latenciaRed\": 1e309}}",
        "{\"computadores\": [{\"ram\": -1, \"politica\": 3, \"quantum\": null}]}",
        "{\"computadores\": [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}]}",
        "{\"buffers\": [{\"nombre\": \"\", \"capacidad\": 0, \"computador\": -3}, {\"nombre\": \"B1\", \"capacidad\": 2, \"computador\": 0},"
                + " {\"nombre\": \"B1\", \"capacidad\": 2, \"computador\": 0}]}",
        "{\"procesos\": [{\"nombre\": \"x\", \"tipo\": \"MAGIA\"}, {\"tipo\": \"CPU_BOUND\", \"prioridad\": 0, \"memoria\": 999999999,"
                + " \"deadline\": 0, \"instrucciones\": -1}, 42, \"texto\", null, [1, 2]]}",
        "{\"procesos\": [{\"nombre\": \"p\", \"tipo\": \"PRODUCTOR\", \"prioridad\": 2, \"memoria\": 1, \"deadline\": 5, \"buffer\": \"NO\","
                + " \"ciclosPorElemento\": 1, \"elementos\": 1}]}",
        "{\"debugger\": \"tal vez\", \"clave_rara\": {\"a\": [[[[[[]]]]]]}}",
    };

    /** Valores en los bordes: deadline 1, memoria igual a la RAM, buffer de capacidad 1 muy disputado, latencia 0, quantum 1. */
    private static void probarBordes() {
        r.grupo("Valores en los bordes");
        long errores = Debug.getErrores();
        Cluster c = cluster(new int[]{100, 100}, PoliticaTipo.ROUND_ROBIN, 1, 0, 1);
        PCB fugaz = crear(c, DatosProceso.cpuBound("fugaz", 1, 1, 1, 50, 0));
        PCB justo = crear(c, DatosProceso.cpuBound("justo", 9, 100, 3000, 3, 1));
        c.crearBuffer(new DatosBuffer("B", 1, 0));
        for (int i = 0; i < 3; i++) {
            crear(c, DatosProceso.productor("bp" + i, 1 + i, 1, 3000, "B", 1, 2, i % 2));
            crear(c, DatosProceso.consumidor("bc" + i, 4 + i, 1, 3000, "B", 1, 2, (i + 1) % 2));
        }
        PCB eUnica = crear(c, DatosProceso.ioBound("e1", 5, 1, 3000, 1, 1, 1, AUTO));
        long ciclos = correr(c, 5000);
        String problema = revisar(c, ciclos, false);
        r.verificar("deadline 1 se termina por deadline en el ciclo 1", fugaz != null && fugaz.isDeadlineVencido() && fugaz.getCicloFin() == 1,
                String.valueOf(fugaz));
        r.verificar("un proceso que ocupa toda la RAM espera y luego corre completo", justo != null && justo.getEstado() == EstadoProceso.TERMINADO
                && !justo.isDeadlineVencido(), String.valueOf(justo));
        r.verificar("buffer de capacidad 1 con 3 productores y 3 consumidores, locales y remotos: 6 y 6",
                c.getBuffers().buscar("B").estado().producidos() == 6 && c.getBuffers().buscar("B").estado().consumidos() == 6,
                c.getBuffers().buscar("B").estado().toString());
        r.verificar("I/O bound de 1 sola instrucción (que es la E/S) termina", eUnica != null && eUnica.getEstado() == EstadoProceso.TERMINADO,
                String.valueOf(eUnica));
        r.verificar("todo termina con invariantes y sin ERROR", problema.isEmpty() && Debug.getErrores() == errores,
                problema + " | errores " + (Debug.getErrores() - errores));
        c.apagar();
    }

    /** Llamadas con datos inválidos se rechazan devolviendo false/null, sin excepciones y sin romper el sistema. */
    private static void probarLlamadasInvalidas() {
        r.grupo("Llamadas inválidas a la API del clúster");
        Cluster c = cluster(new int[]{128, 128}, PoliticaTipo.FCFS, 3, 1, 0);
        c.crearBuffer(new DatosBuffer("B", 2, 0));
        int excepciones = 0;
        boolean rechazos = true;
        try {
            rechazos &= c.crearProceso(null) == null;
            rechazos &= c.crearProceso(new DatosProceso(null, null, 0, 0, 0, 0, 0, 0, null, 0, 0, 0)) == null;
            rechazos &= c.crearProceso(DatosProceso.cpuBound("x", 0, 10, 10, 10, 0)) == null;
            rechazos &= c.crearProceso(DatosProceso.cpuBound("x", 5, 10, 10, 10, 7)) == null;
            rechazos &= c.crearProceso(DatosProceso.cpuBound("x", 5, 999, 10, 10, AUTO)) == null;
            rechazos &= c.crearProceso(DatosProceso.productor("x", 5, 10, 10, "NOEXISTE", 1, 1, 0)) == null;
            rechazos &= c.crearBuffer(null) == null;
            rechazos &= c.crearBuffer(new DatosBuffer("B", 2, 0)) == null;
            rechazos &= c.crearBuffer(new DatosBuffer("C", 500, 0)) == null;
            rechazos &= c.crearBuffer(new DatosBuffer("D", 1, 9)) == null;
            rechazos &= !c.cambiarPolitica(9, PoliticaTipo.EDF);
            rechazos &= !c.cambiarPolitica(0, null);
            rechazos &= !c.cambiarQuantum(0, 0);
            rechazos &= !c.cambiarQuantum(-1, 3);
            rechazos &= !c.cambiarDuracionCiclo(-1);
            rechazos &= !c.cambiarMaxCiclos(-5);
            rechazos &= c.getComputador(99) == null;
            c.getReloj().ejecutarCiclos(5);
        } catch (RuntimeException e) {
            excepciones++;
        }
        r.verificar("17 llamadas inválidas rechazadas sin excepciones", rechazos && excepciones == 0,
                "rechazos=" + rechazos + ", excepciones=" + excepciones);
        r.verificar("el clúster sigue íntegro después", invariantes(c).isEmpty(), invariantes(c));
        c.apagar();
    }

    /** Proceso al azar de cualquier tipo, con parámetros dentro de rangos válidos; null si no hay buffer para P/C. */
    private static DatosProceso procesoAlAzar(Azar azar, String nombre, int computadores, Cluster c) {
        int tipo = azar.entre(0, 3);
        int prioridad = azar.entre(1, 10);
        int memoria = azar.entre(1, 60);
        int deadline = azar.entre(20, 400);
        int destino = azar.entre(-1, computadores - 1);
        if (tipo >= 2) {
            ListaEnlazada<Buffer> buffers = c.getBuffers().copiar();
            if (buffers.estaVacia()) {
                return null;
            }
            String buffer = buffers.obtener(azar.entre(0, buffers.tamano() - 1)).getNombre();
            return tipo == 2
                    ? DatosProceso.productor(nombre, prioridad, memoria, deadline, buffer, azar.entre(1, 4), azar.entre(1, 5), destino)
                    : DatosProceso.consumidor(nombre, prioridad, memoria, deadline, buffer, azar.entre(1, 4), azar.entre(1, 5), destino);
        }
        int instrucciones = azar.entre(1, 40);
        return tipo == 0
                ? DatosProceso.cpuBound(nombre, prioridad, memoria, deadline, instrucciones, destino)
                : DatosProceso.ioBound(nombre, prioridad, memoria, deadline, instrucciones, azar.entre(1, 10), azar.entre(1, 6), destino);
    }

    /** Ejecuta hasta que todos los procesos terminen o se llegue al máximo de ciclos; devuelve el ciclo final. */
    private static long correr(Cluster c, int maximo) {
        while (!terminado(c) && c.getReloj().getCiclo() < maximo) {
            c.getReloj().ejecutarCiclos(10);
        }
        return c.getReloj().getCiclo();
    }

    private static boolean terminado(Cluster c) {
        return c.getReloj().getCiclo() > 0 && c.todosTerminados();
    }

    /** Problemas al final de una corrida: procesos vivos, programas incompletos, RAM, buffers y contabilidad de la CPU. */
    private static String revisar(Cluster c, long ciclos, boolean sinVencidos) {
        StringBuilder sb = new StringBuilder(invariantes(c));
        Nodo<PCB> nodo = c.todosLosProcesos().getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            if (p.getEstado() != EstadoProceso.TERMINADO) {
                sb.append(p.getEtiqueta()).append(" sigue en ").append(p.getEstado().getNombre()).append("; ");
            } else if (!p.isDeadlineVencido() && !p.programaTerminado()) {
                sb.append(p.getEtiqueta()).append(" terminó sin completar su programa; ");
            } else if (sinVencidos && p.isDeadlineVencido()) {
                sb.append(p.getEtiqueta()).append(" venció su deadline; ");
            }
            nodo = nodo.getSiguiente();
        }
        if (c.todosTerminados()) {
            for (int i = 0; i < c.cantidadComputadores(); i++) {
                Computador pc = c.getComputador(i);
                int deBuffers = 0;
                Nodo<Buffer> b = c.getBuffers().copiar().getCabeza();
                while (b != null) {
                    deBuffers += b.getDato().getAnfitrion() == i ? b.getDato().getCapacidad() : 0;
                    b = b.getSiguiente();
                }
                if (pc.getRam().getUsada() != deBuffers) {
                    sb.append(pc.getNombre()).append(" RAM usada ").append(pc.getRam().getUsada()).append(" (esperada ")
                            .append(deBuffers).append(" de buffers); ");
                }
                if (pc.getCpu().getIdProceso() != CPU.SIN_PROCESO) {
                    sb.append(pc.getNombre()).append(" CPU con proceso cargado; ");
                }
            }
            Nodo<Buffer> b = c.getBuffers().copiar().getCabeza();
            while (b != null) {
                EstadoBuffer e = b.getDato().estado();
                if (e.valorMutex() != 1 || e.valorLleno() != e.elementos() || e.valorVacio() + e.valorLleno() != e.capacidad()
                        || !e.bloqueadosVacio().estaVacia() || !e.bloqueadosLleno().estaVacia() || !e.bloqueadosMutex().estaVacia()) {
                    sb.append(e.nombre()).append(" quedó inconsistente (vacío ").append(e.valorVacio()).append(", lleno ")
                            .append(e.valorLleno()).append(", mutex ").append(e.valorMutex()).append(", elementos ")
                            .append(e.elementos()).append("); ");
                }
                b = b.getSiguiente();
            }
        }
        for (int i = 0; i < c.cantidadComputadores(); i++) {
            CPU cpu = c.getComputador(i).getCpu();
            if (cpu.getCiclosUsuario() + cpu.getCiclosSO() + cpu.getCiclosOciosos() != ciclos) {
                sb.append(cpu.getNombre()).append(" contó ").append(cpu.getCiclosUsuario() + cpu.getCiclosSO() + cpu.getCiclosOciosos())
                        .append(" de ").append(ciclos).append(" ciclos; ");
            }
        }
        return sb.toString();
    }

    /** Integridad de cada RAM e invariante de cada buffer en este instante. */
    private static String invariantes(Cluster c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.cantidadComputadores(); i++) {
            if (!c.getComputador(i).getRam().verificarIntegridad()) {
                sb.append("RAM del computador ").append(i).append(" sin integridad; ");
            }
        }
        Nodo<Buffer> b = c.getBuffers().copiar().getCabeza();
        while (b != null) {
            if (!b.getDato().verificarInvariante()) {
                sb.append("invariante rota en ").append(b.getDato().getNombre()).append("; ");
            }
            b = b.getSiguiente();
        }
        return sb.toString();
    }

    /** Clúster sin parada automática, con una misma política y quantum en todos los computadores. */
    private static Cluster cluster(int[] rams, PoliticaTipo politica, int quantum, int latencia, int envejecimiento) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        for (int ram : rams) {
            computadores.agregarFinal(new ConfigComputador(ram, politica, quantum));
        }
        ConfiguracionSistema config = new ConfiguracionSistema(false, 0, new CondicionesParada(0, false, 1, false, 1),
                latencia, envejecimiento, computadores, new ListaEnlazada<DatosBuffer>(), new ListaEnlazada<DatosProceso>(), 0);
        return new Cluster(config, log);
    }

    private static PCB crear(Cluster c, DatosProceso datos) {
        return r.registrar(c.crearProceso(datos));
    }

    /** Cambia caracteres al azar: borrar, duplicar, reemplazar por símbolos JSON o cortar el texto. */
    private static String mutar(String base, Azar azar) {
        StringBuilder sb = new StringBuilder(base);
        int cambios = azar.entre(1, 6);
        String simbolos = "{}[]\":,-0123456789eE.abcxyz \n";
        for (int i = 0; i < cambios && sb.length() > 0; i++) {
            int pos = azar.entre(0, sb.length() - 1);
            switch (azar.entre(0, 3)) {
                case 0 -> sb.deleteCharAt(pos);
                case 1 -> sb.insert(pos, sb.charAt(pos));
                case 2 -> sb.setCharAt(pos, simbolos.charAt(azar.entre(0, simbolos.length() - 1)));
                default -> sb.setLength(pos);
            }
        }
        return sb.toString();
    }

    private static Thread hilo(AtomicInteger excepciones, Runnable trabajo) {
        Thread t = new Thread(() -> {
            try {
                trabajo.run();
            } catch (RuntimeException e) {
                excepciones.incrementAndGet();
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void unir(Thread t) {
        try {
            t.join(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Generador de números pseudoaleatorios con semilla fija (xorshift), para que cada corrida sea reproducible. */
    private static final class Azar {

        private long estado;

        Azar(long semilla) {
            estado = semilla * 0x9E3779B97F4A7C15L + 1;
        }

        /** Entero uniforme entre min y max, ambos incluidos. */
        int entre(int min, int max) {
            estado ^= estado << 13;
            estado ^= estado >>> 7;
            estado ^= estado << 17;
            return min + (int) Math.floorMod(estado, (long) (max - min + 1));
        }
    }
}
