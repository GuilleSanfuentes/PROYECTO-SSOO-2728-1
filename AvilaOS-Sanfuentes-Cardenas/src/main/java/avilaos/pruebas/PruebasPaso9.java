package avilaos.pruebas;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigComputador;
import avilaos.config.CondicionesParada;
import avilaos.config.NuevaConfiguracion;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.EstadoProceso;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.sync.DatosBuffer;
import avilaos.vista.GraficoUtilizacion;
import avilaos.vista.gui.FilaBuffer;
import avilaos.vista.gui.FilaProceso;
import avilaos.vista.gui.FotoCluster;
import avilaos.vista.gui.ModeloTablaBuffers;
import avilaos.vista.gui.ModeloTablaPCB;
import avilaos.vista.gui.ModeloTablaTexto;
import avilaos.vista.gui.PanelGrafico;
import avilaos.vista.gui.PanelMetricas;
import avilaos.vista.gui.Validador;
import avilaos.vista.gui.FiltroEntrada;
import java.io.File;
import javax.swing.JTextField;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.jfree.chart.JFreeChart;

/** Pruebas sin pantalla de la etapa 9: validador, modelos de tabla, carga estricta de configuración y fotos de la GUI. */
public final class PruebasPaso9 {

    private static final String BASE_VALIDA = """
            {
              "debugger": true,
              "simulacion": { "duracionCicloMs": 500 },
              "computadores": [
                { "ram": 1024, "politica": "FCFS", "quantum": 3 },
                { "ram": 512, "politica": "ROUND_ROBIN", "quantum": 3 }
              ],
              "buffers": [ { "nombre": "B1", "capacidad": 5, "computador": 0 } ],
              "procesos": []
            }""";

    private static Resultados r;
    private static Path carpeta;

    private PruebasPaso9() {
    }

