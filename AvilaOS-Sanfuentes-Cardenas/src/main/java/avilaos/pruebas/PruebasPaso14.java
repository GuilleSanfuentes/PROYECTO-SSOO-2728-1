package avilaos.pruebas;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.config.ArchivoProcesos;
import avilaos.config.ArchivoSistemaArchivos;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfigDisco;
import avilaos.config.ConfiguracionSistema;
import avilaos.config.DatosProcesos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoEntrada;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProcesoArchivo;
import avilaos.so.archivos.InfoEntrada;
import avilaos.sync.DatosBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pruebas de terminal de la etapa 14: un computador y los dos JSON obligatorios. */
public final class PruebasPaso14 {

    private static final String RUTA_FS = "config/sistema_archivos_enunciado.json";
    private static final String RUTA_PROCESOS = "config/procesos_enunciado.json";
    private static final String[] RUTAS_ENUNCIADO = {"/sistema/config.sys", "/usuarios/video.mp4", "/usuarios/notas.txt",
        "/usuarios/img1.png", "/sistema/boot.bin", "/usuarios/data.csv", "/sistema/script.py", "/sistema/core.dll"};
    private static final int[] BLOQUES_ENUNCIADO = {95, 180, 34, 119, 11, 123, 62, 64};
    private static final String BUENO = "{\"name\":\"ok\",\"memory\":16,\"deadline\":50,\"instructions\":2,"
            + "\"targetPath\":\"/d/a\",\"operation\":\"READ\",\"fileSizeBlocks\":0}";

    private static Resultados r;

    private PruebasPaso14() {
    }

