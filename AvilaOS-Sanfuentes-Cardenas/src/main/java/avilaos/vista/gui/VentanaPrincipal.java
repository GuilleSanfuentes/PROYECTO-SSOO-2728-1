package avilaos.vista.gui;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfiguracionSistema;
import avilaos.hardware.ObservadorReloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.GeneradorIds;
import avilaos.vista.ConsolaTerminal;
import avilaos.vista.GraficoUtilizacion;
import avilaos.vista.ReporteTerminal;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import javax.swing.JComponent;
import java.io.File;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Ventana principal de ÁvilaOS: observa al clúster y delega todas las acciones en él. */
public class VentanaPrincipal extends JFrame implements ObservadorReloj {

    private static final int REFRESCO_ESTADO_MS = 200;

    private final Cluster cluster;
    private final File archivo;
    private final CargaSimulacion carga;
    private final PanelControl control;
    private final PanelComputador[] paneles;
    private final PanelInferior inferior;
    private final PanelArchivos panelArchivos;
    private final PanelDisco panelDisco;
    private final PuenteEDT<FotoCluster> puente;
    private final ConsolaTerminal consola;
    private final Timer vigilante;
    private boolean resumenEmitido;
    private boolean cargando;
    private boolean finalizado;

    /** Arma la ventana sobre el clúster armado con el archivo de configuración por defecto. */
    public VentanaPrincipal(Cluster cluster, ConsolaTerminal consola) {
        this(cluster, consola, new File(ArchivoConfiguracion.RUTA_POR_DEFECTO));
    }

    /** Arma la ventana sobre el clúster indicado, sin iniciar la simulación; archivo es la configuración vigente para reiniciar. */
    public VentanaPrincipal(Cluster cluster, ConsolaTerminal consola, File archivo) {
        this(cluster, consola, archivo, null);
    }

    /** Ventana del Proyecto 2: un computador con disco, armado con los dos JSON de la carga (Stop vuelve a armar desde ellos). */
    public VentanaPrincipal(Cluster cluster, ConsolaTerminal consola, CargaSimulacion carga) {
        this(cluster, consola, null, carga);
    }

