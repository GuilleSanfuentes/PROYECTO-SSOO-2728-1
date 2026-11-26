package avilaos.pruebas;

import avilaos.cluster.Atencion;
import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.cluster.ComparadorPoliticas;
import avilaos.cluster.ResultadoPolitica;
import avilaos.config.ArchivoProcesos;
import avilaos.config.ArchivoSistemaArchivos;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.config.DatosProcesos;
import avilaos.enums.EstadoProceso;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.CambioPolitica;
import avilaos.metricas.Metricas;
import avilaos.metricas.MetricasDisco;
import avilaos.metricas.PuntoDesplazamiento;
import avilaos.so.DatosProceso;
import avilaos.so.GeneradorIds;
import avilaos.so.PCB;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.vista.GraficoDesplazamiento;
import avilaos.vista.gui.PanelGraficoDisco;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.data.xy.XYDataset;

/** Pruebas de terminal de la etapa 17: métricas del disco, comparador de políticas y gráfico. */
public final class PruebasPaso17 {

    private static final String RUTA_FS = "config/sistema_archivos_enunciado.json";
    private static final String RUTA_PROCESOS = "config/procesos_enunciado.json";

    /** Política, orden, desplazamiento, ciclo final, espera total y espera sin anticipación de los JSON del enunciado. */
    private static final Object[][] REFERENCIA = {
        {PoliticaDiscoTipo.FIFO, "95 180 34 119 11 123 62 64", 644, 354, 169.625, 161.75},
        {PoliticaDiscoTipo.SSTF, "62 64 34 11 95 119 123 180", 236, 149, 52.375, 44.5},
        {PoliticaDiscoTipo.SCAN, "62 64 95 119 123 180 34 11", 537, 301, 73.625, 65.75},
        {PoliticaDiscoTipo.C_SCAN, "62 64 95 119 123 180 11 34", 283, 175, 58.0, 50.125},
        {PoliticaDiscoTipo.LOOK, "62 64 95 119 123 180 34 11", 299, 181, 51.125, 43.25},
        {PoliticaDiscoTipo.C_LOOK, "62 64 95 119 123 180 11 34", 153, 109, 42.25, 34.375}
    };

    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso17() {
    }

