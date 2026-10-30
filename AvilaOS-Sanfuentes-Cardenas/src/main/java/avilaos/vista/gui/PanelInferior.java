package avilaos.vista.gui;

import avilaos.log.Debug;
import javax.swing.JTabbedPane;

/** Zona inferior redimensionable con una pestaña por vista global: buffers, log, consola, métricas y gráfico. */
public class PanelInferior extends JTabbedPane {

    private final PanelBuffers buffers = new PanelBuffers();
    private final PanelTexto log = new PanelTexto(200, FotoCluster.LINEAS_LOG, null);
    private final PanelTexto consola = new PanelTexto(200, FotoCluster.LINEAS_CONSOLA, "Mostrar salida del Debug");
    private final PanelMetricas metricas = new PanelMetricas();
    private final PanelGrafico grafico = new PanelGrafico();
    private FotoCluster ultima;

    /** Arma las pestañas. */
    public PanelInferior() {
        addTab("Buffers", buffers);
        addTab("Log", log);
        addTab("Consola", consola);
        addTab("Métricas", metricas);
        addTab("Gráfico", grafico);
        consola.getCasilla().setToolTipText("Requiere \"debugger\": true en la configuración");
        consola.getCasilla().addActionListener(e -> alternarConsola());
        addChangeListener(e -> pintarPestanaPesada());
    }

    /** Dibuja la foto en buffers, log y consola, y en métricas o gráfico solo si esa pestaña está a la vista. */
    public void mostrar(FotoCluster foto) {
        ultima = foto;
        buffers.mostrar(foto.buffers());
        log.mostrar(foto.log());
        if (consola.getCasilla().isSelected() && Debug.isActivo()) {
            consola.mostrar(foto.consola());
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