    /** Ejecuta todas las pruebas de la etapa 14 e imprime su resumen. */
    public static void ejecutar(LogEventos log) {
        r = new Resultados(14, "Etapa 14: un computador y los dos JSON obligatorios");
        probarProcesosDelEnunciado();
        probarOpcionales();
        probarEntradasMalas();
        probarSeccionesYArchivos();
        probarQuantum();
        probarBuffersOpcionales();
        probarCargaObligatoria(log);
        probarSistemaArmado(log);
        probarRelojYDisco(log);
        probarModos(log);
        probarGuardarYRecargar();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El JSON de procesos del enunciado se lee exacto. */
    private static void probarProcesosDelEnunciado() {
        r.grupo("JSON de procesos del enunciado");
        long errores = Debug.getErrores();
        DatosProcesos d = ArchivoProcesos.leer(RUTA_PROCESOS);
        r.verificar("se puede cargar, sin observaciones", d.cargable() && d.problemas().estaVacia(), texto(d.problemas()));
        r.verificar("ciclo de 300 ms, swap de 100 y política FIFO",
                d.duracionCicloMs() == 300 && d.bloquesSwap() == 100 && d.politicaDisco() == PoliticaDiscoTipo.FIFO, d.toString());
        r.verificar("velocidad del cabezal 1 y debugger sin indicar", d.velocidadCabezal() == 1 && d.debugger() == null, "");
        r.verificar("RAM 1024, FCFS y quantum 0 (3 por dentro)", d.ram() == 1024 && d.politica() == PoliticaTipo.FCFS
                && d.quantum() == 0 && d.quantumInterno() == 3, d.quantum() + " / " + d.quantumInterno());
        r.verificar("sin buffers", d.buffers().estaVacia(), "");
        r.verificar("son 8 procesos", d.procesos().tamano() == 8, d.procesos().tamano() + "");
        for (int i = 0; i < d.procesos().tamano() && i < RUTAS_ENUNCIADO.length; i++) {
            DatosProcesoArchivo p = d.procesos().obtener(i);
            TipoOperacionES esperada = i == 2 || i == 5 ? TipoOperacionES.ACTUALIZAR
                    : i == 3 ? TipoOperacionES.ELIMINAR : TipoOperacionES.LEER;
            boolean exacto = p.nombre().equals("Proc_" + BLOQUES_ENUNCIADO[i]) && p.ruta().equals(RUTAS_ENUNCIADO[i])
                    && p.operacion() == esperada && p.memoria() == 16 && p.deadline() == 500 && p.instrucciones() == 2
                    && p.bloquesArchivo() == 0 && p.prioridad() == 5 && p.nuevoNombre() == null;
            r.verificar("proceso " + (i + 1) + " exacto (Proc_" + BLOQUES_ENUNCIADO[i] + ")", exacto, p.toString());
        }
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Claves opcionales: headSpeed, debugger, priority y newName. */
    private static void probarOpcionales() {
        r.grupo("Claves opcionales");
        long errores = Debug.getErrores();
        DatosProcesos d = ArchivoProcesos.interpretar(json("\"headSpeed\":4,\"debugger\":true,", "SCAN", "FCFS", "0",
                "{\"name\":\"R\",\"memory\":8,\"deadline\":9,\"instructions\":1,\"targetPath\":\"/d/a\","
                + "\"operation\":\"update\",\"fileSizeBlocks\":0,\"priority\":1,\"newName\":\"b.txt\"},"
                + "{\"name\":\"C\",\"memory\":8,\"deadline\":9,\"instructions\":3,\"targetPath\":\"/d/n/\","
                + "\"operation\":\"CREATE\",\"fileSizeBlocks\":7}"));
        r.verificar("sin observaciones", d.cargable() && d.problemas().estaVacia(), texto(d.problemas()));
        r.verificar("headSpeed 4, debugger true y política SCAN", d.velocidadCabezal() == 4
                && Boolean.TRUE.equals(d.debugger()) && d.politicaDisco() == PoliticaDiscoTipo.SCAN, d.toString());
        r.verificar("priority y newName en un UPDATE", d.procesos().tamano() == 2 && d.procesos().obtener(0).prioridad() == 1
                && "b.txt".equals(d.procesos().obtener(0).nuevoNombre()), "");
        r.verificar("CREATE de 7 bloques, ruta normalizada y prioridad 5", d.procesos().tamano() == 2
                && d.procesos().obtener(1).bloquesArchivo() == 7 && d.procesos().obtener(1).ruta().equals("/d/n")
                && d.procesos().obtener(1).prioridad() == 5, "");
        DatosProcesos ignorado = ArchivoProcesos.interpretar(json("", "FIFO", "FCFS", "0",
                BUENO.replace("}", ",\"newName\":\"x\"}")));
        r.verificar("newName fuera de UPDATE se ignora y se reporta", ignorado.procesos().tamano() == 1
                && ignorado.procesos().primero().nuevoNombre() == null && contiene(ignorado.problemas(), "solo se usa con UPDATE"),
                texto(ignorado.problemas()));
        r.verificar("todas las políticas de disco se reconocen", politicasReconocidas(), "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Cada proceso malo se aparta con número, ruta y motivo; los buenos se cargan. */
    private static void probarEntradasMalas() {
        r.grupo("Entradas malas: se aparta y se reporta");
        long errores = Debug.getErrores();
        String[][] casos = {
            {"no es un objeto", "17", "no es un objeto"},
            {"sin nombre", BUENO.replace("\"name\":\"ok\",", ""), "'name'"},
            {"memoria 0", BUENO.replace("\"memory\":16", "\"memory\":0"), "'memory'"},
            {"memoria mayor que la RAM", BUENO.replace("\"memory\":16", "\"memory\":4096"), "'memory'"},
            {"memoria como texto", BUENO.replace("\"memory\":16", "\"memory\":\"16\""), "'memory'"},
            {"deadline negativo", BUENO.replace("\"deadline\":50", "\"deadline\":-1"), "'deadline'"},
            {"instrucciones decimales", BUENO.replace("\"instructions\":2", "\"instructions\":2.5"), "'instructions'"},
            {"ruta relativa", BUENO.replace("/d/a", "d/a"), "'targetPath'"},
            {"ruta raíz", BUENO.replace("/d/a", "/"), "'targetPath'"},
            {"operación desconocida", BUENO.replace("READ", "COPY"), "'operation'"},
            {"operación de intercambio", BUENO.replace("READ", "SUSPEND"), "'operation'"},
            {"CREATE de 0 bloques", BUENO.replace("READ", "CREATE"), "'fileSizeBlocks'"},
            {"bloques negativos", BUENO.replace("\"fileSizeBlocks\":0", "\"fileSizeBlocks\":-2"), "'fileSizeBlocks'"},
            {"prioridad 11", BUENO.replace("}", ",\"priority\":11}"), "'priority'"},
            {"newName con barra", BUENO.replace("READ", "UPDATE").replace("}", ",\"newName\":\"a/b\"}"), "'newName'"}
        };
        for (String[] caso : casos) {
            DatosProcesos d = ArchivoProcesos.interpretar(json("", "FIFO", "FCFS", "0", BUENO + "," + caso[1]));
            r.verificar(caso[0] + ": se aparta la entrada 2 y se carga la otra", d.cargable() && d.procesos().tamano() == 1
                    && contiene(d.problemas(), "entrada 2") && contiene(d.problemas(), caso[2]), texto(d.problemas()));
        }
        DatosProcesos conRuta = ArchivoProcesos.interpretar(json("", "FIFO", "FCFS", "0",
                BUENO.replace("\"deadline\":50", "\"deadline\":0")));
        r.verificar("el reporte indica número, nombre y ruta", contiene(conRuta.problemas(), "entrada 1 (ok, /d/a)"),
                texto(conRuta.problemas()));
        DatosProcesos clave = ArchivoProcesos.interpretar(json("\"velocidad\":2,", "FIFO", "FCFS", "0",
                BUENO.replace("}", ",\"color\":1}")));
        r.verificar("claves desconocidas se reportan sin apartar", clave.procesos().tamano() == 1
                && contiene(clave.problemas(), "'velocidad'") && contiene(clave.problemas(), "'color'"), texto(clave.problemas()));
        r.verificar("una entrada mala no es ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Ajustes inválidos toman el valor por defecto; solo es incargable sin JSON o sin secciones raíz. */
    private static void probarSeccionesYArchivos() {
        r.grupo("Secciones raíz y valores globales");
        String malos = "{\"globalConfig\":{\"cycleDurationMs\":-5,\"swapSizeBlocks\":\"x\",\"initialDiskPolicy\":\"LIFO\","
                + "\"headSpeed\":0,\"debugger\":3},\"computer\":{\"ramCapacity\":0,\"policy\":\"SJF\",\"quantum\":-1},"
                + "\"processes\":[]}";
        DatosProcesos d = ArchivoProcesos.interpretar(malos);
        r.verificar("valores inválidos: se carga con los de por defecto", d.cargable() && d.duracionCicloMs() == 500
                && d.bloquesSwap() == 100 && d.politicaDisco() == PoliticaDiscoTipo.FIFO && d.velocidadCabezal() == 1
                && d.debugger() == null && d.ram() == 1024 && d.politica() == PoliticaTipo.FCFS && d.quantum() == 3,
                d.toString());
        r.verificar("se reportan los 8 valores", d.problemas().tamano() == 8, texto(d.problemas()));
        String base = json("", "FIFO", "FCFS", "0", BUENO);
        r.verificar("texto que no es JSON: incargable con ERROR", conError(() -> !ArchivoProcesos.interpretar("{ hola").cargable()), "");
        r.verificar("raíz que no es objeto: incargable", conError(() -> !ArchivoProcesos.interpretar("[1,2]").cargable()), "");
        r.verificar("sin globalConfig: incargable", conError(() -> !ArchivoProcesos.interpretar(
                base.replace("globalConfig", "otra")).cargable()), "");
        r.verificar("sin computer: incargable", conError(() -> !ArchivoProcesos.interpretar(
                base.replace("\"computer\"", "\"otra\"")).cargable()), "");
        r.verificar("computer como lista: incargable (un solo computador)", conError(() -> !ArchivoProcesos.interpretar(
                "{\"globalConfig\":{},\"computer\":[{},{}],\"processes\":[]}").cargable()), "");
        r.verificar("sin processes: incargable", conError(() -> !ArchivoProcesos.interpretar(
                base.replace("processes", "otra")).cargable()), "");
        r.verificar("archivo inexistente: incargable", conError(() -> !ArchivoProcesos.leer("config/no_existe.json").cargable()), "");
    }

    /** El quantum 0 vale sin Round Robin y es inválido con Round Robin. */
    private static void probarQuantum() {
        r.grupo("Quantum 0 según la política");
        long errores = Debug.getErrores();
        for (String politica : new String[]{"FCFS", "EDF", "PRIORIDADES"}) {
            DatosProcesos d = ArchivoProcesos.interpretar(json("", "FIFO", politica, "0", BUENO));
            r.verificar(politica + " con quantum 0: válido y usa 3", d.problemas().estaVacia() && d.quantum() == 0
                    && d.quantumInterno() == 3, texto(d.problemas()));
        }
        DatosProcesos rr = ArchivoProcesos.interpretar(json("", "FIFO", "ROUND_ROBIN", "0", BUENO));
        r.verificar("Round Robin con quantum 0: inválido, se reporta y se usa 3", rr.quantum() == 3
                && contiene(rr.problemas(), "computer.quantum"), texto(rr.problemas()));
        DatosProcesos cinco = ArchivoProcesos.interpretar(json("", "FIFO", "ROUND_ROBIN", "5", BUENO));
        r.verificar("Round Robin con quantum 5: válido", cinco.problemas().estaVacia() && cinco.quantumInterno() == 5, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Los buffers del Proyecto 1 solo existen si el JSON trae la clave opcional. */
    private static void probarBuffersOpcionales() {
        r.grupo("Buffers opcionales");
        long errores = Debug.getErrores();
        String conBuffers = json("", "FIFO", "FCFS", "0", BUENO).replace("\"processes\"",
                "\"buffers\":[{\"name\":\"B1\",\"capacity\":5},{\"name\":\"B1\",\"capacity\":2},"
                + "{\"name\":\"G\",\"capacity\":5000}],\"processes\"");
        DatosProcesos d = ArchivoProcesos.interpretar(conBuffers);
        r.verificar("se carga B1 y se apartan el repetido y el que no cabe", d.buffers().tamano() == 1
                && d.problemas().tamano() == 2, texto(d.problemas()));
        r.verificar("el buffer queda en el computador 0", d.buffers().tamano() == 1 && d.buffers().primero().computador() == 0, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Sin los dos JSON no se arma nada y se explica cuál falta. */
    private static void probarCargaObligatoria(LogEventos log) {
        r.grupo("Los dos JSON son obligatorios");
        long errores = Debug.getErrores();
        CargaSimulacion vacia = new CargaSimulacion();
        r.verificar("sin ninguno: se rechaza nombrando ambos", rechaza(vacia, log, "sistema de archivos")
                && vacia.motivoNoArmar().contains("procesos"), vacia.motivoNoArmar());
        CargaSimulacion soloFs = new CargaSimulacion();
        soloFs.cargarSistemaArchivos(RUTA_FS);
        r.verificar("solo el sistema de archivos: falta el de procesos", rechaza(soloFs, log, "JSON de procesos"),
                soloFs.motivoNoArmar());
        CargaSimulacion soloProcesos = new CargaSimulacion();
        soloProcesos.cargarProcesos(RUTA_PROCESOS);
        r.verificar("solo procesos: falta el sistema de archivos", rechaza(soloProcesos, log, "sistema de archivos"),
                soloProcesos.motivoNoArmar());
        r.verificar("rechazar el armado no es ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        soloProcesos.cargarSistemaArchivos(RUTA_FS);
        r.verificar("con los dos ya no hay motivo", soloProcesos.motivoNoArmar() == null, soloProcesos.motivoNoArmar());
        DatosProcesos anteriores = soloProcesos.getProcesos();
        r.verificar("un JSON incargable no reemplaza al que ya estaba",
                conError(() -> !soloProcesos.cargarProcesos("config/no_existe.json").cargable())
                && soloProcesos.getProcesos() == anteriores, "");
    }

    /** El sistema armado con los JSON del enunciado: un computador, su disco y sus archivos. */
    private static void probarSistemaArmado(LogEventos log) {
        r.grupo("Sistema armado con los JSON del enunciado");
        long errores = Debug.getErrores();
        CargaSimulacion carga = cargaDelEnunciado();
        CargaSimulacion.Resultado resultado = carga.armar(log);
        r.verificar("se arma sin motivo de rechazo y sin observaciones", resultado.armado() && resultado.motivo() == null
                && resultado.problemas().estaVacia(), texto(resultado.problemas()));
        if (!resultado.armado()) {
            return;
        }
        Cluster cluster = resultado.cluster();
        Computador pc = cluster.getComputador(0);
        DiscoSD disco = pc.getDisco();
        r.verificar("exactamente 1 computador", cluster.cantidadComputadores() == 1 && cluster.getComputador(1) == null, "");
        r.verificar("disco de 200 bloques + 100 de swap", disco.getBloquesArchivos() == 200 && disco.getBloquesSwap() == 100
                && disco.getTotalPosiciones() == 300, disco.getTotalPosiciones() + "");
        r.verificar("cabezal en 50 y velocidad 1", disco.getCabezal() == 50 && disco.getVelocidad() == 1,
                disco.getCabezal() + " / " + disco.getVelocidad());
        r.verificar("política de disco FIFO", pc.getPlanificadorDisco().getPolitica() == PoliticaDiscoTipo.FIFO, "");
        r.verificar("RAM 1024, FCFS, quantum interno 3 y 300 ms por ciclo", pc.getRam().getCapacidad() == 1024
                && pc.getKernel().getPolitica() == PoliticaTipo.FCFS && pc.getKernel().getQuantum() == 3
                && cluster.getReloj().getDuracionCicloMs() == 300, "");
        int directorios = 0;
        Nodo<InfoEntrada> nodo = pc.getSistemaArchivos().listarTodo().getCabeza();
        while (nodo != null) {
            if (nodo.getDato().tipo() == TipoEntrada.DIRECTORIO && !nodo.getDato().ruta().equals("/")) {
                directorios++;
            }
            nodo = nodo.getSiguiente();
        }
        r.verificar("2 directorios y 8 archivos", directorios == 2 && pc.getSistemaArchivos().listarArchivos().tamano() == 8,
                directorios + " y " + pc.getSistemaArchivos().listarArchivos().tamano());
        for (int i = 0; i < RUTAS_ENUNCIADO.length; i++) {
            int[] bloques = pc.getSistemaArchivos().bloquesDe(RUTAS_ENUNCIADO[i]);
            r.verificar(RUTAS_ENUNCIADO[i] + " en el bloque " + BLOQUES_ENUNCIADO[i],
                    bloques != null && bloques.length == 1 && bloques[0] == BLOQUES_ENUNCIADO[i], "");
        }
        r.verificar("192 bloques libres y el swap completo", disco.contarLibres(false) == 192 && disco.contarLibres(true) == 100,
                disco.contarLibres(false) + " / " + disco.contarLibres(true));
        r.verificar("disco y sistema de archivos íntegros",
                disco.verificarIntegridad() && pc.getSistemaArchivos().verificarIntegridad(), "");
        r.verificar("sin buffers y sin procesos todavía", cluster.getBuffers().cantidad() == 0 && pc.carga() == 0, "");
        CargaSimulacion.Resultado otro = carga.armar(log);
        r.verificar("se puede volver a armar: sistema nuevo y sin observaciones repetidas", otro.armado()
                && otro.cluster() != cluster && otro.problemas().estaVacia(), texto(otro.problemas()));
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Sistema armado: " + cluster.cantidadComputadores() + " computador, disco " + disco.getBloquesArchivos()
                + "+" + disco.getBloquesSwap() + ", cabezal " + disco.getCabezal() + ", "
                + pc.getPlanificadorDisco().getPolitica().getNombre());
        cluster.apagar();
        if (otro.armado()) {
            otro.cluster().apagar();
        }
    }

    /** El disco es un componente del reloj: queda ocioso sin solicitudes y atiende las que se encolan. */
    private static void probarRelojYDisco(LogEventos log) {
        r.grupo("El disco avanza con el reloj");
        long errores = Debug.getErrores();
        CargaSimulacion.Resultado resultado = cargaDelEnunciado().armar(log);
        if (!resultado.armado()) {
            r.verificar("se arma el sistema", false, resultado.motivo());
            return;
        }
        Cluster cluster = resultado.cluster();
        Computador pc = cluster.getComputador(0);
        DiscoSD disco = pc.getDisco();
        r.verificar("antes de iniciar no ha corrido ningún ciclo", cluster.getReloj().getCiclo() == 0
                && disco.getCiclosOciosos() == 0, "");
        cluster.getReloj().ejecutarCiclos(20);
        r.verificar("20 ciclos sin solicitudes: 20 ociosos", disco.getCiclosOciosos() == 20, disco.getCiclosOciosos() + "");
        r.verificar("sin movimiento ni transferencias", disco.getCabezal() == 50 && disco.getDesplazamientoTotal() == 0
                && disco.getCiclosMoviendo() == 0 && disco.getCiclosTransfiriendo() == 0, "");
        pc.getPlanificadorDisco().encolar(1, "P1", TipoOperacionES.LEER, "/sistema/script.py",
                pc.getSistemaArchivos().bloquesDe("/sistema/script.py"), cluster.getReloj().getCiclo());
        cluster.getReloj().ejecutarCiclos(13);
        r.verificar("una solicitud al bloque 62: 12 ciclos de movimiento y 1 de lectura",
                pc.getPlanificadorDisco().getAtendidas() == 1 && disco.getCabezal() == 62
                && disco.getDesplazamientoTotal() == 12 && disco.getCiclosTransfiriendo() == 1,
                disco.getCabezal() + " / " + disco.getDesplazamientoTotal());
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        cluster.apagar();
    }

    /** El modo del Proyecto 1 no cambia y el modo con disco exige un computador. */
    private static void probarModos(LogEventos log) {
        r.grupo("Modo del Proyecto 1 y modo con disco");
        long errores = Debug.getErrores();
        Cluster p1 = new Cluster(configuracion(2), log);
        r.verificar("Proyecto 1: 2 computadores sin disco ni sistema de archivos", p1.cantidadComputadores() == 2
                && p1.getComputador(0).getDisco() == null && p1.getComputador(1).getDisco() == null
                && p1.getComputador(0).getPlanificadorDisco() == null && p1.getComputador(0).getSistemaArchivos() == null, "");
        p1.getReloj().ejecutarCiclos(3);
        r.verificar("Proyecto 1: el reloj avanza igual", p1.getReloj().getCiclo() == 3, p1.getReloj().getCiclo() + "");
        r.verificar("el mínimo del Proyecto 1 sigue en 2", ConfiguracionSistema.MIN_COMPUTADORES == 2, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        p1.apagar();
        ConfigDisco disco = new ConfigDisco(200, 100, 50, 1, PoliticaDiscoTipo.FIFO);
        r.verificar("con disco y 2 computadores: rechazado con ERROR", conError(() -> {
            try {
                new Cluster(configuracion(2), log, disco);
                return false;
            } catch (IllegalArgumentException e) {
                return true;
            }
        }), "");
    }

    /** Guardar y volver a leer el JSON de procesos da los mismos datos. */
    private static void probarGuardarYRecargar() {
        r.grupo("Guardar y recargar el JSON de procesos");
        long errores = Debug.getErrores();
        Path carpeta;
        try {
            carpeta = Files.createTempDirectory("avilaos14");
        } catch (IOException e) {
            r.verificar("carpeta temporal", false, e.getMessage());
            return;
        }
        Path archivo = carpeta.resolve("procesos.json");
        DatosProcesos original = ArchivoProcesos.leer(RUTA_PROCESOS);
        r.verificar("se guarda el del enunciado", ArchivoProcesos.guardar(original, archivo.toString()), "");
        DatosProcesos leido = ArchivoProcesos.leer(archivo.toString());
        r.verificar("se relee igual y sin observaciones", leido.problemas().estaVacia() && iguales(original, leido),
                texto(leido.problemas()));
        String guardado = leerTexto(archivo);
        r.verificar("conserva quantum 0 y no agrega claves opcionales", guardado.contains("\"quantum\": 0")
                && !guardado.contains("headSpeed") && !guardado.contains("priority") && !guardado.contains("newName")
                && !guardado.contains("buffers") && !guardado.contains("debugger"), guardado);
        DatosProcesos completo = ArchivoProcesos.interpretar(json("\"headSpeed\":3,\"debugger\":true,", "C-SCAN",
                "ROUND_ROBIN", "4", BUENO.replace("READ", "UPDATE").replace("}", ",\"priority\":2,\"newName\":\"z\"}"))
                .replace("\"processes\"", "\"buffers\":[{\"name\":\"B1\",\"capacity\":5}],\"processes\""));
        r.verificar("con todas las opcionales se guarda y se relee igual",
                ArchivoProcesos.guardar(completo, archivo.toString())
                && iguales(completo, ArchivoProcesos.leer(archivo.toString())), leerTexto(archivo));
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.verificar("guardar datos incargables falla con ERROR", conError(
                () -> !ArchivoProcesos.guardar(ArchivoProcesos.interpretar("x"), archivo.toString())), "");
        try {
            Files.deleteIfExists(archivo);
            Files.deleteIfExists(carpeta);
        } catch (IOException e) {
            Debug.info("Pruebas", "no se borró la carpeta temporal: " + e.getMessage());
        }
    }

    /** Carga con los dos JSON del enunciado, sin espera entre ciclos que importe para las pruebas. */
    private static CargaSimulacion cargaDelEnunciado() {
        CargaSimulacion carga = new CargaSimulacion();
        DatosSistemaArchivos fs = ArchivoSistemaArchivos.leer(RUTA_FS);
        carga.usarSistemaArchivos(fs);
        carga.cargarProcesos(RUTA_PROCESOS);
        return carga;
    }

    /** Indica si el armado se rechaza con un motivo que menciona el texto. */
    private static boolean rechaza(CargaSimulacion carga, LogEventos log, String esperado) {
        CargaSimulacion.Resultado resultado = carga.armar(log);
        return !resultado.armado() && resultado.motivo() != null && resultado.motivo().contains(esperado)
                && resultado.motivo().equals(carga.motivoNoArmar());
    }

    /** Configuración del Proyecto 1 con la cantidad de computadores pedida. */
    private static ConfiguracionSistema configuracion(int cantidad) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        for (int i = 0; i < cantidad; i++) {
            computadores.agregarFinal(new ConfigComputador(256, PoliticaTipo.FCFS, 3));
        }
        return new ConfiguracionSistema(true, 0, new CondicionesParada(0, false, 1, false, 1), 1, 0, computadores,
                new ListaEnlazada<>(), new ListaEnlazada<>(), 0);
    }

    /** Arma un JSON de procesos de prueba. */
    private static String json(String opcionales, String politicaDisco, String politica, String quantum, String procesos) {
        return "{\"globalConfig\":{" + opcionales + "\"cycleDurationMs\":0,\"swapSizeBlocks\":20,\"initialDiskPolicy\":\""
                + politicaDisco + "\"},\"computer\":{\"ramCapacity\":1024,\"policy\":\"" + politica + "\",\"quantum\":"
                + quantum + "},\"processes\":[" + procesos + "]}";
    }

    /** Indica si cada política de disco se lee por su nombre. */
    private static boolean politicasReconocidas() {
        for (PoliticaDiscoTipo tipo : PoliticaDiscoTipo.values()) {
            DatosProcesos d = ArchivoProcesos.interpretar(json("", tipo.getNombre(), "FCFS", "0", BUENO));
            if (d.politicaDisco() != tipo || !d.problemas().estaVacia()) {
                return false;
            }
        }
        return true;
    }

    /** Compara dos lecturas campo por campo, incluidas las listas. */
    private static boolean iguales(DatosProcesos a, DatosProcesos b) {
        if (!a.cargable() || !b.cargable() || a.duracionCicloMs() != b.duracionCicloMs() || a.bloquesSwap() != b.bloquesSwap()
                || a.politicaDisco() != b.politicaDisco() || a.velocidadCabezal() != b.velocidadCabezal()
                || a.ram() != b.ram() || a.politica() != b.politica() || a.quantum() != b.quantum()
                || (a.debugger() == null ? b.debugger() != null : !a.debugger().equals(b.debugger()))
                || a.procesos().tamano() != b.procesos().tamano() || a.buffers().tamano() != b.buffers().tamano()) {
            return false;
        }
        Nodo<DatosProcesoArchivo> p = a.procesos().getCabeza();
        Nodo<DatosProcesoArchivo> q = b.procesos().getCabeza();
        while (p != null) {
            if (!p.getDato().equals(q.getDato())) {
                return false;
            }
            p = p.getSiguiente();
            q = q.getSiguiente();
        }
        Nodo<DatosBuffer> x = a.buffers().getCabeza();
        Nodo<DatosBuffer> y = b.buffers().getCabeza();
        while (x != null) {
            if (!x.getDato().equals(y.getDato())) {
                return false;
            }
            x = x.getSiguiente();
            y = y.getSiguiente();
        }
        return true;
    }

    /** Indica si algún problema contiene el texto. */
    private static boolean contiene(ListaEnlazada<String> problemas, String texto) {
        Nodo<String> nodo = problemas.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().contains(texto)) {
                return true;
            }
            nodo = nodo.getSiguiente();
        }
        return false;
    }

    /** Une los problemas en un texto para el detalle de un fallo. */
    private static String texto(ListaEnlazada<String> problemas) {
        StringBuilder sb = new StringBuilder();
        Nodo<String> nodo = problemas.getCabeza();
        while (nodo != null) {
            sb.append("\n            - ").append(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Contenido de un archivo de texto, o "" si no se puede leer. */
    private static String leerTexto(Path ruta) {
        try {
            return Files.readString(ruta);
        } catch (IOException e) {
            return "";
        }
    }

    /** Exige que la comprobación se cumpla y emita al menos un ERROR. */
    private static boolean conError(Comprobacion c) {
        long antes = Debug.getErrores();
        boolean resultado = c.cumple();
        return resultado && Debug.getErrores() > antes;
    }

    /** Comprobación que se ejecuta midiendo los ERROR emitidos. */
    private interface Comprobacion {

        boolean cumple();
    }
}