    /** Ejecuta todas las pruebas de la etapa 17 e imprime su resumen. */
    public static void ejecutar(LogEventos eventos) {
        log = eventos;
        r = new Resultados(17, "Etapa 17: métricas y gráfico del disco");
        probarComparadorDelEnunciado();
        probarEsperas();
        probarSerie();
        probarCambiosDePolitica();
        probarFotosConElRelojEnMarcha();
        probarSuspension();
        probarComparadorAislado();
        probarComparadorConProblemas();
        probarMetricasDelProyecto1();
        probarGrafico();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** Con los dos JSON del enunciado, el comparador reproduce P1 a P4 y los valores de referencia. */
    private static void probarComparadorDelEnunciado() {
        r.grupo("Comparador con los JSON del enunciado");
        long errores = Debug.getErrores();
        ComparadorPoliticas.Comparacion comparacion = ComparadorPoliticas.comparar(cargaDelEnunciado("FIFO"), null);
        r.verificar("compara las seis políticas en el orden del enum", comparacion.hecha()
                && comparacion.resultados().length == 6, "" + comparacion.motivo());
        for (Object[] fila : REFERENCIA) {
            PoliticaDiscoTipo politica = (PoliticaDiscoTipo) fila[0];
            ResultadoPolitica res = comparacion.de(politica);
            String nombre = politica.getNombre();
            r.verificar(nombre + ": orden " + fila[1], res.bloquesEnOrden().equals(fila[1]), res.bloquesEnOrden());
            MetricasDisco m = res.metricas();
            r.verificar(nombre + ": desplazamiento " + fila[2] + " y fin en el ciclo " + fila[3],
                    m.desplazamientoTotal() == (int) fila[2] && res.cicloFinal() == (int) fila[3],
                    m.desplazamientoTotal() + " / " + res.cicloFinal());
            r.verificar(nombre + ": espera total " + fila[4] + " y sin anticipación " + fila[5],
                    casi(m.esperaPromedioTotal(), (double) fila[4]) && casi(m.esperaPromedioSinAnticipacion(), (double) fila[5]),
                    m.esperaPromedioTotal() + " / " + m.esperaPromedioSinAnticipacion());
            r.verificar(nombre + ": 8 atendidas, todas por ella; completa, sin vencidos, rechazos ni suspensiones",
                    m.atendidas() == 8 && m.atendidasPor(politica) == 8 && m.solicitudesMedidas() == 8 && res.completo()
                    && res.procesos() == 8 && res.vencidos() == 0 && res.rechazados() == 0 && m.suspensiones() == 0
                    && res.etiqueta().equals(nombre), res.etiqueta());
            r.verificar(nombre + ": la serie no decrece y termina en el desplazamiento total",
                    noDecrece(m.serie()) && m.serie()[m.serie().length - 1].acumulado() == m.desplazamientoTotal(), "");
            r.seccion(" " + nombre + ": desplazamiento " + m.desplazamientoTotal() + " | espera total "
                    + String.format("%.3f", m.esperaPromedioTotal()) + " | sin anticipación "
                    + String.format("%.3f", m.esperaPromedioSinAnticipacion()) + " | fin en el ciclo " + res.cicloFinal());
        }
        Atencion primera = comparacion.de(PoliticaDiscoTipo.FIFO).orden()[0];
        r.verificar("cada atención dice bloque, operación y proceso", primera.bloque() == 95
                && primera.operacion() == TipoOperacionES.LEER && primera.proceso().equals("Proc_95"), "" + primera);
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Las dos esperas, calculadas a mano en un disco aislado. */
    private static void probarEsperas() {
        r.grupo("Espera total y sin anticipación");
        long errores = Debug.getErrores();
        DiscoSD d = new DiscoSD("SD-17", 200, 0, 50, 1);
        PlanificadorDisco p = new PlanificadorDisco("Plan-17", d, PoliticaDiscoTipo.FIFO, new LogEventos(50), 3);
        r.verificar("sin solicitudes las dos valen 0", p.getEsperaPromedio() == 0 && p.getEsperaPromedioSinAnticipacion() == 0, "");
        encolar(p, 1, 50, 0);
        ticks(d, 1, 2);
        encolar(p, 2, 50, 2);
        ticks(d, 3, 2);
        r.verificar("la primera sufre los 3 ciclos de la ventana: total 4, sin anticipación 1",
                p.getAtendidas() == 1 && p.getEsperaPromedio() == 4 && p.getEsperaPromedioSinAnticipacion() == 1,
                p.getEsperaPromedio() + " / " + p.getEsperaPromedioSinAnticipacion());
        ticks(d, 5, 1);
        r.verificar("la que llegó en la ventana solo descuenta el ciclo que la vivió: medias 3,5 y 1,5",
                p.getAtendidas() == 2 && p.getEsperaPromedio() == 3.5 && p.getEsperaPromedioSinAnticipacion() == 1.5,
                p.getEsperaPromedio() + " / " + p.getEsperaPromedioSinAnticipacion());
        ticks(d, 6, 5);
        encolar(p, 3, 50, 10);
        ticks(d, 11, 4);
        r.verificar("una segunda ventana también se descuenta: medias 11/3 y 4/3",
                casi(p.getEsperaPromedio(), 11.0 / 3) && casi(p.getEsperaPromedioSinAnticipacion(), 4.0 / 3),
                p.getEsperaPromedio() + " / " + p.getEsperaPromedioSinAnticipacion());
        double antes = p.getEsperaPromedio();
        encolar(p, 4, 90, 14);
        ticks(d, 15, 2);
        p.cancelar(4, 16);
        r.verificar("una solicitud cancelada en cola no entra en las medias", p.getEsperaPromedio() == antes
                && p.fotografiar(0, 0).solicitudesMedidas() == 3, "");
        DiscoSD d0 = new DiscoSD("SD-17b", 200, 0, 50, 1);
        PlanificadorDisco sin = new PlanificadorDisco("Plan-17b", d0, PoliticaDiscoTipo.FIFO, new LogEventos(50), 0);
        encolar(sin, 1, 60, 0);
        encolar(sin, 2, 70, 0);
        ticks(d0, 1, 40);
        r.verificar("sin anticipación configurada las dos esperas coinciden", sin.getEsperaPromedio() > 0
                && sin.getEsperaPromedio() == sin.getEsperaPromedioSinAnticipacion(), sin.getEsperaPromedio() + "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** La serie propia coincide ciclo a ciclo con el disco, no se recorta y comprime los tramos quietos. */
    private static void probarSerie() {
        r.grupo("Serie del desplazamiento acumulado");
        long errores = Debug.getErrores();
        Cluster c = armar(cargaDelEnunciado("SCAN"));
        c.cargarProcesosArchivo(ArchivoProcesos.leer(RUTA_PROCESOS).procesos());
        DiscoSD disco = c.getComputador(0).getDisco();
        long[] real = new long[401];
        for (int ciclo = 1; ciclo <= 400; ciclo++) {
            c.getReloj().ejecutarCiclos(1);
            real[ciclo] = disco.getDesplazamientoTotal();
        }
        PuntoDesplazamiento[] serie = c.getMetricasDisco().serie();
        int mal = 0;
        for (int ciclo = 0; ciclo <= 400; ciclo++) {
            mal += casi(valorEn(serie, ciclo), real[ciclo]) ? 0 : 1;
        }
        r.verificar("reconstruye el desplazamiento de cada uno de los 400 ciclos", mal == 0, mal + " ciclos distintos");
        r.verificar("empieza en (0, 0), no decrece y acaba en el total", serie[0].ciclo() == 0 && serie[0].acumulado() == 0
                && noDecrece(serie) && serie[serie.length - 1].ciclo() == 400
                && serie[serie.length - 1].acumulado() == disco.getDesplazamientoTotal(), "");
        int antes = serie.length;
        c.getReloj().ejecutarCiclos(1000);
        PuntoDesplazamiento[] quieta = c.getMetricasDisco().serie();
        r.verificar("1000 ciclos sin movimiento solo alargan el último tramo", quieta.length == antes
                && quieta[quieta.length - 1].ciclo() == 1400, antes + " -> " + quieta.length);
        c.apagar();

        DiscoSD largo = new DiscoSD("SD-largo", 200, 0, 0, 1);
        PlanificadorDisco p = new PlanificadorDisco("Plan-largo", largo, PoliticaDiscoTipo.FIFO, new LogEventos(10), 0);
        int solicitudes = 2500;
        for (int i = 0; i < solicitudes; i++) {
            encolar(p, i + 1, i % 2 == 0 ? 10 : 0, 0);
        }
        long ciclo = 0;
        while (!p.estaInactivo() && ciclo < 100_000) {
            largo.tick(++ciclo);
        }
        PuntoDesplazamiento[] completa = p.copiarSerie();
        r.verificar("con 2500 atenciones el historial se recorta a 2000 pero la serie sigue entera",
                p.getAtendidas() == solicitudes && p.copiarHistorial().tamano() == PlanificadorDisco.HISTORIAL_MAXIMO
                && completa[0].ciclo() == 0 && completa.length > 20_000 && valorEn(completa, 11) == 10
                && completa[completa.length - 1].acumulado() == largo.getDesplazamientoTotal(),
                completa.length + " puntos / " + largo.getDesplazamientoTotal());
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Cada solicitud cuenta para la política que la eligió, y cada cambio queda marcado en su ciclo real. */
    private static void probarCambiosDePolitica() {
        r.grupo("Cambios de política en plena ejecución");
        long errores = Debug.getErrores();
        DiscoSD d = new DiscoSD("SD-17c", 200, 0, 0, 1);
        PlanificadorDisco p = new PlanificadorDisco("Plan-17c", d, PoliticaDiscoTipo.FIFO, new LogEventos(50), 0);
        encolar(p, 1, 50, 0);
        encolar(p, 2, 60, 0);
        ticks(d, 1, 10);
        p.cambiarPolitica(PoliticaDiscoTipo.SSTF, 3);
        ticks(d, 11, 41);
        r.verificar("cambiada durante la atención, esa solicitud cuenta para FIFO", p.getAtendidas() == 1
                && p.getAtendidasPor(PoliticaDiscoTipo.FIFO) == 1 && p.getAtendidasPor(PoliticaDiscoTipo.SSTF) == 0,
                p.getAtendidasPor(PoliticaDiscoTipo.FIFO) + " / " + p.getAtendidasPor(PoliticaDiscoTipo.SSTF));
        ticks(d, 52, 20);
        r.verificar("la siguiente, que ya elige SSTF, cuenta para SSTF", p.getAtendidas() == 2
                && p.getAtendidasPor(PoliticaDiscoTipo.SSTF) == 1, "");
        CambioPolitica[] cambios = p.fotografiar(0, 0).cambios();
        r.verificar("la marca va en el primer ciclo con la nueva política, aunque se pidiera con un ciclo atrasado",
                cambios.length == 1 && cambios[0].ciclo() == 11 && cambios[0].anterior() == PoliticaDiscoTipo.FIFO
                && cambios[0].nueva() == PoliticaDiscoTipo.SSTF, cambios.length == 0 ? "sin cambios" : "" + cambios[0]);
        p.cambiarPolitica(PoliticaDiscoTipo.SSTF, 71);
        p.cambiarPolitica(PoliticaDiscoTipo.SCAN, 71);
        p.cambiarPolitica(PoliticaDiscoTipo.C_LOOK, 71);
        cambios = p.fotografiar(0, 0).cambios();
        r.verificar("repetir la política no marca; varios cambios entre dos ciclos quedan en el mismo",
                cambios.length == 3 && cambios[1].ciclo() == 72 && cambios[2].ciclo() == 72
                && cambios[2].nueva() == PoliticaDiscoTipo.C_LOOK, cambios.length + "");

        DiscoSD d2 = new DiscoSD("SD-17d", 200, 0, 50, 1);
        PlanificadorDisco espera = new PlanificadorDisco("Plan-17d", d2, PoliticaDiscoTipo.FIFO, new LogEventos(50), 5);
        encolar(espera, 1, 60, 0);
        ticks(d2, 1, 2);
        espera.cambiarPolitica(PoliticaDiscoTipo.LOOK, 2);
        ticks(d2, 3, 30);
        r.verificar("cambiada durante la espera anticipatoria, cuenta para la que acaba eligiendo",
                espera.getAtendidasPor(PoliticaDiscoTipo.LOOK) == 1 && espera.getAtendidasPor(PoliticaDiscoTipo.FIFO) == 0, "");

        Cluster c = armar(cargaDelEnunciado("FIFO"));
        c.cargarProcesosArchivo(ArchivoProcesos.leer(RUTA_PROCESOS).procesos());
        PlanificadorDisco planificador = c.getComputador(0).getPlanificadorDisco();
        hasta(c, () -> planificador.getAtendidas() == 2 && planificador.getActual() != null);
        planificador.cambiarPolitica(PoliticaDiscoTipo.SSTF, c.getReloj().getCiclo());
        long cambio = c.getReloj().getCiclo() + 1;
        hasta(c, c::todosTerminados);
        MetricasDisco m = c.getMetricasDisco();
        long suma = 0;
        for (long cantidad : m.atendidasPorPolitica()) {
            suma += cantidad;
        }
        r.verificar("a mitad de la carga del enunciado: 3 para FIFO (incluida la que estaba en curso) y 5 para SSTF",
                m.atendidasPor(PoliticaDiscoTipo.FIFO) == 3 && m.atendidasPor(PoliticaDiscoTipo.SSTF) == 5 && suma == m.atendidas()
                && m.cambios().length == 1 && m.cambios()[0].ciclo() == cambio,
                m.atendidasPor(PoliticaDiscoTipo.FIFO) + " / " + m.atendidasPor(PoliticaDiscoTipo.SSTF));
        c.apagar();
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Fotos pedidas desde otro hilo con el reloj corriendo: siempre coherentes y sin cambiar después. */
    private static void probarFotosConElRelojEnMarcha() {
        r.grupo("Fotos coherentes con el reloj en marcha");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100);
        for (int i = 0; i < 24; i++) {
            c.crearProceso(DatosProceso.ioBound("m" + i, 1 + i % 10, 30 + i % 3 * 10, 5000, 6, 2, 30 + i * 7, 0));
        }
        AtomicInteger malas = new AtomicInteger();
        AtomicInteger fotos = new AtomicInteger();
        Semaphore fin = new Semaphore(0);
        c.iniciar();
        Thread lector = new Thread(() -> {
            MetricasDisco previa = null;
            long limite = System.currentTimeMillis() + 20_000;
            while (!c.todosTerminados() && System.currentTimeMillis() < limite) {
                MetricasDisco foto = c.getMetricasDisco();
                Metricas procesador = CalculadorMetricas.computador(c, 0);
                if (!coherente(foto, previa) || procesador.utilizacion() < 0 || procesador.utilizacion() > 100.0001) {
                    malas.incrementAndGet();
                }
                previa = foto;
                fotos.incrementAndGet();
            }
            fin.release();
        });
        lector.start();
        fin.acquireUninterruptibly();
        boolean termino = c.todosTerminados();
        c.detener();
        c.esperarFin(5000);
        r.verificar("todas las fotos cuadran entre sí y avanzan sin retroceder", termino && malas.get() == 0 && fotos.get() > 5,
                malas.get() + " malas de " + fotos.get() + ", terminó=" + termino);
        MetricasDisco guardada = c.getMetricasDisco();
        long desplazamiento = guardada.desplazamientoTotal();
        int puntos = guardada.serie().length;
        long fifo = guardada.atendidasPor(PoliticaDiscoTipo.FIFO);
        guardada.atendidasPorPolitica()[PoliticaDiscoTipo.FIFO.ordinal()] = -1;
        guardada.serie()[0] = new PuntoDesplazamiento(99, 99);
        r.verificar("modificar los arreglos que entrega no altera la foto",
                guardada.atendidasPor(PoliticaDiscoTipo.FIFO) == fifo && guardada.serie()[0].ciclo() == 0, "");
        c.crearProceso(DatosProceso.ioBound("otro", 5, 90, 5000, 6, 2, 10, 0));
        c.crearProceso(DatosProceso.ioBound("mas", 5, 90, 5000, 6, 2, 400, 0));
        hasta(c, c::todosTerminados);
        r.verificar("la foto no cambia aunque la simulación siga", guardada.desplazamientoTotal() == desplazamiento
                && guardada.serie().length == puntos && c.getMetricasDisco().ciclo() > guardada.ciclo(), "");
        c.apagar();
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** Contadores de suspensión y reactivación en un escenario conocido de la etapa 16. */
    private static void probarSuspension() {
        r.grupo("Suspensiones y reactivaciones");
        long errores = Debug.getErrores();
        Cluster c = sistema(100, 100);
        PCB a = c.crearProceso(DatosProceso.ioBound("dma", 8, 60, 20_000, 3, 2, 600, 0));
        hasta(c, () -> a.getEstado() == EstadoProceso.BLOQUEADO);
        c.crearProceso(DatosProceso.cpuBound("calc", 5, 60, 20_000, 700, 0));
        r.verificar("antes de nada: 0 y 0", c.getMetricasDisco().suspensiones() == 0
                && c.getMetricasDisco().reactivaciones() == 0, "");
        hasta(c, () -> a.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO);
        MetricasDisco media = c.getMetricasDisco();
        r.verificar("tras escribirlo en el swap: 1 suspensión y 0 reactivaciones", media.suspensiones() == 1
                && media.reactivaciones() == 0 && media.atendidas() == 1,
                media.suspensiones() + " / " + media.reactivaciones() + " / " + media.atendidas());
        hasta(c, c::todosTerminados);
        MetricasDisco fin = c.getMetricasDisco();
        r.verificar("al final: 1 y 1, y las dos solicitudes de intercambio cuentan como atendidas y medidas",
                fin.suspensiones() == 1 && fin.reactivaciones() == 1 && fin.atendidas() == 2 && fin.solicitudesMedidas() == 2
                && c.getComputador(0).getKernel().getSuspensiones() == 1, fin.suspensiones() + " / " + fin.reactivaciones());
        c.apagar();
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** El comparador no toca la simulación abierta ni su debugger, y deja dicho qué números consumió. */
    private static void probarComparadorAislado() {
        r.grupo("El comparador no altera la simulación abierta");
        long errores = Debug.getErrores();
        CargaSimulacion carga = cargaDelEnunciado("SSTF");
        Cluster abierta = carga.armar(new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO)).cluster();
        abierta.cargarProcesosArchivo(carga.getProcesos().procesos());
        abierta.getReloj().ejecutarCiclos(40);
        boolean consolaEstaba = Debug.getConsola().isActiva();
        Debug.getConsola().setActiva(true);
        MetricasDisco antes = abierta.getMetricasDisco();
        long cicloAntes = abierta.getReloj().getCiclo();
        int entradas = abierta.getComputador(0).getSistemaArchivos().listarTodo().tamano();
        int vivos = abierta.getComputador(0).getKernel().procesosVivos();
        Debug.info("Prueba", "marca");
        String prefijo = prefijoDeCiclo();
        int ultimoNumero = GeneradorIds.siguienteId();
        int lineasLog = abierta.getLog().tamano();
        ComparadorPoliticas.Comparacion una = ComparadorPoliticas.comparar(carga, abierta);
        String[] consola = Debug.getConsola().copiarUltimos(3);
        r.verificar("la consola solo gana la línea del log y la del comparador: nada de las seis simulaciones",
                consola.length == 3 && consola[0].contains("marca") && consola[1].contains("usó los números de proceso")
                && consola[2].contains("comparadas 6"), String.join(" || ", consola));
        Debug.info("Prueba", "después");
        r.verificar("el ciclo que muestra el debugger no cambia y el hilo deja de estar en segundo plano",
                prefijoDeCiclo().equals(prefijo) && !Debug.isSegundoPlano(), prefijo + " -> " + prefijoDeCiclo());
        String esperado = "El comparador de políticas usó los números de proceso P" + (ultimoNumero + 1) + " a P"
                + (ultimoNumero + 48);
        r.verificar("el log de la simulación abierta dice: " + esperado, abierta.getLog().tamano() == lineasLog + 1
                && abierta.getLog().ultimos(1, "").endsWith(esperado), abierta.getLog().ultimos(1, ""));
        MetricasDisco despues = abierta.getMetricasDisco();
        r.verificar("la simulación abierta sigue igual: reloj, disco, árbol y procesos",
                abierta.getReloj().getCiclo() == cicloAntes && despues.desplazamientoTotal() == antes.desplazamientoTotal()
                && despues.atendidas() == antes.atendidas() && despues.politicaActiva() == PoliticaDiscoTipo.SSTF
                && abierta.getComputador(0).getSistemaArchivos().listarTodo().tamano() == entradas
                && abierta.getComputador(0).getKernel().procesosVivos() == vivos
                && carga.getProcesos().politicaDisco() == PoliticaDiscoTipo.SSTF, "");
        ComparadorPoliticas.Comparacion otra = ComparadorPoliticas.comparar(carga, null);
        r.verificar("repetir da lo mismo y la política inicial del JSON no influye", mismos(una, otra)
                && mismos(una, ComparadorPoliticas.comparar(cargaDelEnunciado("FIFO"), null)), "");
        abierta.iniciar();
        abierta.getReloj().setDuracionCicloMs(0);
        ComparadorPoliticas.Comparacion enMarcha = ComparadorPoliticas.comparar(carga, abierta);
        boolean terminoAbierta = abierta.esperarFin(1) || esperar(abierta);
        r.verificar("con la simulación abierta corriendo a la vez, ambos dan lo correcto", mismos(una, enMarcha)
                && terminoAbierta && abierta.getMetricasDisco().atendidas() == 8, "" + abierta.getMetricasDisco().atendidas());
        abierta.detener();
        abierta.esperarFin(5000);
        abierta.apagar();
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");

        boolean[] heredado = new boolean[1];
        Debug.setSegundoPlano(true);
        Debug.ok("Prueba17", "no debe verse");
        Thread hijo = new Thread(() -> heredado[0] = Debug.isSegundoPlano());
        hijo.start();
        long antesError = Debug.getErrores();
        Debug.error("Prueba17", "ERROR provocado en segundo plano");
        Debug.setSegundoPlano(false);
        unir(hijo);
        String[] ultima = Debug.getConsola().copiarUltimos(1);
        r.verificar("en segundo plano un ERROR se cuenta y se muestra, un OK no, y los hilos nuevos lo heredan",
                Debug.getErrores() == antesError + 1 && heredado[0] && ultima[0].contains("(en segundo plano) ERROR provocado")
                && !String.join("", Debug.getConsola().copiarUltimos(3)).contains("no debe verse"), ultima[0]);
        Debug.getConsola().setActiva(consolaEstaba);
    }

    /** Falta de un JSON, límite de ciclos y procesos que vencen o se rechazan. */
    private static void probarComparadorConProblemas() {
        r.grupo("Comparador: cargas incompletas, límite, vencidos y rechazos");
        long errores = Debug.getErrores();
        CargaSimulacion vacia = new CargaSimulacion();
        ComparadorPoliticas.Comparacion sinNada = ComparadorPoliticas.comparar(vacia, null);
        vacia.cargarSistemaArchivos(RUTA_FS);
        ComparadorPoliticas.Comparacion sinProcesos = ComparadorPoliticas.comparar(vacia, null);
        r.verificar("sin los dos JSON no compara y dice cuál falta", !sinNada.hecha() && sinNada.resultados().length == 0
                && !sinProcesos.hecha() && sinProcesos.motivo().contains("JSON de procesos"), "" + sinProcesos.motivo());
        ComparadorPoliticas.Comparacion corta = ComparadorPoliticas.comparar(cargaDelEnunciado("FIFO"), null, 30);
        ResultadoPolitica incompleta = corta.de(PoliticaDiscoTipo.FIFO);
        r.verificar("al llegar al límite de ciclos se marca como incompleta", corta.hecha() && !incompleta.completo()
                && incompleta.cicloFinal() == 30 && incompleta.etiqueta().contains("(incompleta)"), incompleta.etiqueta());
        String texto = leerTexto(RUTA_PROCESOS).replaceFirst("\"deadline\": 500", "\"deadline\": 10")
                .replace("/usuarios/video.mp4", "/usuarios/no_existe.mp4");
        CargaSimulacion conFallos = new CargaSimulacion();
        conFallos.cargarSistemaArchivos(RUTA_FS);
        conFallos.usarProcesos(ArchivoProcesos.interpretar(texto));
        ResultadoPolitica fallos = ComparadorPoliticas.comparar(conFallos, null).de(PoliticaDiscoTipo.FIFO);
        r.verificar("informa vencidos y rechazados, también en la leyenda", fallos.completo() && fallos.vencidos() == 1
                && fallos.rechazados() == 1 && fallos.orden().length == 6
                && fallos.etiqueta().equals("FIFO (1 vencidos, 1 rechazados)"), fallos.etiqueta() + " / " + fallos.orden().length);
        r.verificar("cero ERROR en esos casos", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.verificar("una carga nula o un límite de 0 son ERROR", conError(() -> !ComparadorPoliticas.comparar(null, null).hecha())
                && conError(() -> !ComparadorPoliticas.comparar(cargaDelEnunciado("FIFO"), null, 0).hecha()), "");
    }

    /** Las métricas del Proyecto 1 dan los mismos valores calculados a mano. */
    private static void probarMetricasDelProyecto1() {
        r.grupo("Métricas del Proyecto 1");
        long errores = Debug.getErrores();
        Cluster c = new Cluster(configuracionSinDisco(), log);
        c.crearProceso(DatosProceso.cpuBound("a", 5, 10, 100, 4, 0));
        c.crearProceso(DatosProceso.cpuBound("b", 5, 10, 100, 4, 0));
        c.getReloj().ejecutarCiclos(10);
        Metricas m = CalculadorMetricas.computador(c, 0);
        r.verificar("2 terminados, ninguno vencido ni vivo y 0,2 procesos por ciclo", m.terminadosOk() == 2
                && m.vencidos() == 0 && m.vivos() == 0 && casi(m.throughput(), 0.2), "" + m);
        r.verificar("CPU: 100 % ocupada, 80 % usuario y 20 % sistema", casi(m.utilizacion(), 100)
                && casi(m.porcentajeUsuario(), 80) && casi(m.porcentajeSO(), 20), "" + m);
        r.verificar("respuesta media 3,5, cumplimiento 100 % y equidad 0,9", casi(m.tiempoRespuesta(), 3.5)
                && casi(m.cumplimiento(), 100) && casi(m.equidad(), 0.9), "" + m);
        r.verificar("el global de un computador coincide con el suyo", CalculadorMetricas.global(c).utilizacion() == m.utilizacion()
                && CalculadorMetricas.global(c).equidad() == m.equidad(), "");
        r.verificar("sin disco no hay métricas de disco", c.getMetricasDisco() == null, "");
        c.apagar();
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** El gráfico se arma con una o seis líneas, lleva las marcas y se exporta a PNG. */
    private static void probarGrafico() {
        r.grupo("Gráfico y exportación a PNG");
        long errores = Debug.getErrores();
        ResultadoPolitica[] resultados = ComparadorPoliticas.comparar(cargaDelEnunciado("FIFO"), null).resultados();
        JFreeChart comparacion = GraficoDesplazamiento.crearComparacion(resultados);
        XYDataset datos = comparacion.getXYPlot().getDataset();
        boolean lineas = datos.getSeriesCount() == 6;
        for (int i = 0; lineas && i < 6; i++) {
            lineas = datos.getSeriesKey(i).equals(resultados[i].politica().getNombre())
                    && datos.getItemCount(i) == resultados[i].metricas().serie().length
                    && datos.getYValue(i, datos.getItemCount(i) - 1) == resultados[i].metricas().desplazamientoTotal();
        }
        r.verificar("comparación: una línea por política, con todos sus puntos y su total", lineas, datos.getSeriesCount() + "");
        Cluster c = armar(cargaDelEnunciado("FIFO"));
        c.cargarProcesosArchivo(ArchivoProcesos.leer(RUTA_PROCESOS).procesos());
        c.getReloj().ejecutarCiclos(60);
        c.getComputador(0).getPlanificadorDisco().cambiarPolitica(PoliticaDiscoTipo.SSTF, 60);
        c.getReloj().ejecutarCiclos(60);
        c.getComputador(0).getPlanificadorDisco().cambiarPolitica(PoliticaDiscoTipo.C_SCAN, 120);
        hasta(c, c::todosTerminados);
        MetricasDisco m = c.getMetricasDisco();
        ValueMarker[] marcas = GraficoDesplazamiento.marcas(m);
        JFreeChart simulacion = GraficoDesplazamiento.crear(m);
        boolean colocadas = marcas.length == 2;
        for (int i = 0; colocadas && i < marcas.length; i++) {
            colocadas = marcas[i].getValue() == m.cambios()[i].ciclo() && simulacion.getXYPlot().removeDomainMarker(marcas[i]);
        }
        r.verificar("simulación: una línea y una marca en el ciclo de cada cambio", colocadas
                && simulacion.getXYPlot().getDataset().getSeriesCount() == 1 && marcas[0].getValue() == 61
                && marcas[0].getLabel().equals("FIFO → SSTF"), marcas.length + " marcas");
        c.apagar();
        Path carpeta;
        try {
            carpeta = Files.createTempDirectory("avilaos17");
        } catch (IOException e) {
            r.verificar("carpeta temporal", false, e.getMessage());
            return;
        }
        File uno = carpeta.resolve("sub").resolve("comparacion.png").toFile();
        File dos = carpeta.resolve("simulacion.png").toFile();
        r.verificar("exporta la comparación creando la carpeta", GraficoDesplazamiento.exportarPNG(comparacion, uno)
                && esPNG(uno), "");
        r.verificar("exporta el gráfico de la simulación con sus marcas",
                GraficoDesplazamiento.exportarPNG(GraficoDesplazamiento.crear(m), dos) && esPNG(dos), "");
        DiscoSD d = new DiscoSD("SD-vacio", 20, 0, 0, 1);
        MetricasDisco vacias = new PlanificadorDisco("Plan-vacio", d, PoliticaDiscoTipo.FIFO, new LogEventos(5)).fotografiar(0, 0);
        r.verificar("sin datos también dibuja y exporta", GraficoDesplazamiento.exportarPNG(GraficoDesplazamiento.crear(vacias), dos)
                && GraficoDesplazamiento.exportarPNG(GraficoDesplazamiento.crear((MetricasDisco) null), dos)
                && GraficoDesplazamiento.exportarPNG(GraficoDesplazamiento.crearComparacion(new ResultadoPolitica[0]), dos), "");
        PanelGraficoDisco panel = new PanelGraficoDisco();
        panel.mostrar(null);
        boolean sinGrafico = panel.getGrafico() == null;
        panel.mostrar(m);
        JFreeChart primero = panel.getGrafico();
        panel.mostrarComparacion(resultados);
        r.verificar("el panel se puede incrustar: sin datos queda vacío y luego muestra o reemplaza el gráfico", sinGrafico
                && primero != null && panel.getGrafico() != primero
                && panel.getGrafico().getXYPlot().getDataset().getSeriesCount() == 6, "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        File imposible = new File(dos, "no_se_puede.png");
        r.verificar("una ruta imposible o un gráfico nulo devuelven false con ERROR",
                conError(() -> !GraficoDesplazamiento.exportarPNG(comparacion, imposible))
                && conError(() -> !GraficoDesplazamiento.exportarPNG(null, dos)), "");
        uno.delete();
        uno.getParentFile().delete();
        dos.delete();
        carpeta.toFile().delete();
    }

    // ---------------------------------------------------------------- ayudantes

    private static CargaSimulacion cargaDelEnunciado(String politica) {
        CargaSimulacion carga = new CargaSimulacion();
        carga.usarSistemaArchivos(ArchivoSistemaArchivos.leer(RUTA_FS));
        carga.usarProcesos(ArchivoProcesos.interpretar(leerTexto(RUTA_PROCESOS).replace("\"FIFO\"", "\"" + politica + "\"")));
        return carga;
    }

    private static Cluster armar(CargaSimulacion carga) {
        return carga.armar(log).cluster();
    }

    /** Sistema como el de la etapa 16: cabezal a 10 por ciclo, sin espera anticipatoria y sin procesos. */
    private static Cluster sistema(int ram, int swap) {
        DatosProcesos datos = ArchivoProcesos.interpretar("{\"globalConfig\":{\"cycleDurationMs\":0,\"swapSizeBlocks\":" + swap
                + ",\"initialDiskPolicy\":\"FIFO\",\"headSpeed\":10,\"anticipationCycles\":0},\"computer\":{\"ramCapacity\":"
                + ram + ",\"policy\":\"FCFS\",\"quantum\":0},\"processes\":[]}");
        CargaSimulacion carga = new CargaSimulacion();
        carga.usarSistemaArchivos(ArchivoSistemaArchivos.leer(RUTA_FS));
        carga.usarProcesos(datos);
        return armar(carga);
    }

    private static ConfiguracionSistema configuracionSinDisco() {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(256, PoliticaTipo.FCFS, 3));
        return new ConfiguracionSistema(true, 0, new CondicionesParada(0, false, 1, false, 1), 1, 0, computadores,
                new ListaEnlazada<>(), new ListaEnlazada<>(), 0);
    }

    /** Relaciones que toda foto debe cumplir, y que nada retroceda respecto de la anterior. */
    private static boolean coherente(MetricasDisco foto, MetricasDisco previa) {
        long suma = 0;
        for (long cantidad : foto.atendidasPorPolitica()) {
            suma += cantidad;
        }
        PuntoDesplazamiento[] serie = foto.serie();
        PuntoDesplazamiento ultimo = serie[serie.length - 1];
        return suma == foto.atendidas() && ultimo.acumulado() == foto.desplazamientoTotal() && ultimo.ciclo() == foto.ciclo()
                && noDecrece(serie) && foto.reactivaciones() <= foto.suspensiones()
                && foto.suspensiones() + foto.reactivaciones() <= foto.atendidas()
                && (previa == null || (foto.ciclo() >= previa.ciclo() && foto.atendidas() >= previa.atendidas()
                && foto.desplazamientoTotal() >= previa.desplazamientoTotal() && foto.suspensiones() >= previa.suspensiones()));
    }

    private static boolean noDecrece(PuntoDesplazamiento[] serie) {
        for (int i = 1; i < serie.length; i++) {
            if (serie[i].ciclo() <= serie[i - 1].ciclo() || serie[i].acumulado() < serie[i - 1].acumulado()) {
                return false;
            }
        }
        return true;
    }

    /** Valor de la línea del gráfico en un ciclo, uniendo los puntos vecinos. */
    private static double valorEn(PuntoDesplazamiento[] serie, long ciclo) {
        int bajo = 0;
        int alto = serie.length - 1;
        while (bajo < alto) {
            int medio = (bajo + alto) / 2;
            if (serie[medio].ciclo() < ciclo) {
                bajo = medio + 1;
            } else {
                alto = medio;
            }
        }
        PuntoDesplazamiento b = serie[bajo];
        if (b.ciclo() == ciclo || bajo == 0) {
            return b.acumulado();
        }
        PuntoDesplazamiento a = serie[bajo - 1];
        return a.acumulado() + (double) (b.acumulado() - a.acumulado()) * (ciclo - a.ciclo()) / (b.ciclo() - a.ciclo());
    }

    /** Indica si dos comparaciones dieron el mismo orden, desplazamiento, espera, serie y ciclo final. */
    private static boolean mismos(ComparadorPoliticas.Comparacion a, ComparadorPoliticas.Comparacion b) {
        if (!a.hecha() || !b.hecha()) {
            return false;
        }
        for (PoliticaDiscoTipo politica : PoliticaDiscoTipo.values()) {
            ResultadoPolitica x = a.de(politica);
            ResultadoPolitica y = b.de(politica);
            if (!x.bloquesEnOrden().equals(y.bloquesEnOrden()) || x.cicloFinal() != y.cicloFinal()
                    || x.metricas().desplazamientoTotal() != y.metricas().desplazamientoTotal()
                    || x.metricas().esperaPromedioTotal() != y.metricas().esperaPromedioTotal()
                    || x.metricas().serie().length != y.metricas().serie().length) {
                return false;
            }
        }
        return true;
    }

    /** El prefijo [Cnnnn] de la última línea de la consola. */
    private static String prefijoDeCiclo() {
        String ultima = Debug.getConsola().copiarUltimos(1)[0];
        return ultima.substring(0, ultima.indexOf(']') + 1);
    }

    private static boolean esPNG(File archivo) {
        try {
            byte[] datos = Files.readAllBytes(archivo.toPath());
            return datos.length > 1000 && (datos[0] & 0xFF) == 0x89 && datos[1] == 'P' && datos[2] == 'N' && datos[3] == 'G';
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean esperar(Cluster c) {
        long limite = System.currentTimeMillis() + 20_000;
        while (!c.todosTerminados() && System.currentTimeMillis() < limite) {
            Thread.onSpinWait();
        }
        return c.todosTerminados();
    }

    private static void unir(Thread hilo) {
        try {
            hilo.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean casi(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static void hasta(Cluster c, Comprobacion condicion) {
        for (int i = 0; i < 20_000 && !condicion.cumple(); i++) {
            c.getReloj().ejecutarCiclos(1);
        }
    }

    private static void encolar(PlanificadorDisco p, int proceso, int bloque, long ciclo) {
        p.encolar(proceso, "P" + proceso, TipoOperacionES.LEER, "/x", new int[]{bloque}, ciclo);
    }

    private static void ticks(DiscoSD disco, long desde, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            disco.tick(desde + i);
        }
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
}
