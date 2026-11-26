package avilaos.vista.gui;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.log.Debug;
import javax.swing.JTabbedPane;

/**
 * Zona inferior redimensionable con una pestaña por vista global: buffers, log, consola, métricas y gráfico; en el
 * Proyecto 2 suma la tabla de asignación, la cola de E/S, las métricas del disco y el gráfico del desplazamiento.
 */
public class PanelInferior extends JTabbedPane {

    private final PanelBuffers buffers = new PanelBuffers();
    private final PanelTexto log = new PanelTexto(200, FotoCluster.LINEAS_LOG, null);
    private final PanelTexto consola = new PanelTexto(200, FotoCluster.LINEAS_CONSOLA, "Mostrar salida del Debug");
    private final PanelMetricas metricas = new PanelMetricas();
    private final PanelGrafico grafico = new PanelGrafico();
    private final ModeloTablaAsignacion modeloAsignacion = new ModeloTablaAsignacion();
    private final ModeloColaDisco modeloCola = new ModeloColaDisco();
    private final PanelTablaSimple asignacion;
    private final PanelTablaSimple cola;
    private final PanelMetricasDisco metricasDisco;
    private final PanelDesplazamiento desplazamiento;
    private FotoCluster ultima;

    /** Pestañas del Proyecto 1. */
    public PanelInferior() {
        this(null, null);
    }

    /** Pestañas del Proyecto 2 si hay carga (los dos JSON); la de buffers solo aparece si el JSON trajo buffers. */
    public PanelInferior(CargaSimulacion carga, Cluster cluster) {
        boolean proyecto2 = carga != null;
        asignacion = proyecto2 ? new PanelTablaSimple(modeloAsignacion, new int[]{50, 230, 60, 90, 80, 70, 120, 150},
                "No hay archivos en el sistema") : null;
        cola = proyecto2 ? new PanelTablaSimple(modeloCola, new int[]{90, 40, 90, 90, 230, 100, 60, 100},
                "La cola de E/S del disco está vacía") : null;
        metricasDisco = proyecto2 ? new PanelMetricasDisco() : null;
        desplazamiento = proyecto2 ? new PanelDesplazamiento(carga, cluster) : null;
        if (proyecto2) {
            addTab("Asignación de archivos", asignacion);
            addTab("Cola de E/S del disco", cola);
        }
        if (!proyecto2 || cluster.getBuffers().cantidad() > 0) {
            addTab(proyecto2 ? "Buffers (Proyecto 1)" : "Buffers", buffers);
        }
        addTab("Log", log);
        addTab("Consola", consola);
        addTab(proyecto2 ? "Métricas de CPU" : "Métricas", metricas);
        if (proyecto2) {
            addTab("Métricas del disco", metricasDisco);
        }
        addTab(proyecto2 ? "Gráfico de CPU" : "Gráfico", grafico);
        if (proyecto2) {
            addTab("Desplazamiento del cabezal", desplazamiento);
        }
        consola.getCasilla().setToolTipText("Requiere \"debugger\": true en la configuración");
        consola.getCasilla().addActionListener(e -> alternarConsola());
        addChangeListener(e -> pintarPestanaPesada());
    }

    /** Dibuja la foto en las pestañas livianas, y en las pesadas (métricas y gráficos) solo si están a la vista. */
    public void mostrar(FotoCluster foto) {
        ultima = foto;
        buffers.mostrar(foto.buffers());
        log.mostrar(foto.log());
        if (consola.getCasilla().isSelected() && Debug.isActivo()) {
            consola.mostrar(foto.consola());
        }
        if (foto.conDisco() && asignacion != null) {
            modeloAsignacion.setFilas(foto.archivos().archivos());
            asignacion.actualizarVacio();
            modeloCola.setFilas(foto.disco().actual(), foto.disco().cola());
            cola.actualizarVacio();
        }
        pintarPestanaPesada();
    }

    private void pintarPestanaPesada() {
        if (ultima == null) {
            return;
        }
        if (getSelectedComponent() == metricas) {
            metricas.mostrar(ultima.metricas(), ultima.metricasBuffers());
        } else if (getSelectedComponent() == grafico) {
            grafico.mostrar(ultima.series());
        } else if (getSelectedComponent() == metricasDisco && metricasDisco != null) {
            metricasDisco.mostrar(ultima.metricasDisco());
        } else if (getSelectedComponent() == desplazamiento && desplazamiento != null) {
            desplazamiento.mostrar(ultima.metricasDisco());
        }
    }

    private void alternarConsola() {
        boolean activa = consola.getCasilla().isSelected();
        if (activa && !Debug.isActivo()) {
            consola.mostrar(new String[]{"El debugger está apagado: pon \"debugger\": true en config/avilaosconfigs.json"});
        }
        Debug.getConsola().setActiva(activa);
        if (!activa) {
            consola.limpiar();
        }
    }
}
