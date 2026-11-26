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
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.Permiso;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Bloque;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.DatosProcesoArchivo;
import avilaos.so.FabricaProcesos;
import avilaos.so.Kernel;
import avilaos.so.PCB;
import avilaos.so.PlanificadorMedianoPlazo;
import avilaos.so.archivos.Usuario;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.so.disco.SolicitudDisco;
import avilaos.sync.Buffer;
import avilaos.sync.DatosBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pruebas de terminal de la etapa 16: modelo de 7 estados y planificador a mediano plazo. */
public final class PruebasPaso16 {

    private static final String RUTA_FS = "config/sistema_archivos_enunciado.json";
    private static final String RUTA_PROCESOS = "config/procesos_enunciado.json";
    private static final int SIN_PRISA = 20_000;
    private static final int DMA_LARGO = 3_000;

    private static Resultados r;
    private static LogEventos log;
    private static int violaciones;
    private static String primeraViolacion;

    private PruebasPaso16() {
    }

    /** Ejecuta todas las pruebas de la etapa 16 e imprime su resumen. */
    public static void ejecutar(LogEventos eventos) {
        log = eventos;
        violaciones = 0;
        primeraViolacion = "";
        r = new Resultados(16, "Etapa 16: siete estados y planificador a mediano plazo");
        probarTransiciones();
        probarSeleccion();
        probarSuspenderYReactivar();
        probarVictimasYRamApartada();
        probarBeneficiarioContraSuspendido();
        probarSinSitio();
        probarEventoDuranteLaSuspension();
        probarDosSolicitudesDelMismoProceso();
        probarSemaforosYRed();
        probarDeadlineSuspendido();
        probarDeadlineEnTransito();
        probarBeneficiarioYFinales();
        probarAleatorio();
        probarSinCambios();
        probarIntegridadDeTodoElRecorrido();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** Las 49 combinaciones entre los siete estados. */
    private static void probarTransiciones() {
        r.grupo("Transiciones del modelo de 7 estados");
        EstadoProceso[] estados = EstadoProceso.values();
        int bien = 0;
        StringBuilder mal = new StringBuilder();
        for (EstadoProceso desde : estados) {
            for (EstadoProceso hacia : estados) {
                if (PCB.esTransicionValida(desde, hacia) == esperada(desde, hacia)) {
                    bien++;
                } else {
                    mal.append(' ').append(desde).append("->").append(hacia);
                }
            }
        }
        r.verificar("son 7 estados y las 49 combinaciones responden como en Stallings", estados.length == 7 && bien == 49,
                mal.toString());
        r.verificar("solo los dos suspendidos están fuera de la RAM", EstadoProceso.LISTO_SUSPENDIDO.esSuspendido()
                && EstadoProceso.BLOQUEADO_SUSPENDIDO.esSuspendido() && !EstadoProceso.BLOQUEADO.esSuspendido(), "");
        long errores = Debug.getErrores();
        PCB p = bloqueado(5, 10, 7);
        boolean recorrido = p.suspender() && p.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA
                && p.ocurrirEventoSuspendido(9) && p.getMotivoBloqueo() == MotivoBloqueo.NINGUNO && p.reactivar();
        r.verificar("Bloqueado -> Bloqueado/Suspendido -> Listo/Suspendido -> Listo conserva y limpia el motivo",
                recorrido && p.getEstado() == EstadoProceso.LISTO && p.getCicloInicioBloqueo() == 7
                && p.getCicloListoSuspendido() == 9 && Debug.getErrores() == errores, p.toString());
        r.verificar("suspender un Listo es ERROR y no cambia nada", conError(() -> !p.suspender())
                && p.getEstado() == EstadoProceso.LISTO, p.toString());
        r.verificar("reactivar un no suspendido es ERROR", conError(() -> !p.reactivar()), "");
    }

    /** Elección de víctimas (decisión 11) y de reactivaciones (decisión 12), sin simulación. */
    private static void probarSeleccion() {
        r.grupo("Elección de víctimas y de reactivaciones");
        long errores = Debug.getErrores();
        PCB buena = bloqueado(2, 16, 5);
        PCB reciente = bloqueado(8, 16, 30);
        PCB antigua = bloqueado(8, 16, 10);
        ListaEnlazada<PCB> candidatas = lista(buena, reciente, antigua);
        r.verificar("primero la peor prioridad y, si empatan, la que lleva más tiempo bloqueada",
                nombres(PlanificadorMedianoPlazo.elegirVictimas(candidatas, 40, 100)).equals(nombres(lista(antigua, reciente, buena))),
                nombres(PlanificadorMedianoPlazo.elegirVictimas(candidatas, 40, 100)));
        r.verificar("se detiene al reunir lo que falta", PlanificadorMedianoPlazo.elegirVictimas(candidatas, 10, 100).tamano() == 1
                && PlanificadorMedianoPlazo.elegirVictimas(candidatas, 17, 100).tamano() == 2, "");
        PCB enorme = bloqueado(10, 64, 1);
        r.verificar("salta la que no cabe en el swap", nombres(PlanificadorMedianoPlazo.elegirVictimas(lista(enorme, antigua), 16, 32))
                .equals(nombres(lista(antigua))), "");
        r.verificar("si no se alcanza, no elige ninguna", PlanificadorMedianoPlazo.elegirVictimas(candidatas, 49, 100) == null
                && PlanificadorMedianoPlazo.elegirVictimas(candidatas, 40, 32) == null, "");
        r.verificar("no altera la lista recibida", candidatas.primero() == buena && candidatas.ultimo() == antigua, "");
        PCB tarde = listoSuspendido(3, 16, 50);
        PCB temprano = listoSuspendido(3, 16, 20);
        PCB mejor = listoSuspendido(1, 64, 90);
        ListaEnlazada<PCB> esperando = lista(tarde, temprano, mejor);
        r.verificar("vuelve el de mejor prioridad que quepa", PlanificadorMedianoPlazo.elegirReactivacion(esperando, 64) == mejor
                && PlanificadorMedianoPlazo.elegirReactivacion(esperando, 40) == temprano
                && PlanificadorMedianoPlazo.elegirReactivacion(esperando, 8) == null, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Recorrido completo con RAM pequeña: suspender, esperar el evento y volver. */
    private static void probarSuspenderYReactivar() {
        r.grupo("Suspender y reactivar con RAM pequeña");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 0);
        PCB a = yBloquear(c, dma(c, 8, 60, 600, SIN_PRISA));
        PCB b = calculo(c, 5, 60, 700, SIN_PRISA);
        hasta(c, () -> a.getIntercambio() == TipoOperacionES.SUSPENDER, 20);
        r.verificar("el Nuevo que no cabe espera mientras no haya a quién suspender o hasta que salga",
                b.getEstado() == EstadoProceso.NUEVO, b.toString());
        r.verificar("log: se suspende a A para admitir a B", enLog("Se suspende al proceso " + a.getEtiqueta()
                + " para admitir a " + b.getEtiqueta()), "");
        SolicitudDisco s = solicitudDe(c, a);
        r.verificar("es una solicitud SUSPENDER con tantos bloques de swap como su memoria", s != null
                && s.operacion() == TipoOperacionES.SUSPENDER && s.cantidadBloques() == 60 && disco(c).esSwap(s.destino()), "" + s);
        r.verificar("mientras se escribe sigue Bloqueado y ocupando RAM", a.getEstado() == EstadoProceso.BLOQUEADO
                && a.ocupaRam() && ramUsada(c) == 60 && swapUsado(c) == 60 && kernel(c).getSuspensiones() == 0, a.toString());
        boolean suspendido = hasta(c, () -> a.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 600);
        r.verificar("al terminar el disco queda Bloqueado/Suspendido, sin RAM y con su swap", suspendido && !a.ocupaRam()
                && swapUsado(c) == 60 && kernel(c).getSuspensiones() == 1, a.toString());
        r.verificar("B entra con la memoria liberada", b.ocupaRam() && ramUsada(c) == 60, b.toString());
        r.verificar("el Kernel expone la cola y el motivo del bloqueo", kernel(c).getBloqueadosSuspendidos().primero() == a
                && a.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA && a.toString().contains("Bloqueado/Suspendido")
                && a.toString().contains("DMA") && kernel(c).procesosVivos() == 2, a.toString());
        boolean listo = hasta(c, () -> a.getEstado() == EstadoProceso.LISTO_SUSPENDIDO, 1500);
        r.verificar("al ocurrir su evento pasa a Listo/Suspendido", listo && kernel(c).getListosSuspendidos().primero() == a
                && kernel(c).getBloqueadosSuspendidos().estaVacia() && b.getEstado() != EstadoProceso.TERMINADO, a.toString());
        boolean pedida = hasta(c, () -> a.getIntercambio() == TipoOperacionES.REACTIVAR, 1500);
        r.verificar("al liberarse la RAM se pide reactivarlo y se reserva de inmediato", pedida
                && b.getEstado() == EstadoProceso.TERMINADO && a.getEstado() == EstadoProceso.LISTO_SUSPENDIDO && a.ocupaRam()
                && ramUsada(c) == 60 && swapUsado(c) == 60 && enLog("Se reactiva al proceso " + a.getEtiqueta()), a.toString());
        s = solicitudDe(c, a);
        r.verificar("es una lectura REACTIVAR de sus bloques", s != null && s.operacion() == TipoOperacionES.REACTIVAR
                && !s.operacion().isEscritura() && s.cantidadBloques() == 60, "" + s);
        boolean vuelve = hasta(c, () -> a.getIntercambio() == null, 600);
        r.verificar("al terminar el disco libera el swap y pasa a Listo", vuelve && swapUsado(c) == 0
                && a.getBloqueSwap() == PCB.SIN_SWAP && !a.getEstado().esSuspendido() && kernel(c).getReactivaciones() == 1, a.toString());
        r.verificar("los dos terminan sin vencer", terminaTodo(c) && !a.isDeadlineVencido() && !b.isDeadlineVencido()
                && ramUsada(c) == 0 && swapUsado(c) == 0, a + " / " + b);
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Varias víctimas: se eligen una a una, se piden juntas y la RAM queda apartada para el Nuevo. */
    private static void probarVictimasYRamApartada() {
        r.grupo("Varias víctimas y RAM apartada para el beneficiario");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 0);
        PCB importante = dma(c, 2, 30, DMA_LARGO, SIN_PRISA);
        PCB primera = dma(c, 9, 30, DMA_LARGO, SIN_PRISA);
        PCB segunda = dma(c, 9, 30, DMA_LARGO, SIN_PRISA);
        hasta(c, () -> bloqueados(c) == 3, 40);
        PCB grande = calculo(c, 5, 60, 20, SIN_PRISA);
        paso(c);
        r.verificar("suspende a las dos de peor prioridad y no a la importante", primera.getIntercambio() == TipoOperacionES.SUSPENDER
                && segunda.getIntercambio() == TipoOperacionES.SUSPENDER && importante.getIntercambio() == null, "");
        r.verificar("la elegida primero es la que lleva más tiempo bloqueada",
                primera.getCicloInicioBloqueo() < segunda.getCicloInicioBloqueo()
                && primera.getIdIntercambio() < segunda.getIdIntercambio(), "");
        r.verificar("las dos solicitudes se encolan juntas", pendientesDisco(c) == 2, pendientesDisco(c) + "");
        hasta(c, () -> primera.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO
                || segunda.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 600);
        PCB pequeno = calculo(c, 1, 10, 5, SIN_PRISA);
        paso(c);
        r.verificar("con una sola escrita, nadie más usa la RAM apartada (ni un Nuevo que cabría)",
                grande.getEstado() == EstadoProceso.NUEVO && pequeno.getEstado() == EstadoProceso.NUEVO, pequeno.toString());
        boolean entra = hasta(c, () -> grande.ocupaRam(), 600);
        r.verificar("cuando las dos terminan entra el beneficiario", entra
                && primera.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO
                && segunda.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO && kernel(c).getSuspensiones() == 2, grande.toString());
        r.verificar("cada víctima se suspendió una sola vez", veces("Se suspende al proceso " + primera.getEtiqueta()) == 1
                && veces("Se suspende al proceso " + segunda.getEtiqueta()) == 1, "");
        r.verificar("todos terminan, sin pérdidas ni fugas", terminaTodo(c) && ramUsada(c) == 0 && swapUsado(c) == 0
                && c.todosLosProcesos().tamano() == 5, ramUsada(c) + " / " + swapUsado(c));
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Problema A: la memoria que se libera no vuelve a la víctima antes de que entre quien la motivó. */
    private static void probarBeneficiarioContraSuspendido() {
        r.grupo("El beneficiario entra aunque su víctima ya pueda volver");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 0);
        PCB victima = dma(c, 1, 60, 30, SIN_PRISA);
        hasta(c, () -> bloqueados(c) == 1, 20);
        PCB nuevo = calculo(c, 9, 60, 40, SIN_PRISA);
        paso(c);
        boolean enTransito = hasta(c, () -> victima.isEventoEnTransito(), 80);
        r.verificar("su evento ocurre mientras se escribe", enTransito && victima.getEstado() == EstadoProceso.BLOQUEADO
                && victima.getDetalleBloqueo().contains("evento ocurrido"), victima.toString());
        boolean entra = hasta(c, () -> nuevo.ocupaRam(), 600);
        r.verificar("entra el beneficiario y la víctima, de mejor prioridad, espera en Listo/Suspendido", entra
                && victima.getEstado() == EstadoProceso.LISTO_SUSPENDIDO && victima.getIntercambio() == null
                && !victima.ocupaRam() && kernel(c).getReactivaciones() == 0, victima.toString());
        r.verificar("no entra en un ciclo de suspender y reactivar", terminaTodo(c) && kernel(c).getSuspensiones() == 1
                && kernel(c).getReactivaciones() == 1, kernel(c).getSuspensiones() + " / " + kernel(c).getReactivaciones());
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();
    }

    /** Si ni suspendiendo cabe, o no hay swap, no se suspende a nadie. */
    private static void probarSinSitio() {
        r.grupo("Nuevo que no cabe ni suspendiendo y swap insuficiente");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 0);
        PCB espera = yBloquear(c, dma(c, 8, 20, DMA_LARGO, SIN_PRISA));
        PCB corre = calculo(c, 3, 50, 300, SIN_PRISA);
        paso(c);
        PCB imposible = calculo(c, 1, 60, 5, SIN_PRISA);
        avanzar(c, 20);
        r.verificar("ni suspendiendo cabe: espera en Nuevos y no se suspende a nadie", imposible.getEstado() == EstadoProceso.NUEVO
                && espera.getIntercambio() == null && pendientesDisco(c) == 0 && swapUsado(c) == 0, espera.toString());
        PCB posible = calculo(c, 9, 40, 5, SIN_PRISA);
        paso(c);
        r.verificar("se evalúa el siguiente Nuevo y a ese sí se le hace sitio", espera.getIntercambio() == TipoOperacionES.SUSPENDER
                && enLog("para admitir a " + posible.getEtiqueta()) && !enLog("para admitir a " + imposible.getEtiqueta()), "");
        r.verificar("al terminar el que corre, el grande entra", terminaTodo(c) && !imposible.isDeadlineVencido()
                && corre.getEstado() == EstadoProceso.TERMINADO, imposible.toString());
        c.apagar();

        Cluster lleno = sistema(100, 10, 0);
        PCB grande = yBloquear(lleno, dma(lleno, 8, 60, 200, SIN_PRISA));
        PCB aguarda = calculo(lleno, 5, 60, 5, SIN_PRISA);
        avanzar(lleno, 30);
        r.verificar("swap insuficiente: no se suspende y el Nuevo espera", grande.getIntercambio() == null
                && aguarda.getEstado() == EstadoProceso.NUEVO && swapUsado(lleno) == 0 && pendientesDisco(lleno) == 0, "");
        r.verificar("entra cuando la memoria se libera sola", terminaTodo(lleno) && kernel(lleno).getSuspensiones() == 0, "");
        lleno.apagar();

        Cluster justo = sistema(100, 20, 0);
        PCB cabeEnSwap = dma(justo, 4, 20, DMA_LARGO, SIN_PRISA);
        PCB noCabeEnSwap = dma(justo, 9, 70, DMA_LARGO, SIN_PRISA);
        hasta(justo, () -> bloqueados(justo) == 2, 30);
        calculo(justo, 5, 25, 5, SIN_PRISA);
        paso(justo);
        r.verificar("salta a la víctima que no cabe en el swap", cabeEnSwap.getIntercambio() == TipoOperacionES.SUSPENDER
                && noCabeEnSwap.getIntercambio() == null, "");
        PCB otro = calculo(justo, 5, 90, 5, SIN_PRISA);
        avanzar(justo, 100);
        r.verificar("con el swap lleno el siguiente espera", swapUsado(justo) == 20 && otro.getEstado() == EstadoProceso.NUEVO
                && noCabeEnSwap.getIntercambio() == null, swapUsado(justo) + "");
        r.verificar("todos terminan y cero ERROR", terminaTodo(justo) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        justo.apagar();
    }

    /** El evento cancela la suspensión en cola o deja que termine la que ya se escribe. */
    private static void probarEventoDuranteLaSuspension() {
        r.grupo("Evento durante la suspensión");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 200);
        PCB victima = dma(c, 8, 60, 20, SIN_PRISA);
        hasta(c, () -> bloqueados(c) == 1, 20);
        PCB nuevo = calculo(c, 5, 60, 40, SIN_PRISA);
        paso(c);
        boolean pedida = victima.getIntercambio() == TipoOperacionES.SUSPENDER;
        hasta(c, () -> victima.getEstado() != EstadoProceso.BLOQUEADO, 60);
        r.verificar("en cola: se cancela, libera el swap y sigue en RAM como Listo", pedida && victima.getIntercambio() == null
                && victima.ocupaRam() && swapUsado(c) == 0 && !victima.getEstado().esSuspendido() && pendientesDisco(c) == 0
                && kernel(c).getSuspensiones() == 0, victima.toString());
        r.verificar("queda registrado en el log", enLog("Se cancela la suspensión de " + victima.getEtiqueta()), "");
        r.verificar("el Nuevo entra después", terminaTodo(c) && !nuevo.isDeadlineVencido(), nuevo.toString());
        c.apagar();

        Cluster escribiendo = sistema(100, 100, 0);
        PCB otra = dma(escribiendo, 8, 60, 30, SIN_PRISA);
        hasta(escribiendo, () -> bloqueados(escribiendo) == 1, 20);
        calculo(escribiendo, 5, 60, 400, SIN_PRISA);
        hasta(escribiendo, () -> otra.isEventoEnTransito(), 80);
        boolean seguia = otra.getEstado() == EstadoProceso.BLOQUEADO && otra.ocupaRam();
        hasta(escribiendo, () -> otra.getIntercambio() == null, 600);
        r.verificar("escribiéndose: termina y en ese ciclo pasa a Listo/Suspendido", seguia
                && otra.getEstado() == EstadoProceso.LISTO_SUSPENDIDO && !otra.ocupaRam()
                && kernel(escribiendo).getSuspensiones() == 1, otra.toString());
        r.verificar("el log conserva cuál fue el evento (el del DMA, no el fin de la escritura)",
                enLog(otra.getEtiqueta() + ": Bloqueado/Suspendido -> Listo/Suspendido (Fin de E/S)"), "");
        r.verificar("todos terminan y cero ERROR", terminaTodo(escribiendo) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        escribiendo.apagar();

        Cluster dos = sistema(100, 100, 200);
        PCB pronto = dma(dos, 9, 40, 20, SIN_PRISA);
        PCB lento = dma(dos, 8, 40, DMA_LARGO, SIN_PRISA);
        hasta(dos, () -> bloqueados(dos) == 2, 30);
        PCB necesita = calculo(dos, 5, 100, 5, SIN_PRISA);
        paso(dos);
        boolean ambas = pronto.getIntercambio() != null && lento.getIntercambio() != null;
        hasta(dos, () -> !pronto.getEstado().esSuspendido() && pronto.getIntercambio() == null, 80);
        r.verificar("si sin ella ya no alcanza, se cancelan las demás y el Nuevo sigue esperando", ambas
                && lento.getIntercambio() == null && swapUsado(dos) == 0 && necesita.getEstado() == EstadoProceso.NUEVO,
                lento.toString());
        r.verificar("todos terminan y cero ERROR", terminaTodo(dos) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        dos.apagar();
    }

    /** Un proceso con su operación de archivo y su SUSPENDER en la misma cola, atendidas en ambos órdenes. */
    private static void probarDosSolicitudesDelMismoProceso() {
        r.grupo("Dos solicitudes del mismo proceso en la cola");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 30, PoliticaDiscoTipo.SSTF, 199);
        PCB archivo = archivo(c, 60, TipoOperacionES.ELIMINAR, "/usuarios/video.mp4", 0, SIN_PRISA);
        hasta(c, () -> bloqueados(c) == 1, 20);
        calculo(c, 5, 60, 600, SIN_PRISA);
        paso(c);
        r.verificar("su operación y su SUSPENDER esperan en la misma cola", pendientesDisco(c) == 2
                && archivo.getSolicitudPendiente().idSolicitud() != archivo.getIdIntercambio(), pendientesDisco(c) + "");
        boolean suspendido = hasta(c, () -> archivo.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 600);
        r.verificar("primero el swap: queda Bloqueado/Suspendido con su operación aún pendiente", suspendido
                && archivo.getSolicitudPendiente() != null && fs(c, "/usuarios/video.mp4"), archivo.toString());
        boolean listo = hasta(c, () -> archivo.getEstado() == EstadoProceso.LISTO_SUSPENDIDO, 600);
        r.verificar("al atenderse su operación se aplica y pasa a Listo/Suspendido", listo && !fs(c, "/usuarios/video.mp4")
                && archivo.getSolicitudPendiente() == null, archivo.toString());
        r.verificar("vuelve a la RAM solo para terminar bien", terminaTodo(c) && archivo.getMotivoRechazo() == null
                && !archivo.isDeadlineVencido() && kernel(c).getReactivaciones() == 1, archivo.toString());
        c.apagar();

        Cluster inverso = sistema(100, 100, 30, PoliticaDiscoTipo.FIFO, 50);
        PCB otro = archivo(inverso, 60, TipoOperacionES.ELIMINAR, "/usuarios/video.mp4", 0, SIN_PRISA);
        hasta(inverso, () -> bloqueados(inverso) == 1, 20);
        calculo(inverso, 5, 60, 20, SIN_PRISA);
        paso(inverso);
        hasta(inverso, () -> otro.getSolicitudPendiente() == null, 600);
        r.verificar("primero la operación: se cancela el SUSPENDER y no su otra solicitud", !fs(inverso, "/usuarios/video.mp4")
                && otro.getIntercambio() == null && otro.ocupaRam() && swapUsado(inverso) == 0
                && kernel(inverso).getSuspensiones() == 0, otro.toString());
        r.verificar("terminan y cero ERROR", terminaTodo(inverso) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        inverso.apagar();

        DiscoSD d = new DiscoSD("SD-prueba", 100, 20, 0, 1);
        PlanificadorDisco plan = new PlanificadorDisco("Plan-prueba", d, PoliticaDiscoTipo.FIFO, new LogEventos(20));
        SolicitudDisco una = plan.encolar(7, "P7", TipoOperacionES.LEER, "/a", new int[]{5}, 0);
        SolicitudDisco segunda = plan.encolar(7, "P7", TipoOperacionES.SUSPENDER, "swap", new int[]{100}, 0);
        r.verificar("cancelar por número retira exactamente esa", plan.cancelarSolicitud(segunda.id(), 0) == segunda
                && plan.copiarCola().primero() == una && plan.cancelarSolicitud(segunda.id(), 0) == null, "");
    }

    /** Los bloqueados por semáforo y por red también se suspenden y despiertan estando fuera. */
    private static void probarSemaforosYRed() {
        r.grupo("Semáforos y acceso remoto con suspensión");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 0);
        Buffer buffer = c.crearBuffer(new DatosBuffer("B1", 10, 0));
        PCB consumidor = c.crearProceso(DatosProceso.consumidor("cons", 8, 50, SIN_PRISA, "B1", 1, 2, 0));
        hasta(c, () -> bloqueados(c) == 1, 20);
        PCB ocupa = calculo(c, 5, 60, 500, SIN_PRISA);
        boolean suspendido = hasta(c, () -> consumidor.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 600);
        r.verificar("un bloqueado en semáforo se suspende y conserva su motivo", suspendido
                && consumidor.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO && ocupa.ocupaRam(), consumidor.toString());
        PCB productor = c.crearProceso(DatosProceso.productor("prod", 2, 20, SIN_PRISA, "B1", 1, 2, 0));
        boolean despierta = hasta(c, () -> consumidor.getEstado() == EstadoProceso.LISTO_SUSPENDIDO, 900);
        r.verificar("el semSignal del productor lo pasa a Listo/Suspendido", despierta, consumidor.toString());
        r.verificar("productor y consumidor terminan y el buffer queda sano", terminaTodo(c) && !consumidor.isDeadlineVencido()
                && !productor.isDeadlineVencido() && buffer.verificarInvariante(), consumidor + " / " + productor);
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        c.apagar();

        for (int espera : new int[]{200, 0}) {
            long antes = Debug.getErrores();
            Cluster remoto = sistema(100, 100, espera);
            remoto.getBuffers().agregar(new Buffer("R1", 5, 1));
            PCB lejano = remoto.crearProceso(DatosProceso.productor("lejano", 8, 60, SIN_PRISA, "R1", 1, 1, 0));
            boolean enRed = hasta(remoto, () -> lejano.getMotivoBloqueo() == MotivoBloqueo.RED, 30);
            calculo(remoto, 5, 60, 5, SIN_PRISA);
            paso(remoto);
            boolean pedida = lejano.getIntercambio() == TipoOperacionES.SUSPENDER;
            boolean termina = terminaTodo(remoto);
            r.verificar("acceso remoto " + (espera > 0 ? "con el SUSPENDER en cola: se cancela" : "mientras se escribe: termina de escribirse"),
                    enRed && pedida && termina && !lejano.isDeadlineVencido() && lejano.getElementosProcesados() == 1
                    && kernel(remoto).getSuspensiones() == (espera > 0 ? 0 : 1) && Debug.getErrores() == antes,
                    lejano + " / " + kernel(remoto).getSuspensiones());
            remoto.apagar();
        }
    }

    /** Deadline en los dos estados suspendidos. */
    private static void probarDeadlineSuspendido() {
        r.grupo("Deadline en Bloqueado/Suspendido y Listo/Suspendido");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 0);
        PCB fuera = yBloquear(c, dma(c, 8, 60, DMA_LARGO, 600));
        PCB ocupa = calculo(c, 5, 60, 900, SIN_PRISA);
        hasta(c, () -> fuera.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 590);
        boolean estaba = fuera.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO;
        hasta(c, () -> fuera.getEstado() == EstadoProceso.TERMINADO, 700);
        r.verificar("Bloqueado/Suspendido: termina, libera el swap y cancela su DMA", estaba && fuera.isDeadlineVencido()
                && fuera.getCicloFin() == 600 && swapUsado(c) == 0 && c.getComputador(0).getDma().cantidadPendientes() == 0
                && ramUsada(c) == 60 && ocupa.ocupaRam(), fuera.toString());
        c.apagar();

        Cluster listos = sistema(100, 100, 0);
        PCB vuelve = yBloquear(listos, dma(listos, 8, 60, 300, 700));
        calculo(listos, 5, 60, 1500, SIN_PRISA);
        hasta(listos, () -> vuelve.getEstado() == EstadoProceso.LISTO_SUSPENDIDO, 690);
        boolean enEspera = vuelve.getEstado() == EstadoProceso.LISTO_SUSPENDIDO;
        hasta(listos, () -> vuelve.getEstado() == EstadoProceso.TERMINADO, 700);
        r.verificar("Listo/Suspendido: termina y libera el swap", enEspera && vuelve.isDeadlineVencido()
                && swapUsado(listos) == 0 && !vuelve.ocupaRam() && kernel(listos).getListosSuspendidos().estaVacia(), vuelve.toString());
        r.verificar("terminan y cero ERROR", terminaTodo(listos) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        listos.apagar();
    }

    /** Deadline con el intercambio (o la operación de archivo) en cola o ya en atención. */
    private static void probarDeadlineEnTransito() {
        r.grupo("Deadline durante SUSPENDER, REACTIVAR y operaciones de archivo");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 500);
        PCB enCola = yBloquear(c, dma(c, 8, 60, DMA_LARGO, 40));
        PCB nuevo = calculo(c, 5, 60, 5, SIN_PRISA);
        hasta(c, () -> enCola.getIntercambio() != null, 20);
        boolean pedida = enCola.getIntercambio() == TipoOperacionES.SUSPENDER;
        hasta(c, () -> enCola.getEstado() == EstadoProceso.TERMINADO, 60);
        r.verificar("SUSPENDER en cola: se cancela, termina y libera RAM y swap", pedida && enCola.isDeadlineVencido()
                && enCola.getCicloFin() == 40 && pendientesDisco(c) == 0 && swapUsado(c) == 0 && nuevo.ocupaRam()
                && kernel(c).getSuspensiones() == 0, enCola.toString());
        c.apagar();

        long mitad = cicloDe(PruebasPaso16::escenarioEscritura, p -> p.getIntercambio() == TipoOperacionES.SUSPENDER) + 40;
        Escena e = escenarioEscritura((int) mitad);
        hasta(e.c, () -> e.c.getReloj().getCiclo() >= mitad + 2, SIN_PRISA);
        r.verificar("SUSPENDER en atención: sigue Bloqueado, marcado, con su RAM y su swap", e.p.getEstado() == EstadoProceso.BLOQUEADO
                && e.p.isVencidoEnDisco() && e.p.ocupaRam() && swapUsado(e.c) == 60, e.p.toString());
        hasta(e.c, () -> e.p.getEstado() == EstadoProceso.TERMINADO, 600);
        r.verificar("al acabar la escritura termina por deadline y libera todo sin perder bloques", e.p.isDeadlineVencido()
                && e.p.getCicloFin() > mitad && swapUsado(e.c) == 0 && !e.p.ocupaRam(), e.p.toString());
        e.c.apagar();

        Cluster semaforo = sistema(100, 100, 0);
        Buffer buffer = semaforo.crearBuffer(new DatosBuffer("B1", 10, 0));
        PCB consumidor = semaforo.crearProceso(DatosProceso.consumidor("cons", 8, 60, 90, "B1", 1, 1, 0));
        hasta(semaforo, () -> bloqueados(semaforo) == 1, 20);
        calculo(semaforo, 5, 60, 5, SIN_PRISA);
        hasta(semaforo, () -> consumidor.getEstado() == EstadoProceso.TERMINADO, 600);
        r.verificar("un consumidor vencido durante la escritura sale también de la cola del semáforo", consumidor.isDeadlineVencido()
                && consumidor.getCicloFin() > 90 && !buffer.quitarBloqueado(consumidor)
                && buffer.verificarInvariante() && swapUsado(semaforo) == 0, consumidor.toString());
        semaforo.apagar();

        long vence = cicloDe(PruebasPaso16::escenarioLecturaEnCola, p -> p.getIntercambio() == TipoOperacionES.REACTIVAR) + 40;
        Escena q = escenarioLecturaEnCola((int) vence);
        hasta(q.c, () -> q.p.getIntercambio() == TipoOperacionES.REACTIVAR, SIN_PRISA);
        r.verificar("se llega con el REACTIVAR en cola antes del vencimiento", pendientesDisco(q.c) == 1
                && q.c.getComputador(0).getPlanificadorDisco().getActual() == null && q.p.ocupaRam()
                && q.c.getReloj().getCiclo() < vence, q.p.toString());
        hasta(q.c, () -> q.p.getEstado() == EstadoProceso.TERMINADO, 300);
        r.verificar("REACTIVAR en cola: se cancela y libera la RAM reservada y el swap", q.p.isDeadlineVencido()
                && q.p.getCicloFin() == vence && swapUsado(q.c) == 0 && !q.p.ocupaRam() && pendientesDisco(q.c) == 0
                && kernel(q.c).getReactivaciones() == 0, q.p.toString());
        q.c.apagar();

        long inicio = cicloDe(PruebasPaso16::escenarioLectura, p -> p.getIntercambio() == TipoOperacionES.REACTIVAR) + 40;
        Escena l = escenarioLectura((int) inicio);
        hasta(l.c, () -> l.c.getReloj().getCiclo() >= inicio + 2, SIN_PRISA);
        r.verificar("REACTIVAR en atención: sigue Listo/Suspendido con RAM y swap", l.p.getEstado() == EstadoProceso.LISTO_SUSPENDIDO
                && l.p.isVencidoEnDisco() && l.p.ocupaRam() && swapUsado(l.c) == 60, l.p.toString());
        hasta(l.c, () -> l.p.getEstado() == EstadoProceso.TERMINADO, 600);
        r.verificar("al acabar la lectura termina por deadline y libera todo", l.p.isDeadlineVencido() && swapUsado(l.c) == 0
                && !l.p.ocupaRam() && kernel(l.c).getReactivaciones() == 0, l.p.toString());
        l.c.apagar();

        long escribiendo = cicloDe(PruebasPaso16::escenarioArchivo,
                p -> p.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) + 30;
        Escena f = escenarioArchivo((int) escribiendo);
        hasta(f.c, () -> f.c.getReloj().getCiclo() >= escribiendo + 2, SIN_PRISA);
        r.verificar("suspendido con su operación en atención: espera a que el disco acabe",
                f.p.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO && f.p.isVencidoEnDisco() && swapUsado(f.c) == 60, f.p.toString());
        hasta(f.c, () -> f.p.getEstado() == EstadoProceso.TERMINADO, 900);
        r.verificar("se aplica el cambio, termina por deadline y libera el swap", f.p.isDeadlineVencido() && swapUsado(f.c) == 0
                && fs(f.c, "/usuarios/grande.bin"), f.p.toString());
        r.verificar("terminan y cero ERROR", terminaTodo(f.c) && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        f.c.apagar();
    }

    /** Muerte del beneficiario y finales normales desde un estado suspendido. */
    private static void probarBeneficiarioYFinales() {
        r.grupo("Muerte del beneficiario y finales desde suspendido");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100, 500);
        PCB victima = dma(c, 8, 60, DMA_LARGO, SIN_PRISA);
        hasta(c, () -> bloqueados(c) == 1, 20);
        PCB muere = calculo(c, 5, 60, 5, 10);
        paso(c);
        boolean pedida = victima.getIntercambio() == TipoOperacionES.SUSPENDER;
        hasta(c, () -> muere.getEstado() == EstadoProceso.TERMINADO, 40);
        r.verificar("si muere el beneficiario se cancelan sus SUSPENDER en cola", pedida && victima.getIntercambio() == null
                && victima.getEstado() == EstadoProceso.BLOQUEADO && swapUsado(c) == 0 && pendientesDisco(c) == 0, victima.toString());
        c.apagar();

        Cluster escribe = sistema(100, 100, 0);
        PCB otra = dma(escribe, 8, 60, DMA_LARGO, SIN_PRISA);
        hasta(escribe, () -> bloqueados(escribe) == 1, 20);
        PCB breve = calculo(escribe, 5, 60, 5, 40);
        boolean acaba = hasta(escribe, () -> otra.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 600);
        r.verificar("la que ya se escribía termina de suspenderse", acaba && breve.isDeadlineVencido(), otra.toString());
        r.verificar("y vuelve cuando ocurre su evento", terminaTodo(escribe) && kernel(escribe).getReactivaciones() == 1,
                otra.toString());
        escribe.apagar();

        Cluster ultima = sistema(100, 100, 0);
        PCB sale = yBloquear(ultima, ultima.crearProceso(DatosProceso.ioBound("sale", 8, 60, SIN_PRISA, 2, 2, 400, 0)));
        calculo(ultima, 5, 60, 900, SIN_PRISA);
        boolean fuera = hasta(ultima, () -> sale.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 600);
        hasta(ultima, () -> sale.getEstado() == EstadoProceso.TERMINADO, 600);
        r.verificar("si el evento era su última instrucción, termina desde suspendido sin volver", fuera
                && !sale.isDeadlineVencido() && swapUsado(ultima) == 0 && kernel(ultima).getReactivaciones() == 0, sale.toString());
        ultima.apagar();

        Cluster falla = sistema(100, 100, 30, PoliticaDiscoTipo.SSTF, 199);
        PCB primero = archivo(falla, 20, TipoOperacionES.ELIMINAR, "/usuarios/video.mp4", 0, SIN_PRISA);
        PCB segundo = archivo(falla, 60, TipoOperacionES.ELIMINAR, "/usuarios/video.mp4", 0, SIN_PRISA);
        hasta(falla, () -> bloqueados(falla) == 2, 30);
        calculo(falla, 5, 60, 700, SIN_PRISA);
        boolean estabaFuera = hasta(falla, () -> segundo.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, 900)
                && segundo.getBloqueSwap() != PCB.SIN_SWAP;
        hasta(falla, () -> segundo.getEstado() == EstadoProceso.TERMINADO, 900);
        r.verificar("si su operación falla estando suspendido, termina con el motivo y libera el swap", estabaFuera
                && segundo.getMotivoRechazo() != null && !segundo.isDeadlineVencido()
                && segundo.getBloqueSwap() == PCB.SIN_SWAP && !segundo.ocupaRam()
                && !enLog("Se reactiva al proceso " + segundo.getEtiqueta()), segundo + " / " + segundo.getMotivoRechazo());
        r.verificar("terminan y cero ERROR", terminaTodo(falla) && primero.getMotivoRechazo() == null && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + "");
        falla.apagar();
    }

    /** Mezcla al azar de todo, repetida con la misma semilla: mismo resultado y nada perdido. */
    private static void probarAleatorio() {
        r.grupo("Aleatorio reproducible");
        long errores = Debug.getErrores();
        long suspensiones = 0;
        long reactivaciones = 0;
        int distintas = 0;
        int incompletas = 0;
        int procesos = 0;
        int vencidos = 0;
        for (int semilla = 1; semilla <= 6; semilla++) {
            Corrida primera = corrida(semilla);
            Corrida repetida = corrida(semilla);
            distintas += primera.huella.equals(repetida.huella) ? 0 : 1;
            incompletas += primera.completa && repetida.completa ? 0 : 1;
            suspensiones += primera.suspensiones;
            reactivaciones += primera.reactivaciones;
            procesos += primera.procesos;
            vencidos += primera.vencidos;
        }
        r.verificar("la misma semilla da exactamente el mismo resultado", distintas == 0, distintas + " semillas distintas");
        r.verificar("todos los procesos terminan y no se pierde ninguno", incompletas == 0, incompletas + " corridas incompletas");
        r.verificar("hubo muchas suspensiones y reactivaciones, con éxitos y vencidos", suspensiones > 20 && reactivaciones > 10
                && vencidos > 0 && vencidos < procesos, suspensiones + " / " + reactivaciones + " / " + vencidos + " de " + procesos);
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Aleatorio: " + procesos + " procesos, " + suspensiones + " suspensiones, " + reactivaciones
                + " reactivaciones, " + vencidos + " por deadline");
    }

