package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.EstadoProceso;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.Metricas;
import avilaos.metricas.MetricasBuffer;
import avilaos.metricas.MuestraUtilizacion;
import avilaos.metricas.RegistroUtilizacion;
import avilaos.so.DatosProceso;
import avilaos.so.PCB;
import avilaos.sync.DatosBuffer;
import avilaos.vista.GraficoUtilizacion;
import avilaos.vista.ReporteTerminal;
import java.io.File;
import java.io.IOException;

/** Pruebas de terminal de la etapa 7: métricas por computador y globales, métricas de buffers y serie de utilización. */
public final class PruebasPaso7 {

    private static final int DEADLINE_LARGO = 100000;
    private static final double TOLERANCIA = 1e-9;
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso7() {
    }

    /** Ejecuta todas las pruebas de la etapa 7 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(7, "Etapa 7: métricas y gráfico de utilización de CPU");
        probarIndiceJain();
        probarProcesoSolo();
        probarEquidadNormalizada();
        probarDeadlinesYRespuesta();
        probarMetricasDeBuffer();
        probarBloqueoAbiertoEnMetricas();
        probarSerieDeUtilizacion();
        probarExportacionPNG();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El índice de Jain vale 1 con valores iguales, baja con la desigualdad y no existe sin datos. */
    private static void probarIndiceJain() {
        r.grupo("Índice de Jain");
        r.verificar("valores iguales -> 1", cerca(CalculadorMetricas.indiceJain(new double[]{2, 2, 2, 2}), 1.0),
                String.valueOf(CalculadorMetricas.indiceJain(new double[]{2, 2, 2, 2})));
        r.verificar("{1, 3} -> 16 / (2 * 10) = 0.8", cerca(CalculadorMetricas.indiceJain(new double[]{1, 3}), 0.8),
                String.valueOf(CalculadorMetricas.indiceJain(new double[]{1, 3})));
        r.verificar("un solo valor -> 1", cerca(CalculadorMetricas.indiceJain(new double[]{7}), 1.0),
                String.valueOf(CalculadorMetricas.indiceJain(new double[]{7})));
        r.verificar("sin valores -> sin dato", CalculadorMetricas.indiceJain(new double[0]) == Metricas.SIN_DATO,
                String.valueOf(CalculadorMetricas.indiceJain(new double[0])));
    }

