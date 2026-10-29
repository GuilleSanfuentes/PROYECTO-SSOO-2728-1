package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfiguracionSistema;
import avilaos.hardware.ObservadorReloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.vista.ConsolaTerminal;
import avilaos.vista.GraficoUtilizacion;
import avilaos.vista.ReporteTerminal;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
    private final PanelControl control;
    private final PanelComputador[] paneles;
    private final PanelInferior inferior = new PanelInferior();
    private final PuenteEDT<FotoCluster> puente;
    private final ConsolaTerminal consola;
    private final Timer vigilante;
    private boolean resumenEmitido;
    private boolean cargando;

    /** Arma la ventana sobre el clúster indicado, sin iniciar la simulación; la consola (opcional) sigue al clúster nuevo al cargar. */
    public VentanaPrincipal(Cluster cluster, ConsolaTerminal consola) {
        super("ÁvilaOS");
        this.cluster = cluster;
        this.consola = consola;
        this.control = new PanelControl(cluster);
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

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, inferior);
        division.setResizeWeight(0.75);
        division.setOneTouchExpandable(true);
        division.setContinuousLayout(true);
        division.setBorder(null);
        add(division, BorderLayout.CENTER);

        setMinimumSize(new Dimension(900, 700));
        setSize(1280, 800);
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

    /** Valida el archivo; si es correcto detiene este clúster y abre una ventana nueva con el clúster cargado, en pausa. */
    public void cargarConfiguracion(File archivo) {
        if (cargando) {
            return;
        }
        ConfiguracionSistema nueva;
        try {
            nueva = ArchivoConfiguracion.cargarEstricto(archivo.getPath());
        } catch (IllegalArgumentException e) {
            SelectorConfiguracion.error(this, "Cargar configuración",
                    "No se cargó \"" + archivo.getName() + "\": " + e.getMessage() + "\n\nSigue la simulación actual.");
            return;
        }
        cargando = true;
        Thread hilo = new Thread(() -> {
            cluster.detener();
            cluster.apagar();
            SwingUtilities.invokeLater(() -> reemplazar(archivo, nueva));
        }, "Hilo-Cargar-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void reemplazar(File archivo, ConfiguracionSistema config) {
        emitirResumenFinal();
        Debug.setActivo(config.isDebugger());
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        ReporteTerminal.resumenConfiguracion(archivo.getPath(), config);
        Cluster nuevo = new Cluster(config, log);
        nuevo.cargarProcesosIniciales();
        if (consola != null) {
            consola.reemplazarCluster(nuevo);
        }
        VentanaPrincipal ventana = new VentanaPrincipal(nuevo, consola);
        ventana.setBounds(getBounds());
        ventana.setExtendedState(getExtendedState());
        ventana.mostrar();
        vigilante.stop();
        dispose();
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
        inferior.mostrar(foto);
    }

    /** Si el reloj se detuvo tras correr, dibuja el último estado y emite el resumen final una sola vez. */
    private void vigilarEstado() {
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

    private void cerrar() {
        cluster.apagar();
        emitirResumenFinal();
        dispose();
        System.exit(0);
    }
}
