package avilaos.pruebas;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.config.ArchivoProcesos;
import avilaos.config.ArchivoSistemaArchivos;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.config.DatosProcesos;
import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoCPU;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.Permiso;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoInstruccion;
import avilaos.enums.TipoOperacionES;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.DatosProcesoArchivo;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.so.disco.SolicitudDisco;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pruebas de terminal de la etapa 15: procesos de E/S de archivos conectados al Kernel. */
public final class PruebasPaso15 {

    private static final String RUTA_FS = "config/sistema_archivos_enunciado.json";
    private static final String RUTA_PROCESOS = "config/procesos_enunciado.json";
    private static final Usuario ADMIN = Usuario.ADMIN;
    private static final Usuario USER1 = Usuario.comun("user1");
    private static final Usuario USER2 = Usuario.comun("user2");
    private static final int LIBRES_INICIALES = 192;
    private static final String NOTAS = "/usuarios/notas.txt";
    private static final String VIDEO = "/usuarios/video.mp4";

    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso15() {
    }

    /** Ejecuta todas las pruebas de la etapa 15 e imprime su resumen. */
    public static void ejecutar(LogEventos eventos) {
        log = eventos;
        r = new Resultados(15, "Etapa 15: procesos de E/S de archivos");
        probarProgramaYCreacion();
        probarCrear();
        probarLeerActualizarEliminar();
        probarDirectorios();
        probarEnunciadoDePuntaAPunta();
        probarEsperaAnticipatoria();
        probarRechazos();
        probarDeadlineEnCola();
        probarDeadlineEnAtencion();
        probarConflictos();
        probarHiloDelDisco();
        probarAleatorio();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El programa termina en la solicitud al disco y el PCB guarda los datos de la operación. */
    private static void probarProgramaYCreacion() {
        r.grupo("Programa, PCB y creación del proceso");
        long errores = Debug.getErrores();
        DatosProcesoArchivo datos = new DatosProcesoArchivo("A", 16, 100, 3, "/usuarios/a.txt", TipoOperacionES.CREAR, 4, 5, null);
        PCB pcb = FabricaProcesos.crearArchivo(datos, USER1, Permiso.PRIVADO, 0, 0);
        r.verificar("es un proceso de E/S de archivos", pcb != null && pcb.getTipo() == TipoProceso.ES_ARCHIVO, "" + pcb);
        r.verificar("el PCB guarda operación, ruta, bloques, nombre nuevo y usuario", pcb.getDatosES().operacion() == TipoOperacionES.CREAR
                && pcb.getDatosES().ruta().equals("/usuarios/a.txt") && pcb.getDatosES().bloquesArchivo() == 4
                && pcb.getDatosES().nuevoNombre() == null && pcb.getDatosES().usuario().equals(USER1), "" + pcb.getDatosES());
        r.verificar("2 instrucciones de cálculo y la última es la solicitud al disco", pcb.getPrograma().totalInstrucciones() == 3
                && pcb.getPrograma().instruccionEn(0).tipo() == TipoInstruccion.CALCULO
                && pcb.getPrograma().instruccionEn(1).tipo() == TipoInstruccion.CALCULO
                && pcb.getPrograma().instruccionEn(2).tipo() == TipoInstruccion.SOLICITAR_DISCO, "");
        r.verificar("la solicitud al disco se ejecuta en modo SO y no es la del DMA",
                TipoInstruccion.SOLICITAR_DISCO.getModo() == ModoCPU.SISTEMA_OPERATIVO
                && TipoInstruccion.SOLICITAR_DISCO != TipoInstruccion.SOLICITAR_ES, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.verificar("crear con operación de intercambio es ERROR", conError(() -> FabricaProcesos.crearArchivo(
                new DatosProcesoArchivo("X", 1, 1, 1, "/a", TipoOperacionES.SUSPENDER, 0, 5, null), ADMIN, Permiso.PUBLICO, 0, 0) == null), "");
        r.verificar("el formulario del Proyecto 1 no puede crearlos", conError(() -> FabricaProcesos.crear(
                new DatosProceso("X", TipoProceso.ES_ARCHIVO, 5, 1, 1, 1, 0, 0, null, 0, 0, 0), 0) == null), "");
        Cluster p1 = new Cluster(configuracionSinDisco(), log);
        r.verificar("un computador sin disco los rechaza con ERROR",
                conError(() -> p1.crearProcesoArchivo(datos, ADMIN, Permiso.PUBLICO) == null), "");
        p1.apagar();
    }

    /** CREAR reserva al encolar y el archivo aparece cuando el disco termina. */
    private static void probarCrear() {
        r.grupo("CREAR: reserva al encolar, aparece al terminar");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 0);
        SistemaArchivos fs = fs(c);
        PCB p = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/nuevo.txt", 3, null, 500);
        boolean bloqueado = hasta(c, () -> p.getEstado() == EstadoProceso.BLOQUEADO, 20);
        r.verificar("se bloquea por E/S esperando el disco", bloqueado && p.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA
                && p.getDetalleBloqueo().startsWith("esperando el disco: solicitud #") && p.getDetalleBloqueo().endsWith("bloque 0"),
                p.toString());
        r.verificar("libera la CPU", c.getComputador(0).getKernel().getEnEjecucion() == null, "");
        r.verificar("los 3 bloques ya están reservados", libres(c) == LIBRES_INICIALES - 3 && fs.reservasPendientes() == 1, libres(c) + "");
        r.verificar("el archivo todavía no aparece en el árbol", !fs.existe("/usuarios/nuevo.txt"), "");
        r.verificar("un bloque reservado se identifica con su ruta futura",
                "/usuarios/nuevo.txt".equals(fs.rutaDeBloque(disco(c).leer(0).dueno())), "" + disco(c).leer(0));
        r.verificar("íntegro con la reserva pendiente", integro(c), "");
        boolean atendida = hasta(c, () -> planificador(c).getAtendidas() == 1, 100);
        InfoEntrada e = fs.buscar("/usuarios/nuevo.txt");
        r.verificar("al terminar el disco aparece, en los bloques 0, 1 y 2", atendida && e != null && e.bloques() == 3
                && e.primerBloque() == 0 && fs.bloquesDe(e.ruta())[2] == 2, "" + e);
        r.verificar("es del admin, público y creado por el proceso", e != null && e.dueno().equals("admin")
                && e.permiso() == Permiso.PUBLICO && e.creador().equals(p.getEtiqueta()), "" + e);
        r.verificar("vuelve a Listos antes de terminar", p.getEstado() != EstadoProceso.TERMINADO
                && p.getSolicitudPendiente() == null && enLog(p.getEtiqueta() + " se desbloquea (Fin de disco) y pasa a Listos"),
                p.toString());
        r.verificar("al volver a ejecutarse termina bien", terminaTodo(c) && !p.isDeadlineVencido() && p.getMotivoRechazo() == null, p.toString());
        r.verificar("el log cuenta cada paso", enLog(p.getEtiqueta() + " pide al sistema: Crear /usuarios/nuevo.txt")
                && enLog(p.getEtiqueta() + " solicita al disco") && enLog("El disco atiende la solicitud de " + p.getEtiqueta())
                && enLog("El disco termina la solicitud de " + p.getEtiqueta())
                && enLog("Cambio aplicado por " + p.getEtiqueta() + ": aparece /usuarios/nuevo.txt"), "");
        r.verificar("sin reservas, íntegro y cero ERROR", fs.reservasPendientes() == 0 && integro(c) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** LEER y escribir solo cuestan disco; renombrar y eliminar se aplican al terminar. */
    private static void probarLeerActualizarEliminar() {
        r.grupo("LEER, ACTUALIZAR y ELIMINAR");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 0);
        SistemaArchivos fs = fs(c);

        PCB lectura = pedir(c, USER1, TipoOperacionES.LEER, NOTAS, 0, null, 500);
        hasta(c, () -> lectura.getEstado() == EstadoProceso.BLOQUEADO, 20);
        r.verificar("leer: nada cambia mientras espera", fs.buscar(NOTAS).ultimaOperacion().equals("Crear"), "");
        r.verificar("leer: viaja del 50 al 34 y lee 1 bloque", terminaTodo(c) && disco(c).getCabezal() == 34
                && disco(c).getDesplazamientoTotal() == 16 && disco(c).getCiclosTransfiriendo() == 1, disco(c).getCabezal() + "");
        r.verificar("leer: queda registrada la operación", fs.buscar(NOTAS).ultimaOperacion().equals("Leer")
                && lectura.getMotivoRechazo() == null, "");

        PCB escritura = pedir(c, USER1, TipoOperacionES.ACTUALIZAR, NOTAS, 0, null, 500);
        r.verificar("actualizar sin nombre: solo cuesta disco y queda registrado", terminaTodo(c)
                && fs.buscar(NOTAS).ultimaOperacion().equals("Actualizar") && libres(c) == LIBRES_INICIALES
                && disco(c).getCiclosTransfiriendo() == 2 && escritura.getMotivoRechazo() == null, "");

        InfoEntrada antes = fs.buscar(NOTAS);
        PCB renombre = pedir(c, ADMIN, TipoOperacionES.ACTUALIZAR, NOTAS, 0, "apuntes.txt", 500);
        hasta(c, () -> renombre.getEstado() == EstadoProceso.BLOQUEADO, 20);
        r.verificar("renombrar: el nombre viejo sigue mientras espera", fs.existe(NOTAS) && !fs.existe("/usuarios/apuntes.txt"), "");
        r.verificar("renombrar: al terminar cambia el nombre y conserva id y bloques", terminaTodo(c) && !fs.existe(NOTAS)
                && fs.buscar("/usuarios/apuntes.txt").id() == antes.id()
                && fs.buscar("/usuarios/apuntes.txt").primerBloque() == antes.primerBloque(), "");

        PCB borrado = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, VIDEO, 0, null, 500);
        hasta(c, () -> borrado.getEstado() == EstadoProceso.BLOQUEADO, 20);
        r.verificar("eliminar: el archivo y sus bloques siguen mientras espera", fs.existe(VIDEO) && disco(c).leer(180).isOcupado(), "");
        r.verificar("eliminar: al terminar desaparece y libera el bloque 180", terminaTodo(c) && !fs.existe(VIDEO)
                && !disco(c).leer(180).isOcupado() && libres(c) == LIBRES_INICIALES + 1, libres(c) + "");

        PCB grande = pedir(c, ADMIN, TipoOperacionES.CREAR, "/sistema/grande.bin", 5, null, 500);
        terminaTodo(c);
        long transferidos = disco(c).getCiclosTransfiriendo();
        pedir(c, ADMIN, TipoOperacionES.LEER, "/sistema/grande.bin", 0, null, 500);
        r.verificar("leer un archivo de 5 bloques transfiere 5, uno por ciclo", terminaTodo(c)
                && disco(c).getCiclosTransfiriendo() == transferidos + 5 && grande.getMotivoRechazo() == null, "");
        r.verificar("íntegro y cero ERROR", integro(c) && Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Las operaciones sin bloques se hacen en la llamada; borrar un directorio con archivos pasa por el disco. */
    private static void probarDirectorios() {
        r.grupo("Directorios");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 0);
        SistemaArchivos fs = fs(c);
        PCB crear = pedir(c, ADMIN, TipoOperacionES.CREAR, "/tmp", 0, null, 100);
        r.verificar("crear un directorio lo hace un proceso, sin usar el disco", terminaTodo(c) && fs.existe("/tmp")
                && fs.buscar("/tmp").esDirectorio() && planificador(c).getAtendidas() == 0 && crear.getMotivoRechazo() == null, "");
        pedir(c, ADMIN, TipoOperacionES.ACTUALIZAR, "/tmp", 0, "temporal", 100);
        r.verificar("renombrarlo tampoco usa el disco", terminaTodo(c) && fs.existe("/temporal") && planificador(c).getAtendidas() == 0, "");
        pedir(c, ADMIN, TipoOperacionES.ELIMINAR, "/temporal", 0, null, 100);
        r.verificar("eliminarlo vacío tampoco", terminaTodo(c) && !fs.existe("/temporal") && planificador(c).getAtendidas() == 0, "");
        PCB borrar = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, "/sistema", 0, null, 500);
        hasta(c, () -> borrar.getEstado() == EstadoProceso.BLOQUEADO, 20);
        SolicitudDisco solicitud = planificador(c).copiarCola().primero() != null ? planificador(c).copiarCola().primero()
                : planificador(c).getActual();
        r.verificar("eliminar /sistema pide al disco sus 4 bloques", solicitud != null && solicitud.cantidadBloques() == 4
                && fs.existe("/sistema/boot.bin"), "" + solicitud);
        r.verificar("al terminar desaparece con todo y libera 4 bloques", terminaTodo(c) && !fs.existe("/sistema")
                && libres(c) == LIBRES_INICIALES + 4, libres(c) + "");
        r.verificar("íntegro y cero ERROR", integro(c) && Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Los dos JSON del enunciado, de punta a punta, dan los órdenes de los casos P1 a P4. */
    private static void probarEnunciadoDePuntaAPunta() {
        r.grupo("JSON del enunciado de punta a punta (P1 a P4)");
        String[][] casos = {
            {"FIFO", "95 180 34 119 11 123 62 64"},
            {"SSTF", "62 64 34 11 95 119 123 180"},
            {"SCAN", "62 64 95 119 123 180 34 11"},
            {"C-SCAN", "62 64 95 119 123 180 11 34"}
        };
        for (String[] caso : casos) {
            long errores = Debug.getErrores();
            Cluster c = enunciado(caso[0], ArchivoProcesos.ANTICIPACION_POR_DEFECTO);
            boolean fin = terminaTodo(c);
            String orden = orden(c);
            r.verificar(caso[0] + ": orden " + caso[1], orden.equals(caso[1]), orden);
            int bien = 0;
            Nodo<PCB> nodo = c.todosLosProcesos().getCabeza();
            while (nodo != null) {
                PCB p = nodo.getDato();
                bien += p.getEstado() == EstadoProceso.TERMINADO && !p.isDeadlineVencido() && p.getMotivoRechazo() == null ? 1 : 0;
                nodo = nodo.getSiguiente();
            }
            r.verificar(caso[0] + ": los 8 terminan bien, sin vencer su deadline", fin && bien == 8, bien + " de 8");
            r.verificar(caso[0] + ": img1.png se eliminó y todo queda íntegro", !fs(c).existe("/usuarios/img1.png")
                    && libres(c) == LIBRES_INICIALES + 1 && fs(c).reservasPendientes() == 0 && integro(c), libres(c) + "");
            r.verificar(caso[0] + ": el log muestra la espera y cero ERROR",
                    enLog("El disco espera 18 ciclos anticipando solicitudes (" + caso[0] + ")") && Debug.getErrores() == errores,
                    (Debug.getErrores() - errores) + "");
            r.seccion(" " + caso[0] + ": " + orden.replace(" ", " → ") + " | desplazamiento " + disco(c).getDesplazamientoTotal()
                    + " | espera media " + String.format("%.1f", planificador(c).getEsperaPromedio()) + " | fin en el ciclo "
                    + c.getReloj().getCiclo());
            c.apagar();
        }
        for (int i = 1; i < casos.length; i++) {
            Cluster c = enunciado(casos[i][0], ArchivoProcesos.ANTICIPACION_POR_DEFECTO - 1);
            terminaTodo(c);
            r.verificar(casos[i][0] + ": con 17 ciclos de espera el orden ya no sale (18 es el mínimo)",
                    !orden(c).equals(casos[i][1]), orden(c));
            c.apagar();
        }
    }

    /** La ventana de espera: duración, reinicio, cancelación, cambio de política y su clave en el JSON. */
    private static void probarEsperaAnticipatoria() {
        r.grupo("Espera anticipatoria y su configuración");
        long errores = Debug.getErrores();
        DiscoSD d = new DiscoSD("SD-prueba", 200, 0, 50, 1);
        PlanificadorDisco plan = new PlanificadorDisco("Plan-prueba", d, PoliticaDiscoTipo.FIFO, new LogEventos(50), 3);
        encolar(plan, 1, 60);
        ticks(d, 1, 1);
        encolar(plan, 2, 40);
        ticks(d, 2, 2);
        r.verificar("espera 3 ciclos sin mover el cabezal; una llegada no reinicia la ventana", d.getCabezal() == 50
                && d.getCiclosOciosos() == 3 && plan.getActual() == null, d.getCiclosOciosos() + "");
        ticks(d, 4, 1);
        r.verificar("en el cuarto ciclo elige", plan.getActual() != null && d.getCabezal() == 51, d.getCabezal() + "");
        ticks(d, 5, 10);
        boolean terminoPrimera = plan.getAtendidas() == 1 && d.getCabezal() == 60;
        ticks(d, 15, 1);
        r.verificar("con trabajo en cola no vuelve a esperar: sigue en el ciclo siguiente", terminoPrimera
                && d.getCabezal() == 59 && d.getCiclosOciosos() == 3, d.getCabezal() + " / " + d.getCiclosOciosos());
        ticks(d, 16, 25);
        long ociosos = d.getCiclosOciosos();
        encolar(plan, 3, 45);
        ticks(d, 45, 3);
        r.verificar("con el disco libre otra vez, la siguiente solicitud vuelve a esperar", plan.getActual() == null
                && d.getCiclosOciosos() == ociosos + 3, "");
        r.verificar("cancelar en cola la quita y reinicia la ventana", plan.cancelar(3, 48) != null && plan.estaInactivo(), "");
        encolar(plan, 4, 45);
        ociosos = d.getCiclosOciosos();
        ticks(d, 49, 3);
        r.verificar("después de cancelar la espera es completa", plan.getActual() == null && d.getCiclosOciosos() == ociosos + 3, "");
        ticks(d, 52, 1);
        r.verificar("no se cancela lo que ya se atiende ni lo que no existe", plan.cancelar(4, 52) == null
                && plan.cancelar(99, 52) == null, "");

        DiscoSD d2 = new DiscoSD("SD-prueba2", 200, 0, 50, 1);
        PlanificadorDisco plan2 = new PlanificadorDisco("Plan-prueba2", d2, PoliticaDiscoTipo.FIFO, new LogEventos(50), 2);
        encolar(plan2, 1, 90);
        encolar(plan2, 2, 52);
        ticks(d2, 1, 1);
        plan2.cambiarPolitica(PoliticaDiscoTipo.SSTF, 1);
        ticks(d2, 2, 2);
        r.verificar("un cambio de política durante la espera se aplica al elegir", plan2.getActual() != null
                && plan2.getActual().destino() == 52, "" + plan2.getActual());
        DiscoSD d3 = new DiscoSD("SD-prueba3", 200, 0, 50, 1);
        PlanificadorDisco plan3 = new PlanificadorDisco("Plan-prueba3", d3, PoliticaDiscoTipo.FIFO, new LogEventos(50), 0);
        encolar(plan3, 1, 60);
        ticks(d3, 1, 1);
        r.verificar("con 0 se desactiva: se mueve en el primer ciclo", d3.getCabezal() == 51, d3.getCabezal() + "");

        String base = leerTexto(Path.of(RUTA_PROCESOS));
        DatosProcesos cero = ArchivoProcesos.interpretar(conEspera(base, "0"));
        DatosProcesos cinco = ArchivoProcesos.interpretar(conEspera(base, "5"));
        r.verificar("anticipationCycles se lee (0 y 5)", cero.ciclosAnticipacion() == 0 && cinco.ciclosAnticipacion() == 5
                && cero.problemas().estaVacia() && cinco.problemas().estaVacia(), "");
        DatosProcesos malo = ArchivoProcesos.interpretar(conEspera(base, "-1"));
        r.verificar("un valor inválido se reporta y se usa 18", malo.ciclosAnticipacion() == 18 && malo.problemas().tamano() == 1, "");
        r.verificar("guardar conserva la clave y al releer da lo mismo", guardadoYReleido(cinco) == 5 && guardadoYReleido(cero) == 0, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.verificar("crear el planificador con una espera negativa es ERROR", conError(() -> new PlanificadorDisco("Plan-malo",
                new DiscoSD("SD-malo", 20, 0, 0, 1), PoliticaDiscoTipo.FIFO, new LogEventos(5), -3).getAnticipacion() == 0), "");
    }

    /** Lo que no se puede hacer termina el proceso con su motivo y no llega al disco. */
    private static void probarRechazos() {
        r.grupo("Rechazos: permiso, inexistente, nombres y espacio");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 0);
        rechazo(c, "leer un privado ajeno", USER2, TipoOperacionES.LEER, NOTAS, 0, null, "privado");
        rechazo(c, "escribir un archivo ajeno", USER1, TipoOperacionES.ACTUALIZAR, "/usuarios/data.csv", 0, null, "sus propios archivos");
        rechazo(c, "eliminar un archivo ajeno", USER2, TipoOperacionES.ELIMINAR, VIDEO, 0, null, "sus propios archivos");
        rechazo(c, "renombrar sin ser admin", USER1, TipoOperacionES.ACTUALIZAR, NOTAS, 0, "otro.txt", "solo el administrador");
        rechazo(c, "crear en un directorio ajeno", USER1, TipoOperacionES.CREAR, "/sistema/x.txt", 1, null, "sus propios directorios");
        rechazo(c, "crear un directorio sin ser admin", USER1, TipoOperacionES.CREAR, "/usuarios/dir", 0, null, "solo el administrador");
        rechazo(c, "leer lo que no existe", ADMIN, TipoOperacionES.LEER, "/nada.txt", 0, null, "no existe");
        rechazo(c, "eliminar lo que no existe", ADMIN, TipoOperacionES.ELIMINAR, "/sistema/nada", 0, null, "no existe");
        rechazo(c, "crear en un directorio inexistente", ADMIN, TipoOperacionES.CREAR, "/no/x.txt", 1, null, "no existe");
        rechazo(c, "crear lo que ya existe", ADMIN, TipoOperacionES.CREAR, NOTAS, 1, null, "ya existe");
        rechazo(c, "renombrar a un nombre ocupado", ADMIN, TipoOperacionES.ACTUALIZAR, NOTAS, 0, "data.csv", "ya existe");
        rechazo(c, "leer un directorio", ADMIN, TipoOperacionES.LEER, "/sistema", 0, null, "es un directorio");
        rechazo(c, "escribir un directorio", ADMIN, TipoOperacionES.ACTUALIZAR, "/sistema", 0, null, "es un directorio");
        rechazo(c, "eliminar la raíz", ADMIN, TipoOperacionES.ELIMINAR, "/", 0, null, "raíz");
        rechazo(c, "crear sin espacio suficiente", ADMIN, TipoOperacionES.CREAR, "/enorme.bin", LIBRES_INICIALES + 1, null,
                "no hay bloques libres suficientes");
        r.verificar("ningún rechazo es ERROR y todo queda íntegro", Debug.getErrores() == errores && integro(c),
                (Debug.getErrores() - errores) + "");
        PCB justo = pedir(c, ADMIN, TipoOperacionES.CREAR, "/justo.bin", LIBRES_INICIALES, null, 900);
        r.verificar("crear usando exactamente lo libre sí se puede", terminaTodo(c) && justo.getMotivoRechazo() == null
                && libres(c) == 0 && integro(c), "" + justo.getMotivoRechazo());
        c.apagar();
    }

    /** Un deadline que vence con la solicitud en cola la cancela y deshace la reserva. */
    private static void probarDeadlineEnCola() {
        r.grupo("Deadline vencido con la solicitud en cola");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 100);
        PCB crear = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/tarde.bin", 4, null, 10);
        PCB borrar = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, VIDEO, 0, null, 12);
        boolean fin = terminaTodo(c);
        r.verificar("ambos terminan por deadline", fin && crear.isDeadlineVencido() && borrar.isDeadlineVencido()
                && crear.getCicloFin() == 10 && borrar.getCicloFin() == 12, crear.getCicloFin() + " / " + borrar.getCicloFin());
        r.verificar("sus solicitudes se cancelan y el disco no atiende ninguna", planificador(c).estaInactivo()
                && planificador(c).getAtendidas() == 0 && enLog("El disco cancela la solicitud en cola de " + crear.getEtiqueta()), "");
        r.verificar("CREAR libera los bloques reservados y el archivo no aparece", fs(c).reservasPendientes() == 0
                && libres(c) == LIBRES_INICIALES && !fs(c).existe("/usuarios/tarde.bin"), libres(c) + "");
        r.verificar("ELIMINAR cancelado deja el archivo", fs(c).existe(VIDEO), "");
        r.verificar("liberan la RAM", c.getComputador(0).getRam().getUsada() == 0, c.getComputador(0).getRam().getUsada() + "");
        PCB antes = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/nunca.bin", 2, null, 2);
        r.verificar("si vence antes de la llamada, nada llega al disco", terminaTodo(c) && antes.isDeadlineVencido()
                && libres(c) == LIBRES_INICIALES && planificador(c).estaInactivo(), "");
        r.verificar("íntegro y cero ERROR", integro(c) && Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Si el disco ya la atiende, la solicitud acaba, el cambio se aplica y el proceso termina por deadline. */
    private static void probarDeadlineEnAtencion() {
        r.grupo("Deadline vencido durante la atención");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 0);
        PCB borrar = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, VIDEO, 0, null, 10);
        PCB enCola = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/cola.bin", 2, null, 20);
        hasta(c, () -> c.getReloj().getCiclo() >= 15, 20);
        r.verificar("sigue Bloqueado, marcado y con su RAM", borrar.getEstado() == EstadoProceso.BLOQUEADO && borrar.isVencidoEnDisco()
                && c.getComputador(0).getRam().getUsada() == 32, borrar.toString());
        r.verificar("el cambio no se aplica antes de tiempo", fs(c).existe(VIDEO), "");
        r.verificar("se avisa una sola vez",
                veces(borrar.getEtiqueta() + " alcanza su deadline mientras el disco atiende") == 1, "");
        boolean fin = terminaTodo(c);
        r.verificar("el disco termina, se elimina y el proceso acaba por deadline", fin && !fs(c).existe(VIDEO)
                && borrar.isDeadlineVencido() && borrar.getCicloFin() > 60, borrar.getCicloFin() + "");
        r.verificar("la que esperaba detrás en cola se cancela y libera su reserva", enCola.isDeadlineVencido()
                && enCola.getCicloFin() == 20 && !fs(c).existe("/usuarios/cola.bin") && fs(c).reservasPendientes() == 0
                && planificador(c).getAtendidas() == 1, enCola.getCicloFin() + "");
        PCB crear = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/lento.bin", 40, null, 20);
        r.verificar("CREAR en atención: el archivo aparece y luego termina por deadline", terminaTodo(c) && crear.isDeadlineVencido()
                && fs(c).existe("/usuarios/lento.bin") && fs(c).reservasPendientes() == 0, crear.toString());
        r.verificar("sin RAM retenida, íntegro y cero ERROR", c.getComputador(0).getRam().getUsada() == 0 && integro(c)
                && Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Solicitudes válidas al pedirse que chocan al terminar: se identifican por id y fallan con motivo. */
    private static void probarConflictos() {
        r.grupo("Conflictos entre solicitudes pendientes");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 40);
        PCB primero = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, VIDEO, 0, null, 900);
        PCB segundo = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, VIDEO, 0, null, 900);
        r.verificar("dos eliminaciones: la primera borra y la segunda termina con motivo", terminaTodo(c)
                && primero.getMotivoRechazo() == null && contiene(segundo.getMotivoRechazo(), "ya no existe")
                && !segundo.isDeadlineVencido() && integro(c), "" + segundo.getMotivoRechazo());

        PCB renombre = pedir(c, ADMIN, TipoOperacionES.ACTUALIZAR, NOTAS, 0, "apuntes.txt", 900);
        PCB lectura = pedir(c, ADMIN, TipoOperacionES.LEER, NOTAS, 0, null, 900);
        r.verificar("renombrar no invalida una lectura pendiente (va por id)", terminaTodo(c) && renombre.getMotivoRechazo() == null
                && lectura.getMotivoRechazo() == null && fs(c).buscar("/usuarios/apuntes.txt").ultimaOperacion().equals("Leer"),
                "" + lectura.getMotivoRechazo());

        PCB uno = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/igual.txt", 1, null, 900);
        PCB dos = pedir(c, ADMIN, TipoOperacionES.CREAR, "/usuarios/igual.txt", 1, null, 900);
        PCB tres = pedir(c, ADMIN, TipoOperacionES.ACTUALIZAR, "/usuarios/data.csv", 0, "igual.txt", 900);
        r.verificar("un nombre reservado no se puede crear ni usar al renombrar", terminaTodo(c) && uno.getMotivoRechazo() == null
                && contiene(dos.getMotivoRechazo(), "creación pendiente") && contiene(tres.getMotivoRechazo(), "creación pendiente"),
                dos.getMotivoRechazo() + " / " + tres.getMotivoRechazo());

        pedir(c, ADMIN, TipoOperacionES.CREAR, "/carpeta", 0, null, 900);
        terminaTodo(c);
        int libresAntes = libres(c);
        PCB dentro = pedir(c, ADMIN, TipoOperacionES.CREAR, "/carpeta/a.bin", 6, null, 900);
        PCB borrarCarpeta = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, "/carpeta", 0, null, 900);
        r.verificar("borrar la carpeta destino: el CREAR falla y libera su reserva", terminaTodo(c)
                && borrarCarpeta.getMotivoRechazo() == null && contiene(dentro.getMotivoRechazo(), "ya no existe")
                && libres(c) == libresAntes && fs(c).reservasPendientes() == 0 && !fs(c).existe("/carpeta"),
                "" + dentro.getMotivoRechazo());