    /** Un CPU bound solo: 1 ciclo de despacho + 10 de usuario = 11 ocupados de 20 ciclos; el otro computador no hace nada. */
    private static void probarProcesoSolo() {
        r.grupo("Un proceso solo en el computador 0 (20 ciclos)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3);
        PCB p = r.registrar(c.crearProceso(DatosProceso.cpuBound("solo", 5, 10, DEADLINE_LARGO, 10, 0)));
        PruebasPaso5.correrEnSilencio(c, 20);
        Metricas m0 = CalculadorMetricas.computador(c, 0);
        Metricas m1 = CalculadorMetricas.computador(c, 1);
        Metricas global = CalculadorMetricas.global(c);
        r.verificar("termina bien y se cuenta 1 terminado, 0 vencidos, 0 vivos", PruebasPaso5.terminoBien(p)
                && m0.terminadosOk() == 1 && m0.vencidos() == 0 && m0.vivos() == 0, m0.toString());
        r.verificar("utilización 11/20 = 55% (50% usuario + 5% SO)", cerca(m0.utilizacion(), 55.0)
                && cerca(m0.porcentajeUsuario(), 50.0) && cerca(m0.porcentajeSO(), 5.0), m0.toString());
        r.verificar("throughput 1/20 = 0.05 procesos por ciclo", cerca(m0.throughput(), 0.05), String.valueOf(m0.throughput()));
        r.verificar("tiempo de respuesta = llegada a primera ejecución del PCB", p.getTiempoRespuesta() >= 0
                && cerca(m0.tiempoRespuesta(), p.getTiempoRespuesta()), m0.tiempoRespuesta() + " vs " + p.getTiempoRespuesta());
        r.verificar("deadlines 100% y equidad 1 (un solo proceso)", cerca(m0.cumplimiento(), 100.0) && cerca(m0.equidad(), 1.0),
                m0.cumplimiento() + " / " + m0.equidad());
        r.verificar("el computador 1 ocioso: 0% de CPU, 0 throughput y sin datos de respuesta, deadlines ni equidad",
                cerca(m1.utilizacion(), 0.0) && cerca(m1.throughput(), 0.0) && m1.tiempoRespuesta() == Metricas.SIN_DATO
                && m1.cumplimiento() == Metricas.SIN_DATO && m1.equidad() == Metricas.SIN_DATO, m1.toString());
        r.verificar("la utilización global es el promedio de las dos CPUs (27.5%)", cerca(global.utilizacion(), 27.5)
                && global.terminadosOk() == 1, global.toString());
        c.apagar();
    }

    /** Procesos de distinto tamaño tratados igual salen parejos al normalizar el retorno por las instrucciones. */
    private static void probarEquidadNormalizada() {
        r.grupo("Equidad normalizada (tiempo de retorno / instrucciones)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3);
        PCB chico = r.registrar(c.crearProceso(DatosProceso.cpuBound("chico", 5, 10, DEADLINE_LARGO, 10, 0)));
        PCB grande = r.registrar(c.crearProceso(DatosProceso.cpuBound("grande", 5, 10, DEADLINE_LARGO, 40, 1)));
        PruebasPaso5.correrEnSilencio(c, 60);
        Metricas global = CalculadorMetricas.global(c);
        double sinNormalizar = CalculadorMetricas.indiceJain(new double[]{chico.getTiempoRetorno(), grande.getTiempoRetorno()});
        r.verificar("ambos terminan bien, cada uno solo en su computador", PruebasPaso5.terminoBien(chico)
                && PruebasPaso5.terminoBien(grande), chico + " | " + grande);
        r.verificar("con el retorno normalizado la equidad es casi perfecta (>= 0.99)", global.equidad() >= 0.99,
                String.valueOf(global.equidad()));
        r.verificar("sin normalizar parecería injusto (< 0.8): el tamaño distorsionaría la métrica", sinNormalizar < 0.8,
                String.valueOf(sinNormalizar));
        c.apagar();
    }

    /** Un proceso que vence su deadline sin haber corrido baja el cumplimiento pero no entra en el tiempo de respuesta. */
    private static void probarDeadlinesYRespuesta() {
        r.grupo("Deadlines vencidos y tiempo de respuesta");
        Cluster c = cluster(PoliticaTipo.FCFS, 3);
        PCB largo = r.registrar(c.crearProceso(DatosProceso.cpuBound("largo", 5, 10, DEADLINE_LARGO, 30, 0)));
        PCB urgente = r.registrar(c.crearProceso(DatosProceso.cpuBound("urgente", 5, 10, 8, 5, 0)));
        PruebasPaso5.correrEnSilencio(c, 50);
        Metricas m0 = CalculadorMetricas.computador(c, 0);
        r.verificar("'urgente' espera detrás de 'largo' y muere por deadline sin haber corrido", urgente.isDeadlineVencido()
                && urgente.getTiempoRespuesta() < 0 && PruebasPaso5.terminoBien(largo), largo + " | " + urgente);
        r.verificar("1 terminado bien y 1 vencido", m0.terminadosOk() == 1 && m0.vencidos() == 1, m0.toString());
        r.verificar("cumplimiento de deadlines 50%", cerca(m0.cumplimiento(), 50.0), String.valueOf(m0.cumplimiento()));
        r.verificar("el tiempo de respuesta solo cuenta a 'largo' (el que nunca corrió se excluye)",
                cerca(m0.tiempoRespuesta(), largo.getTiempoRespuesta()), m0.tiempoRespuesta() + " vs " + largo.getTiempoRespuesta());
        r.verificar("la equidad solo usa a los que terminaron bien (1 proceso -> 1)", cerca(m0.equidad(), 1.0),
                String.valueOf(m0.equidad()));
        c.apagar();
    }

    /** Producidos, consumidos y tiempo promedio bloqueado en semáforos de un buffer con productor y consumidor locales. */
    private static void probarMetricasDeBuffer() {
        r.grupo("Métricas de productor-consumidor");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, PruebasPaso5.buffer("B1", 3, 0));
        PCB productor = r.registrar(c.crearProceso(DatosProceso.productor("Sensor", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Monitor", 3, 10, DEADLINE_LARGO, "B1", 1, 4, 0)));
        PruebasPaso5.correrEnSilencio(c, 500);
        ListaEnlazada<MetricasBuffer> filas = CalculadorMetricas.buffers(c);
        MetricasBuffer b1 = filas.primero();
        r.verificar("ambos terminan bien y hay una fila de métricas por buffer", PruebasPaso5.terminoBien(productor)
                && PruebasPaso5.terminoBien(consumidor) && filas.tamano() == 1 && b1 != null, productor + " | " + consumidor);
        if (b1 == null) {
            c.apagar();
            return;
        }
        double esperado = (productor.getCiclosBloqueadoSemaforo() + consumidor.getCiclosBloqueadoSemaforo()) / 2.0;
        r.verificar("producidos = consumidos = 4 con 2 procesos asociados", b1.producidos() == 4 && b1.consumidos() == 4
                && b1.procesos() == 2, b1.toString());
        r.verificar("el productor se bloquea (4 elementos en capacidad 3) y el promedio es la media de los dos",
                productor.getCiclosBloqueadoSemaforo() > 0 && b1.bloqueadoPromedio() > 0 && cerca(b1.bloqueadoPromedio(), esperado),
                b1.bloqueadoPromedio() + " vs " + esperado);
        r.seccion(ReporteTerminal.tablaMetricas(c).stripTrailing());
        c.apagar();
    }

    /** Un proceso que sigue bloqueado en un semáforo también cuenta su espera en la métrica del buffer. */
    private static void probarBloqueoAbiertoEnMetricas() {
        r.grupo("Tiempo bloqueado de procesos que siguen bloqueados");
        Cluster c = cluster(PoliticaTipo.FCFS, 3, PruebasPaso5.buffer("B1", 3, 0));
        PCB consumidor = r.registrar(c.crearProceso(DatosProceso.consumidor("Esperando", 3, 10, DEADLINE_LARGO, "B1", 1, 2, 0)));
        PruebasPaso5.correrEnSilencio(c, 20);
        double antes = CalculadorMetricas.buffers(c).primero().bloqueadoPromedio();
        PruebasPaso5.correrEnSilencio(c, 10);
        double despues = CalculadorMetricas.buffers(c).primero().bloqueadoPromedio();
        r.verificar("el consumidor sigue bloqueado en el semáforo y el PCB aún no acumuló nada",
                consumidor.getEstado() == EstadoProceso.BLOQUEADO && consumidor.getCiclosBloqueadoSemaforo() == 0,
                consumidor.toString() + " acumulado " + consumidor.getCiclosBloqueadoSemaforo());
        r.verificar("la métrica ya cuenta su espera abierta (> 0)", antes > 0, String.valueOf(antes));
        r.verificar("10 ciclos más bloqueado suman exactamente 10 al promedio", cerca(despues - antes, 10.0),
                antes + " -> " + despues);
        r.verificar("coincide con la espera calculada por el PCB hasta el ciclo actual",
                cerca(despues, consumidor.getCiclosBloqueadoSemaforoHasta(c.getReloj().getCiclo())), String.valueOf(despues));
        c.apagar();
    }

    /** La serie del gráfico usa una ventana de 20 ciclos: se llena al principio y se vacía cuando el proceso termina. */
    private static void probarSerieDeUtilizacion() {
        r.grupo("Serie de utilización (ventana de " + RegistroUtilizacion.VENTANA + " ciclos)");
        Cluster c = cluster(PoliticaTipo.FCFS, 3);
        r.registrar(c.crearProceso(DatosProceso.cpuBound("serie", 5, 10, DEADLINE_LARGO, 25, 0)));
        PruebasPaso5.correrEnSilencio(c, 30);
        ListaEnlazada<MuestraUtilizacion> pc0 = c.getUtilizacion().muestras(0);
        ListaEnlazada<MuestraUtilizacion> pc1 = c.getUtilizacion().muestras(1);
        r.verificar("una muestra por ciclo y por computador", pc0.tamano() == 30 && pc1.tamano() == 30,
                pc0.tamano() + " / " + pc1.tamano());
        r.verificar("ciclo 5: ventana aún incompleta, 5 de 5 ocupados = 100%", cerca(en(pc0, 5), 100.0), String.valueOf(en(pc0, 5)));
        r.verificar("ciclo 26 (último ocupado): 20 de 20 = 100%", cerca(en(pc0, 26), 100.0), String.valueOf(en(pc0, 26)));
        r.verificar("ciclo 27: la ventana pierde un ciclo ocupado y gana uno ocioso = 95%", cerca(en(pc0, 27), 95.0),
                String.valueOf(en(pc0, 27)));
        r.verificar("ciclo 30: 16 ocupados de los últimos 20 = 80%", cerca(en(pc0, 30), 80.0), String.valueOf(en(pc0, 30)));
        r.verificar("el computador 1 siempre en 0% y todos los valores dentro de 0..100", cerca(maximo(pc1), 0.0)
                && maximo(pc0) <= 100.0 && minimo(pc0) >= 0.0, maximo(pc1) + " / " + minimo(pc0) + ".." + maximo(pc0));
        c.apagar();
    }

    /** El gráfico se exporta como un PNG no vacío en la carpeta pedida. */
    private static void probarExportacionPNG() {
        r.grupo("Exportación del gráfico a PNG");
        Cluster c = cluster(PoliticaTipo.FCFS, 3);
        r.registrar(c.crearProceso(DatosProceso.cpuBound("grafico", 5, 10, DEADLINE_LARGO, 15, 0)));
        PruebasPaso5.correrEnSilencio(c, 25);
        File archivo = null;
        try {
            archivo = File.createTempFile("utilizacion_prueba", ".png");
            boolean exportado = GraficoUtilizacion.exportarPNG(c.getUtilizacion(), archivo.getPath());
            r.verificar("exportarPNG devuelve true y el archivo tiene contenido", exportado && archivo.length() > 0,
                    "tamaño " + archivo.length());
            r.verificar("en una carpeta inexistente la crea y guarda ahí", exportarEnCarpetaNueva(c),
                    "no se pudo crear la carpeta o el archivo");
        } catch (IOException e) {
            r.verificar("crear el archivo temporal", false, e.getMessage());
        } finally {
            if (archivo != null) {
                archivo.delete();
            }
        }
        c.apagar();
    }

    /** Exporta a una carpeta que no existe, comprueba el archivo y borra lo creado. */
    private static boolean exportarEnCarpetaNueva(Cluster c) {
        File carpeta = new File(System.getProperty("java.io.tmpdir"), "avilaos_prueba_" + System.nanoTime());
        File archivo = new File(carpeta, "grafico.png");
        boolean correcto = GraficoUtilizacion.exportarPNG(c.getUtilizacion(), archivo.getPath()) && archivo.length() > 0;
        archivo.delete();
        carpeta.delete();
        return correcto;
    }

    /** Porcentaje de la muestra del ciclo indicado, o -1 si no existe. */
    private static double en(ListaEnlazada<MuestraUtilizacion> muestras, long ciclo) {
        Nodo<MuestraUtilizacion> nodo = muestras.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().ciclo() == ciclo) {
                return nodo.getDato().porcentaje();
            }
            nodo = nodo.getSiguiente();
        }
        return -1;
    }

    private static double maximo(ListaEnlazada<MuestraUtilizacion> muestras) {
        double mayor = 0;
        Nodo<MuestraUtilizacion> nodo = muestras.getCabeza();
        while (nodo != null) {
            mayor = Math.max(mayor, nodo.getDato().porcentaje());
            nodo = nodo.getSiguiente();
        }
        return mayor;
    }

    private static double minimo(ListaEnlazada<MuestraUtilizacion> muestras) {
        double menor = 100;
        Nodo<MuestraUtilizacion> nodo = muestras.getCabeza();
        while (nodo != null) {
            menor = Math.min(menor, nodo.getDato().porcentaje());
            nodo = nodo.getSiguiente();
        }
        return menor;
    }

    private static boolean cerca(double obtenido, double esperado) {
        return Math.abs(obtenido - esperado) < TOLERANCIA;
    }

    /** Clúster de 2 computadores de 1024: el 0 con la política indicada, el 1 FCFS; latencia de red 1, sin envejecimiento. */
    private static Cluster cluster(PoliticaTipo politica, int quantum, DatosBuffer... buffers) {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        computadores.agregarFinal(new ConfigComputador(1024, politica, quantum));
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