    /** Ejecuta todas las pruebas de la etapa 9 e imprime su resumen. */
    public static void ejecutar(LogEventos log) {
        r = new Resultados(9, "Etapa 9: interfaz gráfica (validador, modelos, carga estricta y fotos)");
        try {
            carpeta = Files.createTempDirectory("avilaos_paso9");
        } catch (IOException e) {
            r.grupo("Carpeta temporal");
            r.verificar("crear carpeta temporal", false, e.getMessage());
            r.cerrar();
            return;
        }
        probarValidador();
        probarModelosDeTabla();
        probarEntradasRestringidas();
        probarCargaEstricta();
        probarFotoYPaneles(log);
        probarNuevaConfiguracion();
        borrarCarpeta();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El validador acepta enteros en rango y explica el motivo exacto de cada rechazo. */
    private static void probarValidador() {
        r.grupo("Validador de enteros");
        Validador.ResultadoEntero ok = Validador.entero(" 42 ", "Quantum", 1, 100);
        r.verificar("42 con espacios es válido", ok.valido() && ok.valor() == 42, ok.toString());
        r.verificar("el mínimo exacto es válido", Validador.entero("1", "Quantum", 1, 100).valido(), "");
        r.verificar("el máximo exacto es válido", Validador.entero("100", "Quantum", 1, 100).valido(), "");
        r.verificar("negativo válido si el rango lo permite", Validador.entero("-3", "X", -5, 5).valor() == -3, "");
        r.verificar("vacío -> motivo 'vacío'", motivo("   ", "vacío"), Validador.entero("   ", "Quantum", 1, 100).motivo());
        r.verificar("nulo -> motivo 'vacío'", motivo(null, "vacío"), Validador.entero(null, "Quantum", 1, 100).motivo());
        r.verificar("letras -> 'no es un número entero'", motivo("abc", "no es un número entero"),
                Validador.entero("abc", "Quantum", 1, 100).motivo());
        r.verificar("decimal -> 'no es un número entero'", motivo("3.5", "no es un número entero"),
                Validador.entero("3.5", "Quantum", 1, 100).motivo());
        r.verificar("enorme -> 'no es un número entero'", motivo("99999999999999999999", "no es un número entero"),
                Validador.entero("99999999999999999999", "Quantum", 1, 100).motivo());
        r.verificar("bajo el mínimo -> 'fuera del rango'", motivo("0", "fuera del rango 1..100"),
                Validador.entero("0", "Quantum", 1, 100).motivo());
        r.verificar("sobre el máximo -> 'fuera del rango'", motivo("101", "fuera del rango 1..100"),
                Validador.entero("101", "Quantum", 1, 100).motivo());
        r.verificar("el motivo nombra el campo", motivo("x", "Quantum:"), Validador.entero("x", "Quantum", 1, 100).motivo());
        r.verificar("fuera de int pero dentro de long -> fuera del rango", motivo("3000000000", "fuera del rango"),
                Validador.entero("3000000000", "X", 0, Integer.MAX_VALUE).motivo());
    }

    /** Los campos solo aceptan los caracteres válidos y hasta su largo máximo, también al pegar texto. */
    private static void probarEntradasRestringidas() {
        r.grupo("Entradas restringidas (solo se puede escribir lo válido)");
        JTextField numero = new JTextField();
        FiltroEntrada.soloDigitos(numero, 3);
        numero.setText("1a2-3");
        r.verificar("campo numérico descarta letras y signos: '1a2-3' -> '123'", "123".equals(numero.getText()), numero.getText());
        numero.setText("98765");
        r.verificar("campo numérico respeta el máximo de 3 cifras", "987".equals(numero.getText()), numero.getText());
        numero.setText("-5");
        r.verificar("no se pueden escribir negativos", "5".equals(numero.getText()), numero.getText());
        JTextField nombre = new JTextField();
        FiltroEntrada.nombre(nombre);
        nombre.setText("Calc 1!@#$");
        r.verificar("nombre de proceso: letras, números y espacios", "Calc 1".equals(nombre.getText()), nombre.getText());
        nombre.setText("x".repeat(40));
        r.verificar("nombre de proceso: máximo " + FiltroEntrada.LARGO_NOMBRE + " caracteres",
                nombre.getText().length() == FiltroEntrada.LARGO_NOMBRE, String.valueOf(nombre.getText().length()));
        JTextField buffer = new JTextField();
        FiltroEntrada.identificador(buffer);
        buffer.setText("B 1#");
        r.verificar("nombre de buffer: sin espacios ni símbolos ('B 1#' -> 'B1')", "B1".equals(buffer.getText()), buffer.getText());
    }

    /** Los modelos de tabla informan filas, columnas y valores, y avisan a la tabla cuando cambian. */
    private static void probarModelosDeTabla() {
        r.grupo("Modelos de tabla");
        FilaProceso fila = new FilaProceso(7, "P7-H1", "Calc", TipoProceso.CPU_BOUND, EstadoProceso.BLOQUEADO,
                5, 3, 4, 10, 20, 30, false, "E/S: 3 ciclos", 1, 64, 4, 0, 2, -1, "", 0, 0, 0);
        ModeloTablaPCB sinMotivo = new ModeloTablaPCB(false);
        ModeloTablaPCB conMotivo = new ModeloTablaPCB(true);
        r.verificar("sin motivo: 7 columnas", sinMotivo.getColumnCount() == 7, String.valueOf(sinMotivo.getColumnCount()));
        r.verificar("con motivo: 8 columnas", conMotivo.getColumnCount() == 8, String.valueOf(conMotivo.getColumnCount()));
        r.verificar("vacío: 0 filas", conMotivo.getRowCount() == 0, "");
        AtomicInteger avisos = new AtomicInteger();
        conMotivo.addTableModelListener(e -> avisos.incrementAndGet());
        conMotivo.setFilas(new FilaProceso[]{fila});
        r.verificar("setFilas avisa a la tabla una vez", avisos.get() == 1, String.valueOf(avisos.get()));
        r.verificar("1 fila tras setFilas", conMotivo.getRowCount() == 1, "");
        r.verificar("columna 0: etiqueta y nombre", "P7-H1 Calc".equals(conMotivo.getValueAt(0, 0)),
                String.valueOf(conMotivo.getValueAt(0, 0)));
        r.verificar("columna 1: tipo", TipoProceso.CPU_BOUND.getNombre().equals(conMotivo.getValueAt(0, 1)),
                String.valueOf(conMotivo.getValueAt(0, 1)));
        r.verificar("columna 2: prioridad 5>3 por envejecimiento", "5>3".equals(conMotivo.getValueAt(0, 2)),
                String.valueOf(conMotivo.getValueAt(0, 2)));
        r.verificar("columna 3: memoria del PCB", "64".equals(conMotivo.getValueAt(0, 3)),
                String.valueOf(conMotivo.getValueAt(0, 3)));
        r.verificar("columna 4: tiempo restante (10 - 4 = 6)", "6".equals(conMotivo.getValueAt(0, 4)),
                String.valueOf(conMotivo.getValueAt(0, 4)));
        r.verificar("columna 7: motivo", "E/S: 3 ciclos".equals(conMotivo.getValueAt(0, 7)),
                String.valueOf(conMotivo.getValueAt(0, 7)));
        r.verificar("progreso y deadline entregan la fila completa", conMotivo.getValueAt(0, 5) == fila
                && conMotivo.getValueAt(0, 6) == fila && conMotivo.getColumnClass(5) == FilaProceso.class, "");
        PCB productor = FabricaProcesos.crear(DatosProceso.productor("Sensor-ficha", 2, 60, 400, "B9", 3, 10, 1), 0);
        FilaProceso copia = FilaProceso.de(productor);
        r.verificar("la copia del PCB trae todos sus elementos (ID, computador, estado, memoria, deadline, restante)",
                copia.id() == productor.getId() && copia.computador() == 1 && copia.estado() == EstadoProceso.NUEVO
                && copia.memoria() == 60 && copia.deadlineRestante() == 400 && copia.restantes() == 80
                && copia.cicloPrimeraEjecucion() == -1, copia.toString());
        r.verificar("la copia de un productor incluye su buffer y elementos", copia.usaBuffer() && "B9".equals(copia.buffer())
                && copia.elementosObjetivo() == 10 && copia.elementosProcesados() == 0, copia.toString());
        r.verificar("fila(0) devuelve el mismo registro", conMotivo.fila(0) == fila, "");

        FilaBuffer buffer = new FilaBuffer("B1", 1, 2, 5, 3, 2, 1, 9, 7, "-", "P4-H1", "-");
        ModeloTablaBuffers modeloBuffers = new ModeloTablaBuffers();
        r.verificar("buffers: 11 columnas", modeloBuffers.getColumnCount() == 11, String.valueOf(modeloBuffers.getColumnCount()));
        modeloBuffers.setFilas(new FilaBuffer[]{buffer});
        r.verificar("buffers: nombre y anfitrión", "B1".equals(modeloBuffers.getValueAt(0, 0))
                && "Computador 1".equals(modeloBuffers.getValueAt(0, 1)),
                modeloBuffers.getValueAt(0, 0) + " / " + modeloBuffers.getValueAt(0, 1));
        r.verificar("buffers: semáforos 3, 2, 1", "3".equals(modeloBuffers.getValueAt(0, 3))
                && "2".equals(modeloBuffers.getValueAt(0, 4)) && "1".equals(modeloBuffers.getValueAt(0, 5)), "");
        r.verificar("buffers: producidos 9, consumidos 7", "9".equals(modeloBuffers.getValueAt(0, 6))
                && "7".equals(modeloBuffers.getValueAt(0, 7)), "");
        r.verificar("buffers: bloqueados en lleno P4-H1", "P4-H1".equals(modeloBuffers.getValueAt(0, 9)),
                String.valueOf(modeloBuffers.getValueAt(0, 9)));
        r.verificar("buffers: la barra recibe la fila completa", modeloBuffers.getValueAt(0, 2) == buffer, "");

        ModeloTablaTexto texto = new ModeloTablaTexto(new String[]{"A", "B"});
        texto.setFilas(new String[][]{{"1", "2"}, {"3", "4"}});
        r.verificar("texto: 2 filas, 2 columnas", texto.getRowCount() == 2 && texto.getColumnCount() == 2, "");
        r.verificar("texto: celda (1,0) = 3 y título B", "3".equals(texto.getValueAt(1, 0)) && "B".equals(texto.getColumnName(1)), "");
    }

    /** cargarEstricto acepta lo válido y rechaza con motivo todo lo demás sin crear archivos. */
    private static void probarCargaEstricta() {
        r.grupo("Carga estricta de configuración");
        ConfiguracionSistema valida = ArchivoConfiguracion.cargarEstricto(escribir("valida.json", BASE_VALIDA));
        r.verificar("archivo válido: 2 computadores, 1 buffer, 0 errores", valida.getComputadores().tamano() == 2
                && valida.getBuffers().tamano() == 1 && valida.getErroresCarga() == 0, "");

        String inexistente = carpeta.resolve("no_existe.json").toString();
        String motivo = rechazo(inexistente);
        r.verificar("inexistente -> 'no existe'", motivo != null && motivo.contains("no existe"), String.valueOf(motivo));
        r.verificar("inexistente no se crea", !Files.exists(Path.of(inexistente)), "");

        motivo = rechazo(escribir("roto.json", "{ esto no es json"));
        r.verificar("JSON roto -> 'no es un JSON de configuración válido'",
                motivo != null && motivo.contains("no es un JSON de configuración válido"), String.valueOf(motivo));
        motivo = rechazo(escribir("arreglo.json", "[1, 2, 3]"));
        r.verificar("raíz que no es objeto -> se rechaza", motivo != null && motivo.contains("objeto JSON"), String.valueOf(motivo));
        motivo = rechazo(escribir("vacio.json", ""));
        r.verificar("archivo vacío -> se rechaza", motivo != null, String.valueOf(motivo));

        motivo = rechazo(escribir("clave.json", BASE_VALIDA.replace("\"debugger\": true,", "\"debugger\": true, \"colorFavorito\": 1,")));
        r.verificar("clave desconocida -> la nombra", motivo != null && motivo.contains("colorFavorito")
                && !motivo.contains("más"), String.valueOf(motivo));
        motivo = rechazo(escribir("capacidad.json", BASE_VALIDA.replace("\"capacidad\": 5", "\"capacidad\": -5")));
        r.verificar("capacidad -5 -> habla de capacidad", motivo != null && motivo.contains("capacidad"), String.valueOf(motivo));
        motivo = rechazo(escribir("dos.json", BASE_VALIDA.replace("\"debugger\": true,", "\"debugger\": true, \"colorFavorito\": 1,")
                .replace("\"capacidad\": 5", "\"capacidad\": -5")));
        r.verificar("dos errores -> primer error y '(y 1 error más)'", motivo != null && motivo.contains("colorFavorito")
                && motivo.contains("(y 1 error más)"), String.valueOf(motivo));
        motivo = rechazo(escribir("ram.json", BASE_VALIDA.replace("\"ram\": 1024", "\"ram\": \"mucha\"")));
        r.verificar("ram de tipo erróneo -> se rechaza", motivo != null, String.valueOf(motivo));

        File ejemplo = new File("config/configuracion_ejemplo.json");
        r.verificar("configuracion_ejemplo.json es válido a propósito", !ejemplo.isFile() || rechazo(ejemplo.getPath()) == null,
                String.valueOf(rechazo(ejemplo.getPath())));
        r.verificar("config/avilaosconfigs.json es válido", rechazo(ArchivoConfiguracion.RUTA_POR_DEFECTO) == null,
                String.valueOf(rechazo(ArchivoConfiguracion.RUTA_POR_DEFECTO)));
    }

    /** Lo que guarda el clúster se vuelve a cargar sin errores; las fotos y los paneles se arman sin pantalla. */
    private static void probarFotoYPaneles(LogEventos log) {
        r.grupo("Guardar, recargar y fotos de la GUI");
        ConfiguracionSistema base = ArchivoConfiguracion.cargar(ArchivoConfiguracion.RUTA_POR_DEFECTO);
        Cluster c = new Cluster(base, log);
        c.cargarProcesosIniciales();
        c.getReloj().setDuracionCicloMs(0);
        c.getReloj().ejecutarCiclos(40);
        int n = c.cantidadComputadores();

        String ruta = carpeta.resolve("guardada.json").toString();
        boolean guardado = c.guardarConfiguracion(ruta);
        String motivo = guardado ? rechazo(ruta) : "no se guardó";
        r.verificar("la configuración guardada se recarga sin errores", guardado && motivo == null, String.valueOf(motivo));
        if (guardado) {
            ConfiguracionSistema recargada = ArchivoConfiguracion.cargarEstricto(ruta);
            r.verificar("recargada: mismos computadores y buffers", recargada.getComputadores().tamano() == n
                    && recargada.getBuffers().tamano() == c.getBuffers().cantidad(), "");
        }

        FotoCluster foto = FotoCluster.de(c);
        r.verificar("foto: ciclo 40", foto.ciclo() == 40, String.valueOf(foto.ciclo()));
        r.verificar("foto: una fila de métricas por computador más la del clúster", foto.metricas().length == n + 1
                && "Clúster".equals(foto.metricas()[n].nombre()), String.valueOf(foto.metricas().length));
        r.verificar("foto: una fila de métricas por buffer", foto.metricasBuffers().length == c.getBuffers().cantidad(),
                String.valueOf(foto.metricasBuffers().length));
        r.verificar("foto: una serie por computador con 40 puntos en orden", foto.series().length == n
                && foto.series()[0].ciclos().length == 40 && foto.series()[0].ciclos()[0] == 1
                && foto.series()[0].ciclos()[39] == 40, String.valueOf(foto.series()[0].ciclos().length));

        boolean paneles = true;
        String falla = "";
        try {
            PanelMetricas metricas = new PanelMetricas();
            metricas.mostrar(foto.metricas(), foto.metricasBuffers());
            metricas.mostrar(foto.metricas(), foto.metricasBuffers());
            PanelGrafico grafico = new PanelGrafico();
            grafico.mostrar(foto.series());
            grafico.mostrar(foto.series());
        } catch (RuntimeException e) {
            paneles = false;
            falla = e.toString();
        }
        r.verificar("los paneles de métricas y gráfico se llenan dos veces sin error", paneles, falla);

        JFreeChart grafico = GraficoUtilizacion.crearVacio(new String[]{"PC0", "PC1"});
        File png = carpeta.resolve("sub/carpeta/grafico.png").toFile();
        boolean exportado = GraficoUtilizacion.exportarPNG(grafico, png);
        r.verificar("exportar el gráfico en vivo crea la carpeta y el PNG", exportado && png.length() > 0,
                String.valueOf(png.length()));
        c.apagar();
    }

    /** La nueva configuración valida rangos y carga heredada, y lo que guarda se recarga sin errores. */
    private static void probarNuevaConfiguracion() {
        r.grupo("Nueva configuración desde la GUI");
        ConfiguracionSistema actual = cargaActual(3);
        NuevaConfiguracion.Resultado vacia = NuevaConfiguracion.construir(actual, 250, 7, computadores(3, 1024), false);
        r.verificar("sin incluir carga: válida y sin buffers ni procesos", vacia.valido() && vacia.config().getBuffers().tamano() == 0
                && vacia.config().getProcesos().tamano() == 0, String.valueOf(vacia.motivo()));
        r.verificar("aplica ciclo, latencia y computadores", vacia.valido() && vacia.config().getDuracionCicloMs() == 250
                && vacia.config().getLatenciaRed() == 7 && vacia.config().getComputadores().tamano() == 3, "");
        r.verificar("hereda parada, envejecimiento y debugger", vacia.valido()
                && vacia.config().getParada() == actual.getParada()
                && vacia.config().getEnvejecimientoCadaCiclos() == 15 && vacia.config().isDebugger() == actual.isDebugger(), "");

        NuevaConfiguracion.Resultado con = NuevaConfiguracion.construir(actual, 250, 7, computadores(3, 1024), true);
        r.verificar("incluir carga: copia 1 buffer y 3 procesos", con.valido() && con.config().getBuffers().tamano() == 1
                && con.config().getProcesos().tamano() == 3, String.valueOf(con.motivo()));
        String ruta = carpeta.resolve("nueva.json").toString();
        boolean guardada = con.valido() && ArchivoConfiguracion.guardarConfiguracion(con.config(), ruta);
        String problema = guardada ? rechazo(ruta) : "no se guardó";
        r.verificar("el archivo guardado se recarga sin errores", guardada && problema == null, String.valueOf(problema));
        if (guardada && problema == null) {
            ConfiguracionSistema recargada = ArchivoConfiguracion.cargarEstricto(ruta);
            r.verificar("recargada: 3 computadores, 1 buffer, 3 procesos, latencia 7", recargada.getComputadores().tamano() == 3
                    && recargada.getBuffers().tamano() == 1 && recargada.getProcesos().tamano() == 3
                    && recargada.getLatenciaRed() == 7, "");
        }

        NuevaConfiguracion.Resultado sinComputador = NuevaConfiguracion.construir(actual, 250, 7, computadores(2, 1024), true);
        r.verificar("proceso en un computador que ya no existe: se rechaza con el motivo", !sinComputador.valido()
                && sinComputador.motivo().contains("no existe") && sinComputador.motivo().contains("Consumidor"), String.valueOf(sinComputador.motivo()));
        r.verificar("sin incluir carga, quitar computadores sí se permite", NuevaConfiguracion.construir(actual, 250, 7, computadores(2, 1024), false).valido(), "");

        NuevaConfiguracion.Resultado bufferGrande = NuevaConfiguracion.construir(actual, 250, 7, computadores(3, 4), true);
        r.verificar("buffer que no cabe en la RAM: se rechaza", !bufferGrande.valido() && bufferGrande.motivo().contains("no cabe")
                && bufferGrande.motivo().contains("B1"), String.valueOf(bufferGrande.motivo()));
        r.verificar("varios problemas: avisa cuántos más", !bufferGrande.valido() && bufferGrande.motivo().contains("más)"),
                String.valueOf(bufferGrande.motivo()));
        NuevaConfiguracion.Resultado procesoGrande = NuevaConfiguracion.construir(actual, 250, 7, computadores(3, 50), true);
        r.verificar("proceso con más memoria que la RAM: se rechaza", !procesoGrande.valido() && procesoGrande.motivo().contains("pide 100"),
                String.valueOf(procesoGrande.motivo()));
        r.verificar("rechazo no devuelve configuración", procesoGrande.config() == null, "");

        r.verificar("ciclo negativo", !NuevaConfiguracion.construir(actual, -1, 7, computadores(3, 1024), false).valido(), "");
        r.verificar("latencia fuera de rango", !NuevaConfiguracion.construir(actual, 250, 100000, computadores(3, 1024), false).valido(), "");
        r.verificar("un solo computador", !NuevaConfiguracion.construir(actual, 250, 7, computadores(1, 1024), false).valido(), "");
        r.verificar("más del máximo de computadores", !NuevaConfiguracion.construir(actual, 250, 7, computadores(9, 1024), false).valido(), "");
        r.verificar("RAM cero", !NuevaConfiguracion.construir(actual, 250, 7, computadores(3, 0), false).valido(), "");
        ListaEnlazada<ConfigComputador> sinQuantum = computadores(2, 1024);
        sinQuantum.agregarFinal(new ConfigComputador(1024, PoliticaTipo.ROUND_ROBIN, 0));
        r.verificar("quantum cero", !NuevaConfiguracion.construir(actual, 250, 7, sinQuantum, false).valido(), "");
    }

    /** Configuración de partida con un buffer B1 (capacidad 5, Computador 1) y tres procesos que usan hasta el último computador. */
    private static ConfiguracionSistema cargaActual(int cantidad) {
        ListaEnlazada<DatosBuffer> buffers = new ListaEnlazada<>();
        buffers.agregarFinal(new DatosBuffer("B1", 5, 1));
        ListaEnlazada<DatosProceso> procesos = new ListaEnlazada<>();
        procesos.agregarFinal(DatosProceso.cpuBound("Calculadora", 3, 100, 500, 50, 0));
        procesos.agregarFinal(DatosProceso.productor("Sensor", 2, 50, 500, "B1", 3, 4, DatosProceso.ASIGNACION_AUTOMATICA));
        procesos.agregarFinal(DatosProceso.consumidor("Consumidor", 2, 50, 500, "B1", 3, 4, cantidad - 1));
        return new ConfiguracionSistema(true, 500, new CondicionesParada(0, false, 20, true, 40), 2, 15,
                computadores(cantidad, 1024), buffers, procesos, 0);
    }

    private static ListaEnlazada<ConfigComputador> computadores(int cantidad, int ram) {
        ListaEnlazada<ConfigComputador> lista = new ListaEnlazada<>();
        for (int i = 0; i < cantidad; i++) {
            lista.agregarFinal(new ConfigComputador(ram, PoliticaTipo.FCFS, 3));
        }
        return lista;
    }

    /** Motivo del rechazo, o null si el archivo se cargó bien. */
    private static String rechazo(String ruta) {
        try {
            ArchivoConfiguracion.cargarEstricto(ruta);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static boolean motivo(String texto, String parte) {
        Validador.ResultadoEntero res = Validador.entero(texto, "Quantum", 1, 100);
        return !res.valido() && res.motivo().contains(parte);
    }

    /** Escribe un archivo de prueba en la carpeta temporal y devuelve su ruta. */
    private static String escribir(String nombre, String contenido) {
        Path archivo = carpeta.resolve(nombre);
        try {
            Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Debug.error("Pruebas", "no se pudo escribir " + archivo + ": " + e.getMessage());
        }
        return archivo.toString();
    }

    private static void borrarCarpeta() {
        File[] hijos = carpeta.toFile().listFiles();
        if (hijos != null) {
            for (File hijo : hijos) {
                borrar(hijo);
            }
        }
        carpeta.toFile().delete();
    }

    private static void borrar(File archivo) {
        File[] hijos = archivo.listFiles();
        if (hijos != null) {
            for (File hijo : hijos) {
                borrar(hijo);
            }
        }
        archivo.delete();
    }
}