        pedir(c, ADMIN, TipoOperacionES.CREAR, "/vieja", 0, null, 900);
        terminaTodo(c);
        PCB sigue = pedir(c, ADMIN, TipoOperacionES.CREAR, "/vieja/b.bin", 2, null, 900);
        pedir(c, ADMIN, TipoOperacionES.ACTUALIZAR, "/vieja", 0, "nueva", 900);
        r.verificar("si la carpeta solo cambia de nombre, el archivo aparece en ella", terminaTodo(c)
                && sigue.getMotivoRechazo() == null && fs(c).existe("/nueva/b.bin"), "" + sigue.getMotivoRechazo());
        r.verificar("íntegro y cero ERROR", integro(c) && Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** El hilo del disco solo deja la interrupción; el cambio lo hace el Kernel en su ciclo. */
    private static void probarHiloDelDisco() {
        r.grupo("El fin del disco llega por interrupción");
        long errores = Debug.getErrores();
        Cluster c = sistema(PoliticaDiscoTipo.FIFO, 1000);
        Computador pc = c.getComputador(0);
        PCB p = pedir(c, ADMIN, TipoOperacionES.ELIMINAR, VIDEO, 0, null, 5000);
        hasta(c, () -> p.getEstado() == EstadoProceso.BLOQUEADO, 20);
        long ciclo = c.getReloj().getCiclo();
        for (int i = 0; i < 1300 && planificador(c).getAtendidas() == 0; i++) {
            disco(c).tick(++ciclo);
        }
        r.verificar("tras terminar el disco queda una interrupción pendiente",
                planificador(c).getAtendidas() == 1 && pc.getLinea().cantidadPendientes() == 1, pc.getLinea().cantidadPendientes() + "");
        r.verificar("el hilo del disco no toca ni el proceso ni el árbol", p.getEstado() == EstadoProceso.BLOQUEADO
                && fs(c).existe(VIDEO), p.toString());
        c.getReloj().ejecutarCiclos(1);
        r.verificar("el Kernel la atiende en su ciclo y aplica el cambio", !fs(c).existe(VIDEO)
                && p.getEstado() != EstadoProceso.BLOQUEADO && pc.getLinea().cantidadPendientes() == 0, p.toString());
        r.verificar("cero ERROR", terminaTodo(c) && Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Muchas operaciones al azar, con deadlines cortos y cambios de política: nada se pierde ni se corrompe. */
    private static void probarAleatorio() {
        r.grupo("Aleatorio: operaciones, deadlines y cambios de política");
        long errores = Debug.getErrores();
        int rotos = 0;
        int sinTerminar = 0;
        int total = 0;
        int vencidos = 0;
        int rechazados = 0;
        for (int semilla = 1; semilla <= 6; semilla++) {
            Azar azar = new Azar(semilla);
            Cluster c = sistema(PoliticaDiscoTipo.FIFO, semilla % 2 == 0 ? 6 : 0);
            Usuario[] usuarios = {ADMIN, ADMIN, ADMIN, ADMIN, USER1, USER2};
            String[] nombres = {"a", "b", "c", "d", "e", "f", "g", "h"};
            TipoOperacionES[] operaciones = {TipoOperacionES.CREAR, TipoOperacionES.CREAR, TipoOperacionES.LEER,
                TipoOperacionES.ACTUALIZAR, TipoOperacionES.ELIMINAR};
            for (int paso = 0; paso < 600; paso++) {
                if (azar.entre(0, 8) == 0) {
                    TipoOperacionES op = operaciones[azar.entre(0, operaciones.length - 1)];
                    ListaEnlazada<InfoEntrada> entradas = fs(c).listarTodo();
                    InfoEntrada elegida = entradas.obtener(azar.entre(0, entradas.tamano() - 1));
                    String nombre = nombres[azar.entre(0, nombres.length - 1)];
                    String ruta = elegida.ruta();
                    int bloques = 0;
                    if (op == TipoOperacionES.CREAR) {
                        String padre = elegida.esDirectorio() ? elegida.ruta() : SistemaArchivos.padreDe(elegida.ruta());
                        ruta = (padre.equals("/") ? "" : padre) + "/" + nombre;
                        bloques = azar.entre(0, 6) == 0 ? 0 : azar.entre(1, 30);
                    } else if (azar.entre(0, 9) == 0) {
                        ruta = "/no/" + nombre;
                    }
                    String nuevo = op == TipoOperacionES.ACTUALIZAR && azar.entre(0, 1) == 0 ? nombre : null;
                    int deadline = azar.entre(0, 4) == 0 ? azar.entre(3, 60) : azar.entre(200, 1500);
                    pedir(c, usuarios[azar.entre(0, usuarios.length - 1)], op, ruta, bloques, nuevo, deadline);
                }
                if (azar.entre(0, 40) == 0) {
                    planificador(c).cambiarPolitica(PoliticaDiscoTipo.values()[azar.entre(0, 5)], c.getReloj().getCiclo());
                }
                c.getReloj().ejecutarCiclos(1);
                if (paso % 25 == 0 && !integro(c)) {
                    rotos++;
                }
            }
            if (!terminaTodo(c)) {
                sinTerminar++;
            }
            if (!integro(c) || fs(c).reservasPendientes() != 0 || !planificador(c).estaInactivo()
                    || c.getComputador(0).getRam().getUsada() != 0) {
                rotos++;
            }
            Nodo<PCB> nodo = c.todosLosProcesos().getCabeza();
            while (nodo != null) {
                total++;
                vencidos += nodo.getDato().isDeadlineVencido() ? 1 : 0;
                rechazados += nodo.getDato().getMotivoRechazo() != null ? 1 : 0;
                nodo = nodo.getSiguiente();
            }
            c.apagar();
        }
        r.verificar("todos los procesos terminan", sinTerminar == 0 && total > 300,sinTerminar + " escenarios / " + total);
        r.verificar("hubo muchos éxitos, rechazos y deadlines vencidos", vencidos > 20 && rechazados > 20
                && total - vencidos - rechazados > total / 4, total + " / " + vencidos + " / " + rechazados);
        r.verificar("disco, árbol, reservas, cola y RAM íntegros", rotos == 0, rotos + " comprobaciones rotas");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Aleatorio: " + total + " procesos, " + rechazados + " con operación rechazada, " + vencidos + " por deadline");
    }

    // ---------------------------------------------------------------- ayudantes

    /** Sistema con el árbol del enunciado, cabezal a 1 por ciclo y la política y espera indicadas; sin procesos. */
    private static Cluster sistema(PoliticaDiscoTipo politica, int espera) {
        String texto = "{\"globalConfig\":{\"cycleDurationMs\":0,\"swapSizeBlocks\":100,\"initialDiskPolicy\":\""
                + politica.getNombre() + "\",\"headSpeed\":1,\"anticipationCycles\":" + espera + "},"
                + "\"computer\":{\"ramCapacity\":4096,\"policy\":\"FCFS\",\"quantum\":0},\"processes\":[]}";
        return armar(ArchivoProcesos.interpretar(texto));
    }

    /** Sistema con los dos JSON del enunciado, cambiando solo la política de disco y la espera, con sus 8 procesos. */
    private static Cluster enunciado(String politica, int espera) {
        String texto = leerTexto(Path.of(RUTA_PROCESOS)).replace("\"FIFO\"", "\"" + politica + "\"");
        if (espera != ArchivoProcesos.ANTICIPACION_POR_DEFECTO) {
            texto = conEspera(texto, String.valueOf(espera));
        }
        DatosProcesos datos = ArchivoProcesos.interpretar(texto);
        Cluster c = armar(datos);
        c.cargarProcesosArchivo(datos.procesos());
        return c;
    }

    private static Cluster armar(DatosProcesos datos) {
        CargaSimulacion carga = new CargaSimulacion();
        carga.usarSistemaArchivos(ArchivoSistemaArchivos.leer(RUTA_FS));
        carga.usarProcesos(datos);
        return carga.armar(log).cluster();
    }

    /** Agrega la clave de la espera anticipatoria a un JSON de procesos. */
    private static String conEspera(String texto, String valor) {
        return texto.replace("\"swapSizeBlocks\"", "\"anticipationCycles\": " + valor + ", \"swapSizeBlocks\"");
    }

    private static ConfiguracionSistema configuracionSinDisco() {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(256, PoliticaTipo.FCFS, 3));
        return new ConfiguracionSistema(true, 0, new CondicionesParada(0, false, 1, false, 1), 1, 0, computadores,
                new ListaEnlazada<>(), new ListaEnlazada<>(), 0);
    }

    /** Crea un proceso de 2 instrucciones que pide la operación. */
    private static PCB pedir(Cluster c, Usuario usuario, TipoOperacionES operacion, String ruta, int bloques, String nuevo,
            int deadline) {
        return c.crearProcesoArchivo(new DatosProcesoArchivo("es", 16, deadline, 2, ruta, operacion, bloques, 5, nuevo),
                usuario, Permiso.PUBLICO);
    }

    /** Comprueba que la operación se rechaza con ese motivo, sin llegar al disco ni cambiar nada. */
    private static void rechazo(Cluster c, String caso, Usuario usuario, TipoOperacionES operacion, String ruta, int bloques,
            String nuevo, String motivo) {
        int libresAntes = libres(c);
        int entradas = fs(c).listarTodo().tamano();
        long desplazamiento = disco(c).getDesplazamientoTotal();
        PCB p = pedir(c, usuario, operacion, ruta, bloques, nuevo, 100);
        boolean fin = terminaTodo(c);
        r.verificar(caso + ": termina con el motivo y no llega al disco", fin && !p.isDeadlineVencido()
                && contiene(p.getMotivoRechazo(), motivo) && planificador(c).estaInactivo() && planificador(c).getAtendidas() == 0
                && disco(c).getDesplazamientoTotal() == desplazamiento && libres(c) == libresAntes
                && fs(c).listarTodo().tamano() == entradas && enLog("Se rechaza la operación de " + p.getEtiqueta()),
                "" + p.getMotivoRechazo());
    }

    /** Ejecuta ciclos de uno en uno hasta que se cumpla la condición. */
    private static boolean hasta(Cluster c, Comprobacion condicion, int maximo) {
        for (int i = 0; i < maximo && !condicion.cumple(); i++) {
            c.getReloj().ejecutarCiclos(1);
        }
        return condicion.cumple();
    }

    private static boolean terminaTodo(Cluster c) {
        return hasta(c, c::todosTerminados, 20_000);
    }

    private static void encolar(PlanificadorDisco plan, int proceso, int bloque) {
        plan.encolar(proceso, "P" + proceso, TipoOperacionES.LEER, "/x", new int[]{bloque}, 0);
    }

    private static void ticks(DiscoSD disco, long desde, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            disco.tick(desde + i);
        }
    }

    private static SistemaArchivos fs(Cluster c) {
        return c.getComputador(0).getSistemaArchivos();
    }

    private static DiscoSD disco(Cluster c) {
        return c.getComputador(0).getDisco();
    }

    private static PlanificadorDisco planificador(Cluster c) {
        return c.getComputador(0).getPlanificadorDisco();
    }

    private static int libres(Cluster c) {
        return disco(c).contarLibres(false);
    }

    private static boolean integro(Cluster c) {
        return disco(c).verificarIntegridad() && fs(c).verificarIntegridad();
    }

    /** Destinos atendidos, en orden. */
    private static String orden(Cluster c) {
        StringBuilder sb = new StringBuilder();
        Nodo<SolicitudDisco> nodo = planificador(c).copiarHistorial().getCabeza();
        while (nodo != null) {
            sb.append(sb.length() > 0 ? " " : "").append(nodo.getDato().destino());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    private static boolean enLog(String texto) {
        return veces(texto) > 0;
    }

    /** Veces que el texto aparece en los eventos recientes del log. */
    private static int veces(String texto) {
        String eventos = log.ultimos(LogEventos.CAPACIDAD_POR_DEFECTO, "\n");
        int cantidad = 0;
        for (int i = eventos.indexOf(texto); i >= 0; i = eventos.indexOf(texto, i + 1)) {
            cantidad++;
        }
        return cantidad;
    }

    private static boolean contiene(String texto, String parte) {
        return texto != null && texto.contains(parte);
    }

    /** Guarda los datos en un archivo temporal y devuelve la espera que se relee (-1 si falla). */
    private static int guardadoYReleido(DatosProcesos datos) {
        try {
            Path archivo = Files.createTempFile("avilaos15", ".json");
            int leida = ArchivoProcesos.guardar(datos, archivo.toString())
                    ? ArchivoProcesos.leer(archivo.toString()).ciclosAnticipacion() : -1;
            Files.deleteIfExists(archivo);
            return leida;
        } catch (IOException e) {
            Debug.info("Pruebas", "archivo temporal: " + e.getMessage());
            return -1;
        }
    }

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

    /** Condición que se evalúa en una prueba. */
    private interface Comprobacion {

        boolean cumple();
    }

    /** Generador reproducible (xorshift). */
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