    private VentanaPrincipal(Cluster cluster, ConsolaTerminal consola, File archivo, CargaSimulacion carga) {
        super(carga != null ? "ÁvilaOS  -  disco, sistema de archivos y suspensión" : "ÁvilaOS  -  " + archivo.getName());
        this.cluster = cluster;
        this.archivo = archivo;
        this.carga = carga;
        this.consola = consola;
        this.control = new PanelControl(cluster, carga != null);
        this.inferior = new PanelInferior(carga, cluster);
        this.puente = new PuenteEDT<>(this::refrescar);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        add(control, BorderLayout.NORTH);

        paneles = new PanelComputador[cluster.cantidadComputadores()];
        PanelColumnas columnas = new PanelColumnas(paneles.length);
        for (int i = 0; i < paneles.length; i++) {
            paneles[i] = new PanelComputador(cluster, i);
            columnas.add(paneles[i]);
        }
        JScrollPane scroll = new JScrollPane(columnas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setMinimumSize(new Dimension(0, 150));

        java.awt.Component arriba = scroll;
        if (carga != null) {
            panelArchivos = new PanelArchivos(cluster, control);
            panelDisco = new PanelDisco();
            panelArchivos.setMinimumSize(new Dimension(220, 150));
            scroll.setMinimumSize(new Dimension(380, 150));
            panelDisco.setMinimumSize(new Dimension(300, 150));
            JSplitPane izquierda = dividir(JSplitPane.HORIZONTAL_SPLIT, panelArchivos, scroll, 0.0);
            izquierda.setDividerLocation(330);
            JSplitPane centro = dividir(JSplitPane.HORIZONTAL_SPLIT, izquierda, panelDisco, 0.55);
            centro.setDividerLocation(870);
            arriba = centro;
        } else {
            panelArchivos = null;
            panelDisco = null;
        }
        JSplitPane division = dividir(JSplitPane.VERTICAL_SPLIT, arriba, inferior, 0.72);
        add(division, BorderLayout.CENTER);

        setMinimumSize(new Dimension(900, 700));
        setSize(carga != null ? 1440 : 1280, carga != null ? 920 : 800);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });
        cluster.agregarObservador(this);
        vigilante = new Timer(REFRESCO_ESTADO_MS, e -> vigilarEstado());
        vigilante.start();
    }

    /** Divisor redimensionable con flecha para expandir. */
    private static JSplitPane dividir(int orientacion, java.awt.Component a, java.awt.Component b, double peso) {
        JSplitPane division = new JSplitPane(orientacion, a, b);
        division.setResizeWeight(peso);
        division.setOneTouchExpandable(true);
        division.setContinuousLayout(true);
        division.setBorder(null);
        return division;
    }

    /** Proyecto 2 al arrancar: pide los dos JSON, arma el sistema con sus procesos y abre la ventana en pausa. */
    public static void abrirProyecto2() {
        CargaSimulacion carga = new CargaSimulacion();
        DialogoCarga dialogo = new DialogoCarga(null, carga, null);
        dialogo.setVisible(true);
        if (!dialogo.isAceptado()) {
            System.exit(0);
            return;
        }
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        CargaSimulacion.Resultado resultado = carga.armarConProcesos(log);
        if (!resultado.armado()) {
            SelectorConfiguracion.error(null, "ÁvilaOS", "No se pudo armar el sistema: " + resultado.motivo());
            System.exit(1);
            return;
        }
        ConsolaTerminal consola = new ConsolaTerminal(resultado.cluster());
        consola.iniciar();
        VentanaPrincipal ventana = new VentanaPrincipal(resultado.cluster(), consola, carga);
        ventana.mostrar();
        SwingUtilities.invokeLater(() -> DialogoProblemas.mostrarSiHay(ventana, "Observaciones de la carga", resultado.problemas()));
    }

    /** Proyecto 2: elige otros JSON (o conserva el disco actual) y, si se aceptan, detiene y rearma todo, en pausa. */
    public void cargarJSON() {
        if (cargando || carga == null) {
            return;
        }
        CargaSimulacion nueva = new CargaSimulacion();
        nueva.usarSistemaArchivos(carga.getSistemaArchivos());
        nueva.usarProcesos(carga.getProcesos());
        cluster.formularioAbierto(true);
        DialogoCarga dialogo;
        try {
            dialogo = new DialogoCarga(this, nueva, cluster.getComputador(0));
            dialogo.setVisible(true);
        } finally {
            cluster.formularioAbierto(false);
        }
        if (dialogo.isAceptado()) {
            detenerYRearmar(nueva, "JSON cargados. Pulsa Iniciar.", false);
        }
    }

    /** Detiene y apaga este sistema en otro hilo y luego arma uno nuevo con la carga, en el hilo de Swing. */
    private void detenerYRearmar(CargaSimulacion nueva, String aviso, boolean iniciarDespues) {
        cargando = true;
        Thread hilo = new Thread(() -> {
            cluster.detener();
            cluster.apagar();
            SwingUtilities.invokeLater(() -> rearmar(nueva, aviso, iniciarDespues));
        }, "Hilo-Rearmado-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void rearmar(CargaSimulacion nueva, String aviso, boolean iniciarDespues) {
        emitirResumenFinal();
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        CargaSimulacion.Resultado resultado = nueva.armarConProcesos(log);
        CargaSimulacion vigente = nueva;
        if (!resultado.armado()) {
            vigente = carga;
            resultado = carga.armarConProcesos(log);
            aviso = "No se pudo armar con los JSON nuevos; se rearmó con los anteriores";
        }
        Cluster nuevo = resultado.cluster();
        if (consola != null) {
            consola.reemplazarCluster(nuevo);
        }
        VentanaPrincipal ventana = new VentanaPrincipal(nuevo, consola, vigente);
        ventana.setBounds(getBounds());
        ventana.setExtendedState(getExtendedState());
        ventana.mostrar();
        ventana.control.avisar(aviso, vigente != nueva);
        DialogoProblemas.mostrarSiHay(ventana, "Observaciones de la carga", resultado.problemas());
        vigilante.stop();
        dispose();
        if (iniciarDespues) {
            nuevo.iniciar();
        }
    }

    /** Valida el archivo; si es correcto detiene este clúster y abre una ventana nueva con el clúster cargado, en pausa. */
    public void cargarConfiguracion(File nuevoArchivo) {
        if (cargando) {
            return;
        }
        ConfiguracionSistema nueva;
        try {
            nueva = ArchivoConfiguracion.cargarEstricto(nuevoArchivo.getPath());
        } catch (IllegalArgumentException e) {
            SelectorConfiguracion.error(this, "Cargar configuración",
                    "No se cargó \"" + nuevoArchivo.getName() + "\": " + e.getMessage() + "\n\nSigue la simulación actual.");
            return;
        }
        detenerYReemplazar(nuevoArchivo, nueva, "Configuración cargada: " + nuevoArchivo.getName() + ". Pulsa Iniciar.", false, false);
    }

    /**
     * Stop/reinicio: detiene la simulación, emite el resumen final y rearma todo desde el archivo de configuración vigente
     * (releído, por si cambió). Si iniciarDespues es true, la simulación nueva arranca sola.
     */
    public void reiniciar(boolean iniciarDespues) {
        if (cargando) {
            return;
        }
        if (carga != null) {
            detenerYRearmar(carga, iniciarDespues ? "Simulación reiniciada con los mismos dos JSON"
                    : "Simulación detenida y rearmada con los mismos dos JSON. Pulsa Iniciar o carga otros.", iniciarDespues);
            return;
        }
        ConfiguracionSistema config;
        String aviso;
        boolean error = false;
        try {
            config = ArchivoConfiguracion.cargarEstricto(archivo.getPath());
            aviso = iniciarDespues ? "Simulación reiniciada desde " + archivo.getName()
                    : "Simulación detenida y reiniciada desde " + archivo.getName() + ". Pulsa Iniciar o carga otra configuración.";
        } catch (IllegalArgumentException e) {
            config = cluster.configuracionInicial();
            aviso = "\"" + archivo.getName() + "\" ya no es válido (" + e.getMessage() + "); se reinició con la configuración anterior";
            error = true;
        }
        detenerYReemplazar(archivo, config, aviso, error, iniciarDespues);
    }

    /** Detiene y apaga este clúster en otro hilo y luego lo reemplaza por uno nuevo en el hilo de Swing. */
    private void detenerYReemplazar(File nuevoArchivo, ConfiguracionSistema config, String aviso, boolean esError,
            boolean iniciarDespues) {
        cargando = true;
        Thread hilo = new Thread(() -> {
            cluster.detener();
            cluster.apagar();
            SwingUtilities.invokeLater(() -> reemplazar(nuevoArchivo, config, aviso, esError, iniciarDespues));
        }, "Hilo-Reinicio-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void reemplazar(File nuevoArchivo, ConfiguracionSistema config, String aviso, boolean esError,
            boolean iniciarDespues) {
        emitirResumenFinal();
        Debug.setActivo(config.isDebugger());
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        ReporteTerminal.resumenConfiguracion(nuevoArchivo.getPath(), config);
        GeneradorIds.reiniciar();
        Cluster nuevo = new Cluster(config, log);
        nuevo.cargarProcesosIniciales();
        if (consola != null) {
            consola.reemplazarCluster(nuevo);
        }
        VentanaPrincipal ventana = new VentanaPrincipal(nuevo, consola, nuevoArchivo);
        ventana.setBounds(getBounds());
        ventana.setExtendedState(getExtendedState());
        ventana.mostrar();
        ventana.control.avisar(aviso, esError);
        vigilante.stop();
        dispose();
        if (iniciarDespues) {
            nuevo.iniciar();
        }
    }

    /** Muestra la ventana en el hilo de Swing, con el estado inicial ya dibujado. */
    public void mostrar() {
        SwingUtilities.invokeLater(() -> {
            refrescar(FotoCluster.de(cluster));
            setVisible(true);
        });
    }

    @Override
    public void alCerrarCiclo(long ciclo) {
        puente.publicar(FotoCluster.de(cluster));
    }

    private void refrescar(FotoCluster foto) {
        control.mostrarCiclo(foto.ciclo());
        control.actualizarEstado();
        for (int i = 0; i < paneles.length; i++) {
            paneles[i].mostrar(foto.computadores()[i]);
        }
        if (foto.conDisco() && panelDisco != null) {
            panelArchivos.mostrar(foto.archivos());
            panelDisco.mostrar(foto.disco());
            control.mostrarDisco(foto.disco().politica(), foto.archivos().usuarios());
        }
        inferior.mostrar(foto);
    }

    /** Si el reloj se detuvo tras correr, dibuja el último estado y emite el resumen final una sola vez. */
    private void vigilarEstado() {
        if (finalizado) {
            return;
        }
        if (!cargando && cluster.getReloj().getCiclo() > 0 && !cluster.todosLosProcesos().estaVacia()
                && cluster.todosTerminados()) {
            finalizar();
            return;
        }
        control.actualizarEstado();
        if (!cluster.getReloj().isEjecutando() && cluster.getReloj().getCiclo() > 0 && !resumenEmitido) {
            refrescar(FotoCluster.de(cluster));
            emitirResumenFinal();
        }
    }

    /** Imprime el resumen final y guarda el gráfico de utilización de CPU. */
    private void emitirResumenFinal() {
        if (resumenEmitido || cluster.getReloj().getCiclo() == 0) {
            return;
        }
        resumenEmitido = true;
        ReporteTerminal.resumenFinal(cluster);
        GraficoUtilizacion.exportarPNG(cluster.getUtilizacion(), GraficoUtilizacion.RUTA_POR_DEFECTO);
    }

    /** Todos los procesos terminaron: apaga la simulación, cierra las demás ventanas, bloquea esta y muestra la despedida. */
    private void finalizar() {
        finalizado = true;
        vigilante.stop();
        Thread hilo = new Thread(() -> {
            cluster.detener();
            cluster.apagar();
            SwingUtilities.invokeLater(() -> {
                refrescar(FotoCluster.de(cluster));
                control.bloquearTodo();
                emitirResumenFinal();
                for (Window otra : Window.getWindows()) {
                    if (otra != this && otra.isDisplayable()) {
                        otra.dispose();
                    }
                }
                Bloqueo bloqueo = new Bloqueo();
                setGlassPane(bloqueo);
                bloqueo.setVisible(true);
                bloqueo.requestFocusInWindow();
                DialogoDespedida.mostrar(this, this::salir);
            });
        }, "Hilo-Fin-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Termina el programa: cierra todas las ventanas y finaliza el proceso. */
    private void salir() {
        for (Window ventana : Window.getWindows()) {
            ventana.dispose();
        }
        System.exit(0);
    }

    /** Capa semitransparente sobre la ventana que se traga ratón y teclado: la interfaz queda visible pero bloqueada. */
    private static final class Bloqueo extends JComponent {

        Bloqueo() {
            setOpaque(false);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            MouseAdapter tragar = new MouseAdapter() {
            };
            addMouseListener(tragar);
            addMouseMotionListener(tragar);
            addMouseWheelListener(tragar);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    e.consume();
                }

                @Override
                public void keyTyped(KeyEvent e) {
                    e.consume();
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(33, 37, 41, 120));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void cerrar() {
        cluster.apagar();
        emitirResumenFinal();
        dispose();
        System.exit(0);
    }
}