    /** Los órdenes del enunciado no cambian y el Proyecto 1 sigue sin suspensión. */
    private static void probarSinCambios() {
        r.grupo("Órdenes P1 a P4 y modo del Proyecto 1");
        long errores = Debug.getErrores();
        String[][] casos = {{"FIFO", "95 180 34 119 11 123 62 64"}, {"SSTF", "62 64 34 11 95 119 123 180"},
            {"SCAN", "62 64 95 119 123 180 34 11"}, {"C-SCAN", "62 64 95 119 123 180 11 34"}};
        for (String[] caso : casos) {
            DatosProcesos datos = ArchivoProcesos.interpretar(leerTexto(RUTA_PROCESOS).replace("\"FIFO\"", "\"" + caso[0] + "\""));
            CargaSimulacion carga = new CargaSimulacion();
            carga.usarSistemaArchivos(ArchivoSistemaArchivos.leer(RUTA_FS));
            carga.usarProcesos(datos);
            Cluster c = carga.armar(log).cluster();
            c.cargarProcesosArchivo(datos.procesos());
            boolean fin = terminaTodo(c);
            r.verificar(caso[0] + ": mismo orden y sin suspensiones (RAM 1024)", fin && orden(c).equals(caso[1])
                    && kernel(c).getSuspensiones() == 0 && swapUsado(c) == 0, orden(c));
            c.apagar();
        }
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(100, PoliticaTipo.FCFS, 3));
        Cluster p1 = new Cluster(new ConfiguracionSistema(true, 0, new CondicionesParada(0, false, 1, false, 1), 1, 0,
                computadores, new ListaEnlazada<>(), new ListaEnlazada<>(), 0), log);
        PCB a = p1.crearProceso(DatosProceso.ioBound("a", 8, 60, 500, 3, 2, 40, 0));
        p1.getReloj().ejecutarCiclos(5);
        PCB b = p1.crearProceso(DatosProceso.cpuBound("b", 5, 60, 500, 5, 0));
        p1.getReloj().ejecutarCiclos(20);
        r.verificar("Proyecto 1: sin disco nadie se suspende y el Nuevo espera", a.getEstado() == EstadoProceso.BLOQUEADO
                && b.getEstado() == EstadoProceso.NUEVO && p1.getComputador(0).getKernel().getListosSuspendidos().estaVacia()
                && p1.getComputador(0).getKernel().getBloqueadosSuspendidos().estaVacia()
                && p1.getComputador(0).getKernel().getSuspensiones() == 0, a + " / " + b);
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        p1.apagar();
    }

    /** Resume las comprobaciones hechas en cada ciclo de todos los escenarios anteriores. */
    private static void probarIntegridadDeTodoElRecorrido() {
        r.grupo("RAM, swap, disco y árbol en cada ciclo");
        r.verificar("ningún ciclo de ningún escenario dejó memoria, swap, disco o árbol inconsistentes", violaciones == 0,
                violaciones + " veces; la primera: " + primeraViolacion);
    }

    // ---------------------------------------------------------------- escenarios

    /** Sistema y proceso observado. */
    private record Escena(Cluster c, PCB p) {
    }

    /** Resultado de una corrida aleatoria. */
    private record Corrida(String huella, boolean completa, long suspensiones, long reactivaciones, int procesos, int vencidos) {
    }

    private interface Escenario {

        Escena armar(int deadline);
    }

    private interface Momento {

        boolean llego(PCB proceso);
    }

    /** Ciclo en que, sin prisa de deadline, el proceso observado llega al momento buscado. */
    private static long cicloDe(Escenario escenario, Momento momento) {
        Escena e = escenario.armar(SIN_PRISA);
        hasta(e.c, () -> momento.llego(e.p), SIN_PRISA);
        long ciclo = e.c.getReloj().getCiclo();
        e.c.apagar();
        return ciclo;
    }

    private static Escena escenarioEscritura(int deadline) {
        Cluster c = sistema(100, 100, 0);
        PCB victima = yBloquear(c, dma(c, 8, 60, DMA_LARGO, deadline));
        calculo(c, 5, 60, 5, SIN_PRISA);
        return new Escena(c, victima);
    }

    private static Escena escenarioLectura(int deadline) {
        Cluster c = sistema(100, 100, 0);
        PCB victima = yBloquear(c, dma(c, 8, 60, 300, deadline));
        calculo(c, 5, 60, 400, SIN_PRISA);
        return new Escena(c, victima);
    }

    /** Con espera anticipatoria de 150 ciclos, el REACTIVAR aguarda en la cola. */
    private static Escena escenarioLecturaEnCola(int deadline) {
        Cluster c = sistema(100, 100, 150);
        PCB victima = yBloquear(c, dma(c, 8, 60, 500, deadline));
        calculo(c, 5, 60, 60, SIN_PRISA);
        return new Escena(c, victima);
    }

    /** Crea un archivo de 60 bloques; con SSTF desde el 199 se escribe antes su swap y luego el archivo. */
    private static Escena escenarioArchivo(int deadline) {
        Cluster c = sistema(100, 100, 30, PoliticaDiscoTipo.SSTF, 199);
        PCB creador = yBloquear(c, archivo(c, 60, TipoOperacionES.CREAR, "/usuarios/grande.bin", 60, deadline));
        calculo(c, 5, 60, 900, SIN_PRISA);
        return new Escena(c, creador);
    }

    private static Corrida corrida(int semilla) {
        Azar azar = new Azar(semilla);
        PoliticaDiscoTipo[] politicas = PoliticaDiscoTipo.values();
        Cluster c = sistema(120, 90, semilla % 3 == 0 ? 8 : 0, politicas[semilla % politicas.length], 50);
        Buffer buffer = c.crearBuffer(new DatosBuffer("B1", 4, 0));
        ListaEnlazada<PCB> creados = new ListaEnlazada<>();
        int primerId = -1;
        for (int ciclo = 0; ciclo < 1500; ciclo++) {
            if (azar.entre(0, 11) == 0) {
                int prioridad = azar.entre(1, 10);
                int memoria = azar.entre(8, 50);
                int deadline = azar.entre(0, 5) == 0 ? azar.entre(5, 80) : azar.entre(400, 2500);
                PCB p = switch (azar.entre(0, 4)) {
                    case 0 -> c.crearProceso(DatosProceso.cpuBound("c", prioridad, memoria, deadline, azar.entre(2, 30), 0));
                    case 1 -> c.crearProceso(DatosProceso.ioBound("e", prioridad, memoria, deadline, azar.entre(2, 9),
                            azar.entre(2, 4), azar.entre(2, 150), 0));
                    case 2 -> azar.entre(0, 1) == 0
                            ? c.crearProceso(DatosProceso.productor("p", prioridad, memoria, deadline, "B1", 1, azar.entre(1, 4), 0))
                            : c.crearProceso(DatosProceso.consumidor("q", prioridad, memoria, deadline, "B1", 1, azar.entre(1, 4), 0));
                    default -> archivoAlAzar(c, azar, prioridad, memoria, deadline);
                };
                primerId = primerId < 0 ? p.getId() : primerId;
                creados.agregarFinal(p);
            }
            if (azar.entre(0, 120) == 0) {
                c.getComputador(0).getPlanificadorDisco().cambiarPolitica(politicas[azar.entre(0, politicas.length - 1)], ciclo);
            }
            if (azar.entre(0, 150) == 0) {
                c.cambiarPolitica(0, PoliticaTipo.values()[azar.entre(0, PoliticaTipo.values().length - 1)]);
            }
            paso(c);
        }
        boolean termino = terminaTodo(c);
        StringBuilder huella = new StringBuilder();
        int vencidos = 0;
        Nodo<PCB> nodo = creados.getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            huella.append(p.getId() - primerId).append(':').append(p.getEstado()).append('@').append(p.getCicloFin())
                    .append(p.isDeadlineVencido() ? "v" : "").append(' ');
            vencidos += p.isDeadlineVencido() ? 1 : 0;
            nodo = nodo.getSiguiente();
        }
        huella.append(kernel(c).getSuspensiones()).append('/').append(kernel(c).getReactivaciones()).append('/')
                .append(disco(c).getDesplazamientoTotal()).append('/').append(c.getReloj().getCiclo());
        boolean completa = termino && c.todosLosProcesos().tamano() == creados.tamano() && swapUsado(c) == 0
                && ramUsada(c) == 4 && pendientesDisco(c) == 0 && buffer.verificarInvariante()
                && c.getComputador(0).getSistemaArchivos().reservasPendientes() == 0;
        Corrida resultado = new Corrida(huella.toString(), completa, kernel(c).getSuspensiones(), kernel(c).getReactivaciones(),
                creados.tamano(), vencidos);
        c.apagar();
        return resultado;
    }

    private static PCB archivoAlAzar(Cluster c, Azar azar, int prioridad, int memoria, int deadline) {
        TipoOperacionES[] operaciones = {TipoOperacionES.CREAR, TipoOperacionES.LEER, TipoOperacionES.ACTUALIZAR,
            TipoOperacionES.ELIMINAR};
        TipoOperacionES operacion = operaciones[azar.entre(0, 3)];
        String ruta = "/usuarios/f" + azar.entre(0, 5);
        if (operacion != TipoOperacionES.CREAR && azar.entre(0, 1) == 0) {
            ruta = "/usuarios/notas.txt";
        }
        return c.crearProcesoArchivo(new DatosProcesoArchivo("es", memoria, deadline, 2, ruta, operacion,
                operacion == TipoOperacionES.CREAR ? azar.entre(1, 20) : 0, prioridad, null), Usuario.ADMIN, Permiso.PUBLICO);
    }

    // ---------------------------------------------------------------- ayudantes

    private static Cluster sistema(int ram, int swap, int espera) {
        return sistema(ram, swap, espera, PoliticaDiscoTipo.FIFO, 50);
    }

    /** Sistema con el árbol del enunciado, cabezal a 10 por ciclo y sin procesos. */
    private static Cluster sistema(int ram, int swap, int espera, PoliticaDiscoTipo politica, int cabezal) {
        String procesos = "{\"globalConfig\":{\"cycleDurationMs\":0,\"swapSizeBlocks\":" + swap + ",\"initialDiskPolicy\":\""
                + politica.getNombre() + "\",\"headSpeed\":10,\"anticipationCycles\":" + espera + "},"
                + "\"computer\":{\"ramCapacity\":" + ram + ",\"policy\":\"FCFS\",\"quantum\":0},\"processes\":[]}";
        CargaSimulacion carga = new CargaSimulacion();
        carga.usarSistemaArchivos(ArchivoSistemaArchivos.interpretar(leerTexto(RUTA_FS)
                .replace("\"initialHead\": 50", "\"initialHead\": " + cabezal)));
        carga.usarProcesos(ArchivoProcesos.interpretar(procesos));
        return carga.armar(log).cluster();
    }

    /** Proceso que calcula, se bloquea en el DMA los ciclos indicados y calcula una vez más. */
    private static PCB dma(Cluster c, int prioridad, int memoria, int duracion, int deadline) {
        return c.crearProceso(DatosProceso.ioBound("dma", prioridad, memoria, deadline, 3, 2, duracion, 0));
    }

    private static PCB calculo(Cluster c, int prioridad, int memoria, int instrucciones, int deadline) {
        return c.crearProceso(DatosProceso.cpuBound("calc", prioridad, memoria, deadline, instrucciones, 0));
    }

    private static PCB archivo(Cluster c, int memoria, TipoOperacionES operacion, String ruta, int bloques, int deadline) {
        return c.crearProcesoArchivo(new DatosProcesoArchivo("es", memoria, deadline, 2, ruta, operacion, bloques, 8, null),
                Usuario.ADMIN, Permiso.PUBLICO);
    }

    /** Ejecuta ciclos hasta que el proceso se bloquea, para que quien llegue después no le gane la admisión. */
    private static PCB yBloquear(Cluster c, PCB p) {
        hasta(c, () -> p.getEstado() == EstadoProceso.BLOQUEADO, 30);
        return p;
    }

    /** Un ciclo de reloj seguido de la revisión de integridad. */
    private static void paso(Cluster c) {
        c.getReloj().ejecutarCiclos(1);
        String problema = inconsistencia(c);
        if (problema != null) {
            if (violaciones == 0) {
                primeraViolacion = "ciclo " + c.getReloj().getCiclo() + ": " + problema;
            }
            violaciones++;
        }
    }

    private static void avanzar(Cluster c, int ciclos) {
        for (int i = 0; i < ciclos; i++) {
            paso(c);
        }
    }

    /** Cruza cada proceso con la RAM, el swap, el disco y el árbol; devuelve el primer problema o null. */
    private static String inconsistencia(Cluster c) {
        Computador pc = c.getComputador(0);
        DiscoSD d = pc.getDisco();
        int ram = 0;
        Nodo<Buffer> b = c.getBuffers().copiar().getCabeza();
        while (b != null) {
            ram += b.getDato().getAnfitrion() == 0 ? b.getDato().getCapacidad() : 0;
            b = b.getSiguiente();
        }
        int swap = 0;
        Nodo<PCB> nodo = pc.getKernel().getTodos().getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            EstadoProceso estado = p.getEstado();
            boolean debeOcupar = estado == EstadoProceso.LISTO || estado == EstadoProceso.EJECUCION
                    || estado == EstadoProceso.BLOQUEADO
                    || (estado == EstadoProceso.LISTO_SUSPENDIDO && p.getIntercambio() == TipoOperacionES.REACTIVAR);
            boolean debeTenerSwap = estado.esSuspendido() || p.getIntercambio() == TipoOperacionES.SUSPENDER;
            if (p.ocupaRam() != debeOcupar) {
                return p + " ocupaRam=" + p.ocupaRam();
            }
            if ((p.getBloqueSwap() != PCB.SIN_SWAP) != debeTenerSwap) {
                return p + " swap=" + p.getBloqueSwap();
            }
            ram += p.ocupaRam() ? p.getMemoria() : 0;
            if (debeTenerSwap) {
                swap += p.getMemoria();
                int largo = 0;
                for (int n = p.getBloqueSwap(); n != Bloque.FIN && largo <= p.getMemoria(); n = d.leer(n).siguiente()) {
                    if (!d.esSwap(n) || !p.getEtiqueta().equals(d.leer(n).dueno())) {
                        return p + " bloque de swap ajeno " + n;
                    }
                    largo++;
                }
                if (largo != p.getMemoria()) {
                    return p + " cadena de swap de " + largo;
                }
            }
            nodo = nodo.getSiguiente();
        }
        if (ram != pc.getRam().getUsada() || !pc.getRam().verificarIntegridad()) {
            return "RAM usada " + pc.getRam().getUsada() + " y los procesos suman " + ram;
        }
        if (swap != swapUsado(c)) {
            return "swap usado " + swapUsado(c) + " y los procesos suman " + swap;
        }
        return d.verificarIntegridad() && pc.getSistemaArchivos().verificarIntegridad() ? null : "disco o árbol rotos";
    }

    private static boolean hasta(Cluster c, Comprobacion condicion, int maximo) {
        for (int i = 0; i < maximo && !condicion.cumple(); i++) {
            paso(c);
        }
        return condicion.cumple();
    }

    private static boolean terminaTodo(Cluster c) {
        return hasta(c, c::todosTerminados, 60_000);
    }

    private static Kernel kernel(Cluster c) {
        return c.getComputador(0).getKernel();
    }

    private static DiscoSD disco(Cluster c) {
        return c.getComputador(0).getDisco();
    }

    private static boolean fs(Cluster c, String ruta) {
        return c.getComputador(0).getSistemaArchivos().existe(ruta);
    }

    private static int ramUsada(Cluster c) {
        return c.getComputador(0).getRam().getUsada();
    }

    private static int swapUsado(Cluster c) {
        return disco(c).getBloquesSwap() - disco(c).contarLibres(true);
    }

    private static int bloqueados(Cluster c) {
        return kernel(c).getBloqueados().tamano();
    }

    private static int pendientesDisco(Cluster c) {
        PlanificadorDisco plan = c.getComputador(0).getPlanificadorDisco();
        return plan.cantidadEnCola() + (plan.getActual() == null ? 0 : 1);
    }

    /** Solicitud de intercambio del proceso, en cola o en atención. */
    private static SolicitudDisco solicitudDe(Cluster c, PCB p) {
        PlanificadorDisco plan = c.getComputador(0).getPlanificadorDisco();
        SolicitudDisco actual = plan.getActual();
        if (actual != null && actual.id() == p.getIdIntercambio()) {
            return actual;
        }
        Nodo<SolicitudDisco> nodo = plan.copiarCola().getCabeza();
        while (nodo != null && nodo.getDato().id() != p.getIdIntercambio()) {
            nodo = nodo.getSiguiente();
        }
        return nodo == null ? null : nodo.getDato();
    }

    private static String orden(Cluster c) {
        StringBuilder sb = new StringBuilder();
        Nodo<SolicitudDisco> nodo = c.getComputador(0).getPlanificadorDisco().copiarHistorial().getCabeza();
        while (nodo != null) {
            sb.append(sb.length() > 0 ? " " : "").append(nodo.getDato().destino());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Tabla de Stallings usada por ÁvilaOS. */
    private static boolean esperada(EstadoProceso desde, EstadoProceso hacia) {
        if (desde == hacia || desde == EstadoProceso.TERMINADO) {
            return false;
        }
        if (hacia == EstadoProceso.TERMINADO) {
            return true;
        }
        String par = desde.name() + ">" + hacia.name();
        for (String valida : new String[]{"NUEVO>LISTO", "LISTO>EJECUCION", "EJECUCION>LISTO", "EJECUCION>BLOQUEADO",
            "BLOQUEADO>LISTO", "BLOQUEADO>BLOQUEADO_SUSPENDIDO", "BLOQUEADO_SUSPENDIDO>LISTO_SUSPENDIDO",
            "LISTO_SUSPENDIDO>LISTO"}) {
            if (valida.equals(par)) {
                return true;
            }
        }
        return false;
    }

    /** PCB suelto que se bloqueó en el ciclo indicado. */
    private static PCB bloqueado(int prioridad, int memoria, long ciclo) {
        PCB p = FabricaProcesos.crear(DatosProceso.cpuBound("x", prioridad, memoria, 100, 5, 0), 0);
        p.cambiarEstado(EstadoProceso.LISTO);
        p.despachar(1);
        p.bloquear(MotivoBloqueo.ENTRADA_SALIDA, "prueba", ciclo);
        return p;
    }

    private static PCB listoSuspendido(int prioridad, int memoria, long ciclo) {
        PCB p = bloqueado(prioridad, memoria, 1);
        p.suspender();
        p.ocurrirEventoSuspendido(ciclo);
        return p;
    }

    private static ListaEnlazada<PCB> lista(PCB... procesos) {
        ListaEnlazada<PCB> lista = new ListaEnlazada<>();
        for (PCB p : procesos) {
            lista.agregarFinal(p);
        }
        return lista;
    }

    private static String nombres(ListaEnlazada<PCB> procesos) {
        StringBuilder sb = new StringBuilder();
        Nodo<PCB> nodo = procesos == null ? null : procesos.getCabeza();
        while (nodo != null) {
            sb.append(nodo.getDato().getEtiqueta()).append(' ');
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    private static boolean enLog(String texto) {
        return veces(texto) > 0;
    }

    private static int veces(String texto) {
        String eventos = log.ultimos(LogEventos.CAPACIDAD_POR_DEFECTO, "\n");
        int cantidad = 0;
        for (int i = eventos.indexOf(texto); i >= 0; i = eventos.indexOf(texto, i + 1)) {
            cantidad++;
        }
        return cantidad;
    }

    private static String leerTexto(String ruta) {
        try {
            return Files.readString(Path.of(ruta));
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean conError(Comprobacion c) {
        long antes = Debug.getErrores();
        boolean resultado = c.cumple();
        return resultado && Debug.getErrores() > antes;
    }

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
